"""
RAG service built on DeepSeek + Doubao Embeddings.

Design goals:
- keep API keys out of source code
- cache embeddings locally for fast repeated queries
- use chunk-level retrieval with source metadata for citations
- fall back safely when required keys are missing

Author: ZHAO LEI
"""

from __future__ import annotations

import hashlib
import glob
import json
import math
import os
import threading
import time
import uuid
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional, Sequence, Tuple

from dotenv import load_dotenv, find_dotenv
from openai import OpenAI

import requests

# Load .env from the project root (agent/../.env)
# Falls back to cwd if not found
_env_path = Path(__file__).resolve().parent.parent / ".env"
if _env_path.exists():
    load_dotenv(_env_path)
else:
    load_dotenv()

NAVIGATION_PHRASES = {
    "efficacy",
    "safety",
    "more information",
    "drug information",
    "multimedia",
    "about us",
    "disclaimer",
    "cookie preferences",
    "copyright",
    "all rights reserved",
    "sitemap",
    "privacy policy",
    "terms of use",
}

REFERENCE_SECTIONS = {
    "general references",
    "reference",
    "references",
    "reference list",
    "疗效参考",
    "安全性参考",
    "参考文献",
    "更多信息",
    "more information",
    "drug information for the topic",
}


def _now_iso() -> str:
    return time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())


def _normalize_text(text: str) -> str:
    lines: List[str] = []
    previous = None
    for raw_line in text.replace("\r", "").splitlines():
        line = " ".join(raw_line.split()).strip()
        if not line:
            continue
        lowered = line.lower()
        if lowered in NAVIGATION_PHRASES:
            continue
        if any(phrase in lowered for phrase in ("copyright", "all rights reserved", "disclaimer", "cookie preferences")):
            continue
        if previous == line:
            continue
        lines.append(line)
        previous = line
    return "\n\n".join(lines).strip()


def _safe_cosine_similarity(a: Sequence[float], b: Sequence[float]) -> float:
    if not a or not b or len(a) != len(b):
        return 0.0
    dot = sum(x * y for x, y in zip(a, b))
    norm_a = math.sqrt(sum(x * x for x in a))
    norm_b = math.sqrt(sum(x * x for x in b))
    if norm_a == 0.0 or norm_b == 0.0:
        return 0.0
    return dot / (norm_a * norm_b)


def _chunk_text(text: str, max_chars: int = 1200) -> List[str]:
    paragraphs = [p.strip() for p in text.split("\n\n") if p.strip()]
    if not paragraphs:
        return []

    chunks: List[str] = []
    current = ""
    for paragraph in paragraphs:
        if len(paragraph) > max_chars:
            if current:
                chunks.append(current.strip())
                current = ""
            for start in range(0, len(paragraph), max_chars):
                chunks.append(paragraph[start:start + max_chars].strip())
            continue

        candidate = paragraph if not current else f"{current}\n\n{paragraph}"
        if len(candidate) <= max_chars:
            current = candidate
        else:
            if current:
                chunks.append(current.strip())
            current = paragraph

    if current:
        chunks.append(current.strip())
    return chunks


@dataclass(frozen=True)
class RagConfig:
    corpus_glob: str = os.getenv("RAG_CORPUS_GLOB", str(Path(__file__).resolve().parent.parent / "scraper" / "output" / "*_msd_chunks.jsonl"))
    store_path: Path = Path(os.getenv("RAG_STORE_PATH", str(Path(__file__).resolve().parent / ".rag_store" / "rag_index.json")))
    deepseek_base_url: str = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com")
    deepseek_api_key: str = os.getenv("DEEPSEEK_API_KEY", "")
    deepseek_model: str = os.getenv("DEEPSEEK_MODEL", "deepseek-chat")
    doubao_base_url: str = os.getenv("DOUBAO_BASE_URL", "https://ark.cn-beijing.volces.com/api/v3")
    doubao_api_key: str = os.getenv("DOUBAO_API_KEY", "")
    doubao_model: str = os.getenv("DOUBAO_EMBED_MODEL", "doubao-embedding-text-240715")
    embedding_batch_delay: float = float(os.getenv("RAG_EMBED_BATCH_DELAY", "0.5"))
    embedding_max_retries: int = int(os.getenv("RAG_EMBED_MAX_RETRIES", "5"))
    top_k: int = int(os.getenv("RAG_TOP_K", "5"))
    retrieval_chunk_max_chars: int = int(os.getenv("RAG_CHUNK_MAX_CHARS", "1200"))
    answer_max_tokens: int = int(os.getenv("RAG_ANSWER_MAX_TOKENS", "900"))
    answer_temperature: float = float(os.getenv("RAG_ANSWER_TEMPERATURE", "0.2"))


