"""
Wellness Agentic AI — Main Entry Point
=======================================
Flask-based microservice that provides AI-powered wellness trend analysis,
personalized recommendation generation, and a DeepSeek + Doubao RAG endpoint.

API Endpoints:
    POST /analyze      — Analyze user wellness records and generate recommendations
    POST /rag/reindex  — Rebuild the RAG index from scraper output
    POST /rag/ask      — Ask a question against the indexed MSD corpus (one-shot RAG)
    GET  /rag/status   — Check RAG index status
    POST /chat         — LLM Chat with RAG tool calling (LLM decides when to search)
    GET  /health       — Health check

Usage:
    python main.py
    (Runs on http://localhost:5001 by default)

Author: ZHAO LEI
"""

import os
from pathlib import Path

from flask import Flask, request, jsonify
from flask_cors import CORS
import requests
from agent import WellnessAgent
from rag_service import RagService
from dotenv import load_dotenv

# Load .env from the project root (agent/../.env), fall back to cwd
_env_path = Path(__file__).resolve().parent.parent / ".env"
if _env_path.exists():
    load_dotenv(_env_path)
else:
    load_dotenv()
app = Flask(__name__)
CORS(app)

# Configuration
BACKEND_URL = os.environ.get("BACKEND_URL", "http://localhost:8080")
JWT_TOKEN = os.environ.get("AGENT_JWT_TOKEN", "")
# ZHAO LEI: DashScope uses the same OpenAI-compatible authentication flow.
OPENAI_API_KEY = (
    os.environ.get("OPENAI_API_KEY")
    or os.environ.get("DASHSCOPE_API_KEY")
    or os.environ.get("GPT_API_KEY", "")
)
OPENAI_CHAT_URL = os.environ.get("OPENAI_CHAT_URL", "https://api.openai.com/v1/chat/completions")
OPENAI_MODEL = os.environ.get("OPENAI_MODEL", "gpt-4o-mini")

agent = WellnessAgent(backend_url=BACKEND_URL, jwt_token=JWT_TOKEN)
rag_service = None
rag_service_error = None
try:
    rag_service = RagService()
except Exception as exc:
    rag_service_error = str(exc)


def direct_gpt_chat(message, history=None, system_prompt=None):
    """OpenAI-compatible direct chat fallback for CA testing without RAG embeddings."""
    if not OPENAI_API_KEY:
        raise RuntimeError(
            "OPENAI_API_KEY, DASHSCOPE_API_KEY, or GPT_API_KEY is not configured"
        )

    messages = [{
        "role": "system",
        "content": system_prompt or (
            "You are WellBot, a concise wellness assistant. "
            "Answer health and wellness questions clearly, avoid diagnosis, "
            "and recommend professional medical help for medical concerns."
        )
    }]
    if history:
        messages.extend(history)
    messages.append({"role": "user", "content": message})

    response = requests.post(
        OPENAI_CHAT_URL,
        headers={
            "Authorization": f"Bearer {OPENAI_API_KEY}",
            "Content-Type": "application/json",
        },
        json={
            "model": OPENAI_MODEL,
            "messages": messages,
            "max_tokens": 500,
            "stream": False,
        },
        timeout=60,
    )
    response.raise_for_status()
    payload = response.json()
    choices = payload.get("choices") or []
    if not choices:
        raise RuntimeError("GPT response did not contain choices")
    content = ((choices[0].get("message") or {}).get("content") or "").strip()
    if not content:
        raise RuntimeError("GPT response did not contain message content")
    return content


@app.route("/health", methods=["GET"])
def health():
    """Health check endpoint."""
    return jsonify({
        "status": "UP",
        "service": "wellness-agent",
        "version": "0.1.0",
        "ragStatus": "UP" if rag_service else "UNAVAILABLE",
        "ragError": rag_service_error
    })


@app.route("/analyze", methods=["POST"])
def analyze():
    """
    Analyze wellness records for a user and generate recommendations.

    Request body (JSON):
    {
        "userId": 1,
        "username": "demo",
        "jwtToken": "eyJhbG...",     // JWT for backend API auth
        "backendUrl": "http://..."   // Optional: override backend URL
    }

    Response:
    {
        "success": true,
        "analysisSummary": "...",
        "sleepAnalysis": { ... },
        "activityAnalysis": { ... },
        "recommendations": [ ... ]
    }
    """
    data = request.get_json(silent=True) or {}

    user_id = data.get("userId")
    username = data.get("username")
    jwt_token = data.get("jwtToken", JWT_TOKEN)
    backend_url = data.get("backendUrl", BACKEND_URL)

    if not user_id:
        return jsonify({
            "success": False,
            "error": "userId is required"
        }), 400

    if not jwt_token:
        return jsonify({
            "success": False,
            "error": "jwtToken is required for backend authentication"
        }), 400

    try:
        result = agent.analyze_and_recommend(
            user_id=user_id,
            username=username or f"user_{user_id}",
            jwt_token=jwt_token,
            backend_url=backend_url
        )
        return jsonify(result)
    except Exception as e:
        return jsonify({
            "success": False,
            "error": str(e)
        }), 500


