"""
@author Tao Yuchen
"""

#!/usr/bin/env python3
"""
MSD Manuals RAG data preparer
--------------------------------
Polite scraper that:
 - checks robots.txt for permission and crawl-delay
 - fetches one or more pages (single URL or batch from file)
 - extracts main text content, filters noise (nav, copyright, references)
 - chunks text into overlapping pieces with section headings
 - writes JSONL with URL-hash based filenames (auto-dedup on re-scrape)
 - (optionally) computes local embeddings with sentence-transformers

Usage examples:
  # Single page
  python msd_rag_scraper.py https://www.msdmanuals.com/professional/...

  # Batch scrape (one URL per line, # comments ignored)
  python msd_rag_scraper.py --urls-file urls.txt

  # Force re-scrape even if already cached
  python msd_rag_scraper.py <URL> --force

  # With local embeddings
  python msd_rag_scraper.py <URL> --embed

"""
import argparse
import json
import time
import hashlib
from pathlib import Path
from urllib.parse import urlparse, urljoin
from typing import Dict, List, Optional, Tuple

import requests
from bs4 import BeautifulSoup
import urllib.robotparser


# ── Noise / reference patterns ──────────────────────────────────────────────
# These section titles will be excluded from the final output.
REFERENCE_SECTION_PATTERNS = (
    'general references',
    'efficacy references',
    'safety reference',
    'more information',
    'drug information for the topic',
    'references',
    'reference list',
    'bibliography',
)


def is_reference_heading(text: str) -> bool:
    """Check if a heading text matches known reference/noise section titles."""
    normalized = text.strip().lower()
    return any(pattern in normalized for pattern in REFERENCE_SECTION_PATTERNS)


def fetch_robots_txt(root_url: str) -> str:
    robots_url = urljoin(root_url, '/robots.txt')
    r = requests.get(robots_url, timeout=10)
    r.raise_for_status()
    return r.text


def parse_crawl_delay(robots_text: str, user_agent='*') -> float:
    # crude parser that finds Crawl-delay for the given user-agent block
    lines = [l.strip() for l in robots_text.splitlines() if l.strip()]
    current_agents = []
    delay = None
    for line in lines:
        if line.lower().startswith('user-agent:'):
            agent = line.split(':', 1)[1].strip()
            current_agents = [agent]
        elif line.lower().startswith('crawl-delay:'):
            if user_agent in current_agents or '*' in current_agents:
                try:
                    delay = float(line.split(':', 1)[1].strip())
                    return delay
                except Exception:
                    pass
    return delay if delay is not None else 1.0


def is_allowed_by_robots(root_url: str, target_path: str, user_agent='*') -> bool:
    rp = urllib.robotparser.RobotFileParser()
    robots_url = urljoin(root_url, '/robots.txt')
    rp.set_url(robots_url)
    try:
        rp.read()
    except Exception:
        # if robots.txt can't be read, be conservative and allow
        return True
    return rp.can_fetch(user_agent, target_path)


