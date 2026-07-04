# agent

This service now exposes two flows:
- health analysis for the wellness app
- RAG over the MSD Manuals corpus using DeepSeek + Doubao Embeddings

> ⚠️ MSD Manuals content used for educational, non-commercial purposes.
> See [`ATTRIBUTION.md`](../ATTRIBUTION.md) for license and attribution details.

## Environment variables

Fill these in manually before running the service:

```bash
DASHSCOPE_API_KEY=
OPENAI_CHAT_URL=https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions
OPENAI_MODEL=qwen3.7-plus

DEEPSEEK_API_KEY=
DEEPSEEK_BASE_URL=https://api.deepseek.com
DEEPSEEK_MODEL=deepseek-chat

DOUBAO_API_KEY=
DOUBAO_BASE_URL=https://ark.cn-beijing.volces.com/api/v3
DOUBAO_EMBED_MODEL=doubao-embedding-text-240715

RAG_CORPUS_GLOB=../scraper/output/*_msd_chunks.jsonl
RAG_STORE_PATH=.rag_store/rag_index.json
RAG_TOP_K=5
```

`DOUBAO_API_KEY` is only required for full embedding-based RAG indexing.
If it is missing, the agent still starts and keeps `/chat` and `/rag/ask`
available through direct GPT fallback, while `/rag/reindex` reports that RAG
indexing is unavailable.

`OPENAI_API_KEY` and `DASHSCOPE_API_KEY` are interchangeable for the
OpenAI-compatible fallback. For a dedicated DashScope deployment, use the
deployment-specific URL shown in the Alibaba Cloud API example and append
`/chat/completions` to its `base_url`.

## Run

```bash
cd agent
pip install -r requirements.txt
python main.py
```

## RAG endpoints

- `POST /rag/reindex` - build or refresh the local index
- `POST /rag/ask` - ask a question using the indexed MSD corpus (one-shot RAG)
- `GET /rag/status` - inspect index status

## LLM Chat with RAG Tool Calling

- `POST /chat` - Chat with the LLM that can autonomously invoke RAG search as a tool.

  ```json
  // Request
  {
    "message": "What are the side effects of metformin?",
    "history": [  // optional
      {"role": "user", "content": "Hi"},
      {"role": "assistant", "content": "Hello! How can I help?"}
    ]
  }

  // Response
  {
    "success": true,
    "answer": "Metformin may cause gastrointestinal side effects... [1][2]",
    "tool_calls": [
      {"tool": "rag_search", "arguments": {"question": "..."}, "result_summary": "..."}
    ],
    "sources": [
      {"rank": 1, "title": "...", "section": "Side Effects", "source_url": "...", "score": 0.95}
    ]
  }
  ```

  How it works: The LLM receives the user message along with tool definitions. When it detects a medical question, it calls `rag_search` to retrieve relevant documents, then synthesizes a final answer with citations. Non-medical questions (greetings, general chat) are answered directly.

## Notes

- The RAG index is cached locally under `.rag_store/`.
- The corpus is read from `../scraper/output/*_msd_chunks.jsonl` by default.
- The service answers only from retrieved context and cites sources in the response.
