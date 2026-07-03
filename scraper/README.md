# scraper — MSD Manuals RAG data preparer

This folder contains a small, polite scraper to prepare RAG experiment data from MSD Manuals pages.

> ⚠️ **Content Notice**: The MSD Manuals content is used for educational, non-commercial purposes.
> See [`ATTRIBUTION.md`](../ATTRIBUTION.md) for full attribution and license details.

Files:
- `msd_rag_scraper.py`: main script to fetch a page, extract main text, chunk it, and optionally compute embeddings.

Quick start

1. Install dependencies (recommended in a venv):

```bash
pip install requests beautifulsoup4 sentence-transformers
```

2. Run the scraper on the example page:

```bash
python msd_rag_scraper.py https://www.msdmanuals.com/professional/special-subjects/integrative-complementary-and-alternative-medicine/overview-of-integrative-complementary-and-alternative-medicine
```

3. Optional: compute embeddings (may require downloading models):

```bash
python msd_rag_scraper.py <URL> --embed
```

Notes and extension ideas
- Respect `robots.txt` (the script checks and uses `Crawl-delay`).
- Current chunking rule: prioritize headings and paragraphs. The scraper starts a new chunk at each heading, keeps paragraph boundaries together when possible, and only falls back to word-level splitting when a single paragraph exceeds the chunk size.
- For larger scale crawling, introduce a scheduler, politeness (per-host queues), retry/backoff, and domain-limited crawling.
- For production RAG prep, add:
  - HTML-to-text improvements (better boilerplate removal, content selectors)
  - HTML chunk metadata (headings, section titles)
  - deduplication and normalization
  - persistent storage and indexing (FAISS/Qdrant/Chroma)
  - parallel downloads with per-host rate limits

License: project default.