@dataclass
class RagDocument:
    doc_id: str
    source_url: str
    title: str
    section_title: str
    chunk_id: int
    text: str
    cleaned_text: str
    tokens: int
    embedding: List[float]

    def to_json(self) -> Dict[str, Any]:
        return asdict(self)


@dataclass
class ReindexTask:
    """Tracks the progress of an asynchronous reindex operation."""
    task_id: str
    status: str = "pending"          # pending | running | completed | failed
    progress: int = 0                # 0–100
    phase: str = ""                  # e.g. "Loading documents", "Embedding", "Saving"
    message: str = ""
    error: Optional[str] = None
    result: Optional[Dict[str, Any]] = None
    started_at: Optional[str] = None
    completed_at: Optional[str] = None


# Global registry for async reindex tasks
_reindex_tasks: Dict[str, ReindexTask] = {}
_task_lock = threading.Lock()


class DoubaoEmbeddingClient:
    def __init__(self, base_url: str, api_key: str, model: str, timeout: int = 45,
                 batch_delay: float = 0.5, max_retries: int = 5):
        if not api_key:
            raise RuntimeError("DOUBAO_API_KEY is not configured")
        self.client = OpenAI(
            api_key=api_key,
            base_url=base_url.rstrip("/"),
        )
        self.model = model
        self.timeout = timeout
        self.batch_delay = batch_delay
        self.max_retries = max_retries

    def embed_texts(self, texts: Sequence[str]) -> List[List[float]]:
        if not texts:
            return []

        batch_size = 10  # Doubao API 限制最多 10 条/请求
        all_embeddings: List[List[float]] = []

        total = len(texts)
        print(f"[RAG] Embedding {total} texts in batches of {batch_size}...")
        for i in range(0, total, batch_size):
            batch = list(texts[i:i + batch_size])
            batch_num = i // batch_size + 1
            total_batches = (total + batch_size - 1) // batch_size
            print(f"[RAG]  Embedding batch {batch_num}/{total_batches} ({len(batch)} texts)...")
            batch_embeddings = self._embed_with_retry(batch)
            all_embeddings.extend(batch_embeddings)

            # Rate-limit delay between batches
            if i + batch_size < total:
                time.sleep(self.batch_delay)

        print(f"[RAG] Embedding complete ({len(all_embeddings)} embeddings generated)")
        return all_embeddings

    def _embed_with_retry(self, batch: List[str]) -> List[List[float]]:
        last_exc: Optional[Exception] = None
        for attempt in range(1, self.max_retries + 1):
            try:
                response = self.client.embeddings.create(
                    model=self.model,
                    input=batch,
                )
                ordered = sorted(response.data, key=lambda item: item.index)
                return [
                    [float(v) for v in item.embedding]
                    for item in ordered
                ]
            except Exception as exc:
                last_exc = exc
                error_str = str(exc).lower()
                is_rate_limit = "429" in str(exc) or "toomanyrequests" in error_str or "accountratelimitexceeded" in error_str
                if is_rate_limit and attempt < self.max_retries:
                    wait = min(1.5 ** attempt, 30.0)
                    time.sleep(wait)
                    continue
                raise
        raise RuntimeError(f"Embedding failed after {self.max_retries} retries") from last_exc