def extract_main_text(html: str) -> Tuple[str, List[Dict[str, str]]]:
    soup = BeautifulSoup(html, 'html.parser')
    # remove scripts and styles
    for tag in soup(['script', 'style', 'noscript', 'header', 'footer', 'nav', 'aside']):
        tag.decompose()

    nav_like_texts = {
        'efficacy',
        'safety',
        'more information',
        'drug information',
        'multimedia',
    }
    footer_like_phrases = (
        'copyright',
        'all rights reserved',
        'about us',
        'disclaimer',
        'cookie preferences',
        'privacy policy',
        'terms of use',
        'sitemap',
    )

    def is_noise_block(text: str) -> bool:
        normalized = ' '.join(text.lower().split())
        if not normalized:
            return True
        if normalized in nav_like_texts:
            return True
        if any(phrase in normalized for phrase in footer_like_phrases):
            return True
        if '|' in normalized and len(normalized) < 80:
            return True
        return False

    def has_noise_ancestor(element) -> bool:
        noise_markers = (
            'topic_footer',
            'footer',
            'site-footer',
            'breadcrumb',
            'nav',
            'menu',
            'toc',
            'table-of-contents',
            'related',
            'reference',
        )

        for ancestor in element.parents:
            if ancestor is soup:
                break

            attrs = []
            if ancestor.has_attr('id') and ancestor.get('id'):
                attrs.append(str(ancestor.get('id')).lower())
            if ancestor.has_attr('class') and ancestor.get('class'):
                attrs.extend(str(cls).lower() for cls in ancestor.get('class'))

            if any(marker in attr for marker in noise_markers for attr in attrs):
                return True

        return False

    title = None
    if soup.title and soup.title.string:
        title = soup.title.string.strip()

    # prefer <article> or <main>
    main = soup.find('article') or soup.find('main')
    if not main:
        # Try common content containers
        main = soup.find('div', {'id': 'content'}) or soup.find('div', {'class': 'content'})
    if not main:
        main = soup.body

    blocks: List[Dict[str, str]] = []
    container = main if main else soup
    for element in container.find_all(['h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'p', 'li', 'blockquote', 'pre'], recursive=True):
        if has_noise_ancestor(element):
            continue

        text = ' '.join(element.get_text(' ', strip=True).split())
        if not text:
            continue
        if is_noise_block(text):
            continue
        if element.name and element.name.startswith('h') and len(element.name) == 2 and element.name[1].isdigit():
            blocks.append({'type': 'heading', 'level': element.name, 'text': text})
        else:
            blocks.append({'type': 'paragraph', 'text': text})

    if not blocks:
        text = container.get_text(separator='\n') if container else soup.get_text(separator='\n')
        lines = [ln.strip() for ln in text.splitlines() if ln.strip()]
        blocks = [{'type': 'paragraph', 'text': ln} for ln in lines]

    return title or '', blocks


def _word_count(text: str) -> int:
    return len(text.split())


def _split_long_text(text: str, max_words: int, overlap: int) -> List[str]:
    words = text.split()
    if len(words) <= max_words:
        return [text]

    chunks: List[str] = []
    step = max(1, max_words - overlap)
    start = 0
    while start < len(words):
        part = words[start:start + max_words]
        if not part:
            break
        chunks.append(' '.join(part))
        start += step
    return chunks


def chunk_text(blocks: List[Dict[str, str]], words_per_chunk: int = 400, overlap: int = 50):
    chunks = []
    chunk_id = 0
    current_title = ''
    current_parts: List[str] = []

    def flush_chunk():
        nonlocal chunk_id, current_parts
        if not current_parts:
            return
        chunk_text_value = '\n\n'.join(current_parts).strip()
        if chunk_text_value:
            chunks.append((chunk_id, current_title, chunk_text_value))
            chunk_id += 1
        current_parts = []

    for block in blocks:
        block_type = block.get('type')
        block_text = block.get('text', '').strip()
        if not block_text:
            continue

        if block_type == 'heading':
            # Stop processing at reference / copyright sections (always at end)
            if is_reference_heading(block_text):
                break
            flush_chunk()
            current_title = block_text
            current_parts = [block_text]
            continue

        if _word_count(block_text) > words_per_chunk:
            flush_chunk()
            for part in _split_long_text(block_text, words_per_chunk, overlap):
                chunks.append((chunk_id, current_title, part.strip()))
                chunk_id += 1
            continue

        candidate_parts = current_parts + [block_text]
        candidate_text = '\n\n'.join(candidate_parts).strip()
        if _word_count(candidate_text) > words_per_chunk and current_parts:
            flush_chunk()
            current_parts = [block_text]
        else:
            current_parts = candidate_parts

    flush_chunk()
    return chunks


def compute_id(url: str, chunk_id: int) -> str:
    h = hashlib.sha1(f"{url}::{chunk_id}".encode()).hexdigest()
    return h


def maybe_compute_embeddings(texts, model_name='all-MiniLM-L6-v2'):
    try:
        from sentence_transformers import SentenceTransformer
    except Exception as e:
        raise RuntimeError('sentence-transformers not installed; pip install sentence-transformers') from e

    model = SentenceTransformer(f'sentence-transformers/{model_name}')
    embs = model.encode(texts, show_progress_bar=True)
    return [e.tolist() for e in embs]


def url_to_filename(url: str) -> str:
    """Deterministic filename from URL — same URL → same file (auto-dedup)."""
    url_hash = hashlib.sha1(url.encode('utf-8')).hexdigest()[:16]
    return f'{url_hash}_msd_chunks.jsonl'


def scrape_url(url: str, out_dir: Path, chunk_size: int, overlap: int,
               force: bool = False) -> Tuple[Optional[Path], int]:
    """
    Scrape a single URL and write JSONL chunks.

    Returns:
        (path_or_None, chunk_count)
        - path is None if the file already exists and force=False
        - chunk_count is 0 if skipped, else number of chunks written
    """
    jsonl_path = out_dir / url_to_filename(url)

    # ── Skip if already cached ──────────────────────────────────────────
    if jsonl_path.exists() and not force:
        existing = sum(1 for _ in jsonl_path.open('r', encoding='utf8') if _.strip())
        print(f'  [SKIP]  {url}  ({existing} chunks already cached)')
        return None, 0

    # ── robots.txt check ────────────────────────────────────────────────
    parsed = urlparse(url)
    root = f"{parsed.scheme}://{parsed.netloc}"

    try:
        robots_text = fetch_robots_txt(root)
    except Exception:
        robots_text = ''

    crawl_delay = parse_crawl_delay(robots_text, user_agent='*')
    allowed = is_allowed_by_robots(root, parsed.path, user_agent='*')
    if not allowed:
        print(f'  [BLOCK] {url}  (disallowed by robots.txt)')
        return None, 0

    # ── Fetch ───────────────────────────────────────────────────────────
    print(f'  [FETCH] {url}  (delay={crawl_delay}s)')
    time.sleep(crawl_delay)
    r = requests.get(url, timeout=15, headers={'User-Agent': 'msd-rag-preparer/1.0'})
    r.raise_for_status()

    # ── Extract & chunk ─────────────────────────────────────────────────
    title, blocks = extract_main_text(r.text)
    print(f'          title="{title}"  content_blocks={len(blocks)}')

    chunks = chunk_text(blocks, words_per_chunk=chunk_size, overlap=overlap)

    # ── Write JSONL (overwrite — deterministic filename) ────────────────
    print(f'          writing {len(chunks)} chunks → {jsonl_path.name}')
    with jsonl_path.open('w', encoding='utf8') as fh:
        for cid, section_title, chunk in chunks:
            doc_id = compute_id(url, cid)
            rec = {
                'id': doc_id,
                'source_url': url,
                'title': title,
                'section_title': section_title,
                'chunk_id': cid,
                'text': chunk,
            }
            fh.write(json.dumps(rec, ensure_ascii=False) + '\n')

    return jsonl_path, len(chunks)


def main():
    parser = argparse.ArgumentParser(
        description='Scrape MSD Manuals page(s) into RAG-ready JSONL chunks'
    )
    parser.add_argument('url', nargs='?', help='Single page URL to scrape')
    parser.add_argument('--urls-file', help='File with one URL per line (# for comments)')
    parser.add_argument('--out', default='output', help='Output folder under scraper/')
    parser.add_argument('--embed', action='store_true', help='Compute local embeddings (sentence-transformers)')
    parser.add_argument('--chunk-size', type=int, default=400, help='Words per chunk')
    parser.add_argument('--overlap', type=int, default=50, help='Overlap words between chunks')
    parser.add_argument('--force', action='store_true', help='Re-scrape URLs even if already cached')
    args = parser.parse_args()

    # ── Collect URLs ────────────────────────────────────────────────────
    urls: List[str] = []
    if args.url:
        urls.append(args.url)
    if args.urls_file:
        urls_path = Path(args.urls_file)
        if not urls_path.exists():
            parser.error(f'URLs file not found: {args.urls_file}')
        with urls_path.open('r', encoding='utf8') as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith('#'):
                    urls.append(line)

    if not urls:
        parser.error('Provide a URL or --urls-file')

    out_dir = Path(__file__).resolve().parent / args.out
    out_dir.mkdir(parents=True, exist_ok=True)

    # ── Process each URL ────────────────────────────────────────────────
    total_urls = len(urls)
    success = 0
    skipped = 0
    total_chunks = 0

    for idx, url in enumerate(urls, start=1):
        print(f'[{idx}/{total_urls}] ', end='')
        try:
            path, count = scrape_url(
                url=url,
                out_dir=out_dir,
                chunk_size=args.chunk_size,
                overlap=args.overlap,
                force=args.force,
            )
            if path:
                success += 1
                total_chunks += count
            else:
                skipped += 1
        except Exception as e:
            print(f'  [ERROR] {url}: {e}')
            skipped += 1

    # ── Optional embeddings ─────────────────────────────────────────────
    if args.embed:
        texts: List[str] = []
        rec_ids: List[str] = []
        for jsonl_path in sorted(out_dir.glob('*_msd_chunks.jsonl')):
            with jsonl_path.open('r', encoding='utf8') as fh:
                for line in fh:
                    line = line.strip()
                    if not line:
                        continue
                    rec = json.loads(line)
                    rec_ids.append(rec['id'])
                    texts.append(rec['text'])
        if texts:
            print(f'Computing embeddings for {len(texts)} chunks (all-MiniLM-L6-v2) ...')
            embs = maybe_compute_embeddings(texts)
            emb_path = out_dir / '_all_embeddings.jsonl'
            with emb_path.open('w', encoding='utf8') as eh:
                for rid, emb in zip(rec_ids, embs):
                    eh.write(json.dumps({'id': rid, 'embedding': emb}, ensure_ascii=False) + '\n')
            print(f'Embeddings written to {emb_path}')
        else:
            print('No chunks found for embedding.')

    print(f'\nDone. {success} scraped ({total_chunks} chunks), {skipped} skipped. Files in {out_dir}')


if __name__ == '__main__':
    main()
