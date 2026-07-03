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
"""

import os
from pathlib import Path

from flask import Flask, request, jsonify
from flask_cors import CORS
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

agent = WellnessAgent(backend_url=BACKEND_URL, jwt_token=JWT_TOKEN)
rag_service = RagService()


@app.route("/health", methods=["GET"])
def health():
    """Health check endpoint."""
    return jsonify({
        "status": "UP",
        "service": "wellness-agent",
        "version": "0.1.0"
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
    app.run(host="0.0.0.0", port=port, debug=True)