@app.route("/rag/reindex", methods=["POST"])
def rag_reindex():
    if rag_service is None:
        return jsonify({
            "success": False,
            "error": f"RAG service unavailable: {rag_service_error}"
        }), 503

    data = request.get_json(silent=True) or {}
    force = bool(data.get("force", False))

    try:
        task_id = rag_service.start_reindex(force=force)
        return jsonify({
            "success": True,
            "data": {
                "taskId": task_id,
                "status": "started",
            },
        })
    except Exception as e:
        return jsonify({
            "success": False,
            "error": str(e)
        }), 500


@app.route("/rag/reindex-status/<task_id>", methods=["GET"])
def rag_reindex_status(task_id: str):
    if rag_service is None:
        return jsonify({
            "success": False,
            "error": f"RAG service unavailable: {rag_service_error}"
        }), 503

    task = rag_service.get_reindex_task(task_id)
    if task is None:
        return jsonify({
            "success": False,
            "error": f"Task {task_id} not found"
        }), 404

    return jsonify({
        "success": True,
        "data": {
            "taskId": task.task_id,
            "status": task.status,
            "progress": task.progress,
            "phase": task.phase,
            "message": task.message,
            "error": task.error,
            "result": task.result,
            "startedAt": task.started_at,
            "completedAt": task.completed_at,
        },
    })


@app.route("/rag/ask", methods=["POST"])
def rag_ask():
    if rag_service is None:
        data = request.get_json(silent=True) or {}
        question = str(data.get("question", "")).strip()
        if not question:
            return jsonify({
                "success": False,
                "error": "question is required"
            }), 400
        try:
            answer = direct_gpt_chat(
                question,
                system_prompt=(
                    "You are WellBot. RAG document retrieval is unavailable in this CA test "
                    "environment because the embedding provider is not configured. "
                    "Answer from general wellness knowledge, be concise, and state that no "
                    "MSD source citations are available."
                )
            )
            return jsonify({
                "success": True,
                "answer": answer,
                "sources": [],
                "fallback": "direct_gpt_no_rag",
                "warning": f"RAG service unavailable: {rag_service_error}"
            })
        except Exception as e:
            return jsonify({
                "success": False,
                "error": str(e)
            }), 500

    data = request.get_json(silent=True) or {}
    question = str(data.get("question", "")).strip()
    top_k = data.get("topK")

    if not question:
        return jsonify({
            "success": False,
            "error": "question is required"
        }), 400

    try:
        result = rag_service.answer(question, top_k=int(top_k) if top_k else None)
        return jsonify(result)
    except Exception as e:
        return jsonify({
            "success": False,
            "error": str(e)
        }), 500


@app.route("/rag/status", methods=["GET"])
def rag_status():
    if rag_service is None:
        return jsonify({
            "success": True,
            "data": {
                "indexPath": None,
                "builtAt": None,
                "documentCount": 0,
                "sourceCount": 0,
                "corpusGlob": None,
                "deepseekModel": None,
                "doubaoModel": None,
                "status": "UNAVAILABLE",
                "error": rag_service_error,
                "fallback": "direct_gpt_no_rag"
            }
        })

    try:
        return jsonify({
            "success": True,
            "data": rag_service.status(),
        })
    except Exception as e:
        return jsonify({
            "success": False,
            "error": str(e)
        }), 500


@app.route("/chat", methods=["POST"])
def chat():
    if rag_service is None:
        data = request.get_json(silent=True) or {}
        message = str(data.get("message", "")).strip()
        history = data.get("history")

        if not message:
            return jsonify({
                "success": False,
                "error": "message is required"
            }), 400

        try:
            answer = direct_gpt_chat(message=message, history=history)
            return jsonify({
                "success": True,
                "answer": answer,
                "tool_calls": [],
                "sources": [],
                "fallback": "direct_gpt_no_rag",
                "warning": f"RAG chat unavailable: {rag_service_error}"
            })
        except Exception as e:
            return jsonify({
                "success": False,
                "error": str(e)
            }), 500

    """
    LLM Chat endpoint with RAG tool calling.

    The LLM can autonomously decide to invoke rag_search when it needs
    medical knowledge from the indexed MSD corpus.

    Request body (JSON):
    {
        "message": "What are the side effects of metformin?",   // required
        "history": [                                             // optional
            {"role": "user", "content": "..."},
            {"role": "assistant", "content": "..."}
        ]
    }

    Response:
    {
        "success": true,
        "answer": "Metformin may cause... [1][2]",
        "tool_calls": [
            {"tool": "rag_search", "arguments": {...}, "result_summary": "..."}
        ],
        "sources": [
            {"rank": 1, "title": "...", "section": "...", "source_url": "...", "score": 0.95}
        ]
    }
    """
    data = request.get_json(silent=True) or {}
    message = str(data.get("message", "")).strip()
    history = data.get("history")  # optional

    if not message:
        return jsonify({
            "success": False,
            "error": "message is required"
        }), 400

    try:
        result = rag_service.chat(user_message=message, history=history)
        return jsonify(result)
    except Exception as e:
        return jsonify({
            "success": False,
            "error": str(e)
        }), 500


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 5001))
    debug = os.environ.get("FLASK_DEBUG", "false").lower() == "true"
    app.run(host="0.0.0.0", port=port, debug=debug, use_reloader=debug)