class DeepSeekChatClient:
    def __init__(self, base_url: str, api_key: str, model: str, timeout: int = 60):
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key
        self.model = model
        self.timeout = timeout

    def _post(self, payload: dict) -> dict:
        """Low-level POST to the chat completions endpoint."""
        response = requests.post(
            f"{self.base_url}/chat/completions",
            headers={
                "Authorization": f"Bearer {self.api_key}",
                "Content-Type": "application/json",
            },
            json=payload,
            timeout=self.timeout,
        )
        response.raise_for_status()
        return response.json()

    def _parse_choice(self, payload: dict) -> dict:
        """Extract the first choice message from the response."""
        choices = payload.get("choices") or []
        if not choices:
            raise RuntimeError("DeepSeek response did not contain choices")
        return choices[0].get("message") or {}

    def complete(self, messages: Sequence[Dict[str, str]], max_tokens: int, temperature: float) -> str:
        if not self.api_key:
            raise RuntimeError("DEEPSEEK_API_KEY is not configured")

        print(f"[RAG] DeepSeek request: max_tokens={max_tokens}, temperature={temperature}")
        payload = self._post({
            "model": self.model,
            "messages": list(messages),
            "temperature": temperature,
            "max_tokens": max_tokens,
            "stream": False,
        })
        message = self._parse_choice(payload)
        content = message.get("content")
        if not content:
            raise RuntimeError("DeepSeek response did not contain message content")
        return str(content).strip()

    # ── Function Calling (Tool Use) support ──────────────────────────

    def complete_with_tools(
        self,
        messages: Sequence[Dict[str, Any]],
        tools: Sequence[Dict[str, Any]],
        max_tokens: int = 1024,
        temperature: float = 0.2,
        tool_choice: str = "auto",
    ) -> dict:
        """
        Send a chat completion request that may trigger tool calls.

        Returns the raw message dict which may contain:
          - "content": str | None
          - "tool_calls": list of tool-call objects (id, function.name, function.arguments)
        """
        if not self.api_key:
            raise RuntimeError("DEEPSEEK_API_KEY is not configured")

        print(f"[RAG] DeepSeek tool-calling request: tools={[t['function']['name'] for t in tools]}")
        payload = self._post({
            "model": self.model,
            "messages": list(messages),
            "temperature": temperature,
            "max_tokens": max_tokens,
            "stream": False,
            "tools": list(tools),
            "tool_choice": tool_choice,
        })
        return self._parse_choice(payload)


class RagService:
    def __init__(self, config: Optional[RagConfig] = None):
        self.config = config or RagConfig()
        self.embedding_client = DoubaoEmbeddingClient(
            base_url=self.config.doubao_base_url,
            api_key=self.config.doubao_api_key,
            model=self.config.doubao_model,
            batch_delay=self.config.embedding_batch_delay,
            max_retries=self.config.embedding_max_retries,
        )
        self.chat_client = DeepSeekChatClient(
            base_url=self.config.deepseek_base_url,
            api_key=self.config.deepseek_api_key,
            model=self.config.deepseek_model,
        )
        self._cache: Optional[Dict[str, Any]] = None

    # ── Async reindex task management ────────────────────────────────

    def start_reindex(self, force: bool = False) -> str:
        """Start an asynchronous reindex in a background thread. Returns a task_id."""
        task_id = uuid.uuid4().hex[:12]
        task = ReindexTask(
            task_id=task_id,
            status="pending",
            phase="Queued",
            message="Task queued, waiting to start...",
        )
        with _task_lock:
            _reindex_tasks[task_id] = task

        thread = threading.Thread(
            target=self._reindex_worker,
            args=(task_id, force),
            daemon=True,
        )
        thread.start()
        return task_id

    @staticmethod
    def get_reindex_task(task_id: str) -> Optional[ReindexTask]:
        with _task_lock:
            task = _reindex_tasks.get(task_id)
            if task is None:
                return None
            # Return a copy so caller can read it without holding the lock
            return ReindexTask(**asdict(task))

    def _update_task(self, task_id: str, **kwargs) -> None:
        with _task_lock:
            task = _reindex_tasks.get(task_id)
            if task is not None:
                for key, value in kwargs.items():
                    setattr(task, key, value)

    def _reindex_worker(self, task_id: str, force: bool) -> None:
        self._update_task(task_id, status="running", started_at=_now_iso())
        try:
            self._update_task(task_id, phase="Checking cache", progress=0,
                              message="Checking if index is stale...")

            if not force and not self._cache_is_stale():
                self._update_task(task_id, phase="Cached", progress=100,
                                  status="completed", message="Index is already up to date.",
                                  completed_at=_now_iso())
                return

            # ── Step 1: Load documents ──
            self._update_task(task_id, phase="Loading documents", progress=5,
                              message="Reading corpus files...")
            documents, new_signatures = self._load_source_documents(rebuild_seen=force)
            if not documents:
                raise RuntimeError("No source documents found for RAG indexing")

            total_docs = len(documents)
            self._update_task(task_id, phase=f"Loaded {total_docs} documents", progress=15,
                              message=f"Loaded {total_docs} documents from corpus files.")

            # ── Step 2: Embedding ──
            texts = [doc["cleaned_text"] for doc in documents]
            batch_size = 10
            total_batches = (len(texts) + batch_size - 1) // batch_size

            all_embeddings: List[List[float]] = []
            for i in range(0, len(texts), batch_size):
                batch = list(texts[i:i + batch_size])
                batch_num = i // batch_size + 1
                progress_pct = 15 + int(70 * (i / len(texts)))
                self._update_task(
                    task_id, phase="Embedding",
                    progress=progress_pct,
                    message=f"Embedding batch {batch_num}/{total_batches} ({len(batch)} texts)..."
                )
                batch_embeddings = self.embedding_client._embed_with_retry(batch)
                all_embeddings.extend(batch_embeddings)
                if i + batch_size < len(texts):
                    time.sleep(self.config.embedding_batch_delay)

            if len(all_embeddings) != len(documents):
                raise RuntimeError("Embedding count mismatch during index build")

            # ── Step 3: Normalize ──
            self._update_task(task_id, phase="Normalizing embeddings", progress=88,
                              message=f"Normalizing {len(all_embeddings)} embeddings...")
            for doc, embedding in zip(documents, all_embeddings):
                doc["embedding"] = self._normalize_vector(embedding)

            # ── Step 4: Save index ──
            self._update_task(task_id, phase="Saving index", progress=95,
                              message="Writing index to disk...")
            payload = {
                "version": 1,
                "built_at": _now_iso(),
                "document_count": len(documents),
                "source_count": len({doc["source_file"] for doc in documents}),
                "documents": documents,
            }
            self.config.store_path.parent.mkdir(parents=True, exist_ok=True)
            self.config.store_path.write_text(
                json.dumps(payload, ensure_ascii=False), encoding="utf-8"
            )
            self._cache = payload

            # ── Step 5: Persist dedup ──
            seen = self._load_seen_signatures()
            seen.update(new_signatures)
            self._save_seen_signatures(seen)

            self._update_task(
                task_id, status="completed", phase="Done", progress=100,
                message=f"Index rebuilt successfully ({len(documents)} documents, {len(seen)} dedup signatures).",
                result={
                    "document_count": len(documents),
                    "source_count": len({doc["source_file"] for doc in documents}),
                    "bulit_at": _now_iso(),
                },
                completed_at=_now_iso(),
            )
        except Exception as exc:
            self._update_task(
                task_id, status="failed", phase="Error",
                message=f"Reindex failed: {exc}",
                error=str(exc),
                completed_at=_now_iso(),
            )

    # ── Persistent dedup ────────────────────────────────────────────────
    @property
    def _seen_signatures_path(self) -> Path:
        return self.config.store_path.parent / "seen_signatures.json"

    def _load_seen_signatures(self) -> set:
        path = self._seen_signatures_path
        if path.exists():
            try:
                data = json.loads(path.read_text(encoding="utf-8"))
                return set(data.get("signatures", []))
            except Exception:
                pass
        return set()

    def _save_seen_signatures(self, signatures: set) -> None:
        path = self._seen_signatures_path
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(
            json.dumps(
                {"signatures": sorted(signatures)},
                ensure_ascii=False,
            ),
            encoding="utf-8",
        )

    def _discover_corpus_files(self) -> List[Path]:
        files = sorted(Path(path) for path in glob.glob(self.config.corpus_glob))
        if files:
            return files

        fallback = Path(__file__).resolve().parent.parent / "scraper" / "output"
        return sorted(fallback.glob("*_msd_chunks.jsonl"))

    def _is_reference_section(self, section_title: str) -> bool:
        normalized = section_title.strip().lower()
        return any(marker in normalized for marker in REFERENCE_SECTIONS)

    def _load_source_documents(self, rebuild_seen: bool = False) -> Tuple[List[Dict[str, Any]], set]:
        """
        Load documents from all JSONL corpus files.

        Args:
            rebuild_seen: If True, ignore the persistent seen_signatures file
                          and rebuild it from scratch (useful after scraper changes).
        """
        documents: List[Dict[str, Any]] = []

        # Load persistent dedup set from disk
        if rebuild_seen:
            seen_signatures: set = set()
        else:
            seen_signatures = self._load_seen_signatures()

        new_signatures: set = set()

        corpus_files = self._discover_corpus_files()
        print(f"[RAG] Loading source documents from {len(corpus_files)} corpus files...")
        for file_idx, file_path in enumerate(corpus_files, start=1):
            print(f"[RAG]  Reading file {file_idx}/{len(corpus_files)}: {file_path.name}")
            try:
                with file_path.open("r", encoding="utf-8") as handle:
                    for line in handle:
                        line = line.strip()
                        if not line:
                            continue
                        record = json.loads(line)
                        section_title = str(record.get("section_title", "")).strip()
                        if self._is_reference_section(section_title):
                            continue

                        cleaned_text = _normalize_text(str(record.get("text", "")))
                        if not cleaned_text:
                            continue

                        chunks = _chunk_text(cleaned_text, max_chars=self.config.retrieval_chunk_max_chars)
                        for idx, chunk_text in enumerate(chunks):
                            signature = hashlib.sha1(
                                f"{record.get('source_url','')}|{section_title}|{record.get('chunk_id','')}|{idx}|{chunk_text}".encode("utf-8")
                            ).hexdigest()
                            if signature in seen_signatures or signature in new_signatures:
                                continue
                            new_signatures.add(signature)
                            documents.append({
                                "doc_id": record.get("id", signature),
                                "source_url": record.get("source_url", ""),
                                "title": record.get("title", ""),
                                "section_title": section_title,
                                "chunk_id": record.get("chunk_id", 0),
                                "text": record.get("text", ""),
                                "cleaned_text": chunk_text,
                                "tokens": len(chunk_text.split()),
                                "source_file": str(file_path),
                            })
            except Exception:
                continue

        return documents, new_signatures

    def _cache_is_stale(self) -> bool:
        if not self.config.store_path.exists():
            return True

        cache_mtime = self.config.store_path.stat().st_mtime
        for file_path in self._discover_corpus_files():
            try:
                if file_path.stat().st_mtime > cache_mtime:
                    return True
            except FileNotFoundError:
                continue
        return False

    def build_index(self, force: bool = False) -> Dict[str, Any]:
        if not force and not self._cache_is_stale():
            print("[RAG] Index cache is fresh, skipping rebuild.")
            return self.load_index()

        print(f"[RAG] Building index (force={force})...")
        # If force=True, also reset persistent dedup so re-scraped content is picked up
        documents, new_signatures = self._load_source_documents(rebuild_seen=force)
        if not documents:
            raise RuntimeError("No source documents found for RAG indexing")

        print(f"[RAG] Loaded {len(documents)} documents from corpus files")

        texts = [doc["cleaned_text"] for doc in documents]
        print(f"[RAG] Starting embedding generation for {len(texts)} documents...")
        try:
            embeddings = self.embedding_client.embed_texts(texts)
        except Exception:
            # Embedding failed — do NOT persist dedup signatures,
            # so these documents will be retried on next rebuild.
            raise

        if len(embeddings) != len(documents):
            raise RuntimeError("Embedding count mismatch during index build")

        print(f"[RAG] Normalizing {len(embeddings)} embeddings...")
        for doc, embedding in zip(documents, embeddings):
            doc["embedding"] = self._normalize_vector(embedding)

        payload = {
            "version": 1,
            "built_at": _now_iso(),
            "document_count": len(documents),
            "source_count": len({doc["source_file"] for doc in documents}),
            "documents": documents,
        }
        self.config.store_path.parent.mkdir(parents=True, exist_ok=True)
        self.config.store_path.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
        self._cache = payload
        print(f"[RAG] Index saved to {self.config.store_path} ({len(documents)} documents)")

        # Persist dedup signatures ONLY after index is successfully built
        seen = self._load_seen_signatures()
        seen.update(new_signatures)
        self._save_seen_signatures(seen)
        print(f"[RAG] Dedup signature count: {len(seen)}")

        return payload

    def load_index(self) -> Dict[str, Any]:
        if self._cache is not None and not self._cache_is_stale():
            return self._cache
        if not self.config.store_path.exists():
            return self.build_index(force=True)

        payload = json.loads(self.config.store_path.read_text(encoding="utf-8"))
        self._cache = payload
        return payload

    def _normalize_vector(self, vector: Sequence[float]) -> List[float]:
        values = [float(item) for item in vector]
        norm = math.sqrt(sum(item * item for item in values))
        if norm == 0.0:
            return values
        return [item / norm for item in values]

    def search(self, question: str, top_k: Optional[int] = None) -> List[Dict[str, Any]]:
        print(f"[RAG] Searching for: \"{question[:80]}{'...' if len(question) > 80 else ''}\"")
        index = self.load_index()
        documents = index.get("documents", [])
        if not documents:
            print("[RAG]  No documents in index")
            return []

        print(f"[RAG]  Embedding query...")
        query_embedding = self.embedding_client.embed_texts([question])[0]
        query_embedding = self._normalize_vector(query_embedding)
        k = top_k or self.config.top_k

        print(f"[RAG]  Scoring {len(documents)} documents by similarity...")
        scored: List[Dict[str, Any]] = []
        for doc in documents:
            score = _safe_cosine_similarity(query_embedding, doc.get("embedding", []))
            enriched = dict(doc)
            enriched["score"] = round(score, 6)
            scored.append(enriched)

        scored.sort(key=lambda item: item["score"], reverse=True)
        print(f"[RAG]  Top {k} results: score range [{scored[-1]['score']:.4f}, {scored[0]['score']:.4f}]")
        return scored[:k]

    def answer(self, question: str, top_k: Optional[int] = None) -> Dict[str, Any]:
        print(f"[RAG] Answering question: \"{question[:80]}{'...' if len(question) > 80 else ''}\"")
        sources = self.search(question, top_k=top_k)
        if not sources:
            print("[RAG]  No relevant sources found")
            return {
                "success": False,
                "answer": "No indexed RAG documents are available yet. Run /rag/reindex first.",
                "sources": [],
            }

        print(f"[RAG]  Building context from {len(sources)} sources...")
        context_blocks = []
        for idx, source in enumerate(sources, start=1):
            context_blocks.append(
                f"[{idx}] {source['title']} | {source['section_title']} | chunk {source['chunk_id']}\n"
                f"Source: {source['source_url']}\n"
                f"Content: {source['cleaned_text']}"
            )

        system_prompt = (
            "You are a precise medical knowledge assistant. Answer only from the provided context. "
            "If the context is insufficient, say that the answer is not found in the indexed material. "
            "Respond in the same language as the user's question."
            "Cite evidence inline with [1], [2], etc. Do not invent facts."
        )
        user_prompt = (
            f"Question: {question}\n\n"
            f"Context:\n{chr(10).join(context_blocks)}\n\n"
            "Return a short answer grounded in the context, followed by a bullet list of sources."
        )

        print(f"[RAG]  Calling DeepSeek LLM (model={self.config.deepseek_model})...")
        answer = self.chat_client.complete(
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
            max_tokens=self.config.answer_max_tokens,
            temperature=self.config.answer_temperature,
        )
        print(f"[RAG]  Answer received ({len(answer)} chars)")

        return {
            "success": True,
            "answer": answer,
            "sources": [
                {
                    "rank": idx,
                    "score": source["score"],
                    "title": source["title"],
                    "section_title": source["section_title"],
                    "chunk_id": source["chunk_id"],
                    "source_url": source["source_url"],
                    "snippet": source["cleaned_text"][:500],
                }
                for idx, source in enumerate(sources, start=1)
            ],
        }

    def status(self) -> Dict[str, Any]:
        index = self.load_index()
        return {
            "indexPath": str(self.config.store_path),
            "builtAt": index.get("built_at"),
            "documentCount": index.get("document_count", 0),
            "sourceCount": index.get("source_count", 0),
            "corpusGlob": self.config.corpus_glob,
            "deepseekModel": self.config.deepseek_model,
            "doubaoModel": self.config.doubao_model,
        }

    # ── LLM Chat with Tool Calling ──────────────────────────────────

    # Tool definitions exposed to the LLM (OpenAI function-calling format)
    TOOLS = [
        {
            "type": "function",
            "function": {
                "name": "rag_search",
                "description": (
                    "Search the MSD (Merck Sharp & Dohme) medical knowledge base for "
                    "information about drugs, diseases, treatments, side effects, "
                    "dosages, clinical guidelines, and other medical topics. "
                    "Use this tool whenever the user asks a medical or pharmaceutical question."
                ),
                "parameters": {
                    "type": "object",
                    "properties": {
                        "question": {
                            "type": "string",
                            "description": "The medical question to search for in the knowledge base. Be specific and include relevant drug names, disease names, or symptoms.",
                        },
                        "top_k": {
                            "type": "integer",
                            "description": "Number of top relevant document chunks to retrieve (default: 5, max: 10).",
                        },
                    },
                    "required": ["question"],
                },
            },
        },
        {
            "type": "function",
            "function": {
                "name": "rag_status",
                "description": (
                    "Check the current status of the RAG knowledge base index: "
                    "how many documents are indexed, when it was last built, etc."
                ),
                "parameters": {
                    "type": "object",
                    "properties": {},
                },
            },
        },
    ]

    CHAT_SYSTEM_PROMPT = (
        "You are a knowledgeable medical assistant powered by the MSD manual knowledge base. "
        "You have access to a `rag_search` tool that retrieves relevant medical information. "
        "Rules:\n"
        "- ALWAYS call `rag_search` when the user asks a medical question, even if you think you know the answer.\n"
        "- If `rag_search` returns no results, honestly tell the user you couldn't find relevant information.\n"
        "- Cite sources inline with [1], [2], etc. referencing the source titles.\n"
        "- Respond in the same language as the user's question.\n"
        "- For non-medical questions (greetings, general chat), you may answer directly without calling tools.\n"
        "- Keep answers concise and evidence-based. Do not invent facts."
    )

    def _execute_tool(self, tool_name: str, tool_args: dict) -> str:
        """Execute a tool call and return the result as a JSON string."""
        if tool_name == "rag_search":
            question = tool_args.get("question", "")
            top_k = tool_args.get("top_k") or self.config.top_k
            top_k = min(int(top_k), 10)  # cap at 10

            sources = self.search(question, top_k=top_k)
            if not sources:
                return json.dumps({
                    "success": False,
                    "message": "No relevant documents found in the knowledge base. The index may be empty or the question may be out of scope.",
                }, ensure_ascii=False)

            # Return compact chunks with metadata for the LLM
            return json.dumps({
                "success": True,
                "count": len(sources),
                "results": [
                    {
                        "rank": idx,
                        "title": s["title"],
                        "section": s["section_title"],
                        "source_url": s["source_url"],
                        "score": s["score"],
                        "content": s["cleaned_text"],
                    }
                    for idx, s in enumerate(sources, start=1)
                ],
            }, ensure_ascii=False)

        elif tool_name == "rag_status":
            return json.dumps(self.status(), ensure_ascii=False)

        else:
            return json.dumps({"error": f"Unknown tool: {tool_name}"})

    def chat(
        self,
        user_message: str,
        history: Optional[List[Dict[str, Any]]] = None,
        max_tool_rounds: int = 3,
    ) -> Dict[str, Any]:
        """
        Chat with the LLM using tool calling.

        The LLM can autonomously decide to invoke `rag_search` when it needs
        medical knowledge.  The conversation loop is:

        1. Send user message + tools → LLM
        2. If LLM returns tool_calls → execute them → feed results back → go to 1
        3. If LLM returns plain content → return final answer

        Args:
            user_message: The user's chat message.
            history: Optional conversation history (list of message dicts).
            max_tool_rounds: Maximum number of tool-calling rounds (prevents infinite loops).

        Returns:
            Dict with "answer", "tool_calls" trace, and optional "sources".
        """
        print(f"[RAG-Chat] User: \"{user_message[:100]}{'...' if len(user_message) > 100 else ''}\"")

        messages: List[Dict[str, Any]] = [
            {"role": "system", "content": self.CHAT_SYSTEM_PROMPT},
        ]
        if history:
            messages.extend(history)
        messages.append({"role": "user", "content": user_message})

        tool_call_trace: List[Dict[str, Any]] = []
        final_sources: List[Dict[str, Any]] = []

        for round_idx in range(max_tool_rounds):
            print(f"[RAG-Chat] Round {round_idx + 1}/{max_tool_rounds} — sending {len(messages)} messages to LLM")

            msg = self.chat_client.complete_with_tools(
                messages=messages,
                tools=self.TOOLS,
                max_tokens=self.config.answer_max_tokens,
                temperature=self.config.answer_temperature,
            )

            tool_calls = msg.get("tool_calls")
            content = msg.get("content")

            # ── LLM returned a text answer (no tool calls) ──
            if not tool_calls:
                answer = (content or "").strip()
                print(f"[RAG-Chat] Final answer ({len(answer)} chars, {len(tool_call_trace)} tool rounds)")
                return {
                    "success": True,
                    "answer": answer,
                    "tool_calls": tool_call_trace,
                    "sources": final_sources if final_sources else None,
                }

            # ── LLM requested tool calls ──
            print(f"[RAG-Chat] LLM requested {len(tool_calls)} tool call(s)")

            # Add the assistant message (with tool_calls) to the conversation
            messages.append(msg)

            for tc in tool_calls:
                tool_name = tc["function"]["name"]
                try:
                    tool_args = json.loads(tc["function"]["arguments"])
                except json.JSONDecodeError:
                    tool_args = {}

                print(f"[RAG-Chat]  → Executing {tool_name}({json.dumps(tool_args, ensure_ascii=False)[:120]})")
                tool_result = self._execute_tool(tool_name, tool_args)

                trace_entry = {
                    "tool": tool_name,
                    "arguments": tool_args,
                    "result_summary": tool_result[:300] + ("..." if len(tool_result) > 300 else ""),
                }
                tool_call_trace.append(trace_entry)

                # Capture sources for the final response
                if tool_name == "rag_search":
                    try:
                        parsed = json.loads(tool_result)
                        if parsed.get("success") and parsed.get("results"):
                            final_sources = [
                                {
                                    "rank": r["rank"],
                                    "title": r["title"],
                                    "section": r["section"],
                                    "source_url": r["source_url"],
                                    "score": r["score"],
                                }
                                for r in parsed["results"]
                            ]
                    except json.JSONDecodeError:
                        pass

                # Add tool result message to the conversation
                messages.append({
                    "role": "tool",
                    "tool_call_id": tc["id"],
                    "content": tool_result,
                })

        # ── Exhausted tool rounds ──
        # Force a final answer without tools
        print(f"[RAG-Chat] Max tool rounds reached, requesting final answer")
        messages.append({
            "role": "user",
            "content": "Please provide your final answer based on the tool results above. Do NOT call any more tools.",
        })
        final_msg = self.chat_client.complete_with_tools(
            messages=messages,
            tools=[],  # no tools this time → LLM must answer
            max_tokens=self.config.answer_max_tokens,
            temperature=self.config.answer_temperature,
        )
        answer = (final_msg.get("content") or "").strip()
        return {
            "success": True,
            "answer": answer,
            "tool_calls": tool_call_trace,
            "sources": final_sources if final_sources else None,
        }
