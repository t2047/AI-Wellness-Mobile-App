# AI-Enabled Wellness Mobile App

Team project for Mobile Application Development CA.

## Project Structure

```
├── android/            # Kotlin Android Application
├── backend/            # Java Spring Boot Backend (port 8080)
├── agent/              # Python Agentic AI — RAG + LLM Chat (port 5001)
├── scraper/            # Web scraper for MSD medical knowledge base
├── database/           # MySQL Schema & Init Scripts
├── docs/               # Documentation & API Spec
└── .env                # Unified API key configuration (gitignored)
```

## Quick Start

### 1. Configure API Keys

Copy `.env.example` to `.env` at the project root and fill in local API keys.
Never commit `.env` or paste real keys into shared docs/code.

```env
AI_PROVIDER=openai
DASHSCOPE_API_KEY=
OPENAI_CHAT_URL=https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions
OPENAI_MODEL=qwen3.7-plus
MYSQL_USER=root
MYSQL_PASSWORD=
```

For a dedicated Alibaba Cloud Model Studio deployment, replace
`OPENAI_CHAT_URL` with the full Chat Completions URL shown in its API example
(the configured `base_url` plus `/chat/completions`). Do not commit `.env`.

### 2. Start the Python Agent (RAG + AI Chat)

```bash
cd agent
pip install -r requirements.txt
python main.py
```

### 3. Start the Backend

```bash
cd backend
mvn spring-boot:run
```

For the dedicated CA test profile:

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=CA
```

### 4. Android

Open `android/` in Android Studio, sync Gradle, and run on emulator.

## Architecture

```
┌─────────────┐     ┌──────────────┐     ┌──────────────────┐
│  Android     │────▶│  Backend     │────▶│  Python Agent    │
│  Kotlin App  │     │  :8080       │     │  :5001           │
└─────────────┘     │  Spring Boot  │     │  Flask           │
                    │               │     │  ├── /chat (RAG) │
                    │  Chat: 3-tier │     │  ├── /rag/*      │
                    │  fallback     │     │  └── /analyze    │
                    │               │     └──────────────────┘
                    │  ┌─────────┐  │
                    │  │ DeepSeek│  │  ← direct fallback
                    │  │ API     │  │
                    │  └─────────┘  │
                    └──────────────┘
```

## Tech Stack
- **Android:** Kotlin, Retrofit, ViewModel, LiveData
- **Backend:** Spring Boot 3.2, Spring Security, JPA
- **Database:** MySQL 8.0 / H2 (dev)
- **Auth:** JWT (jjwt)
- **AI:** OpenAI-compatible chat provider (including DashScope/Qwen), optional DeepSeek + Doubao RAG

## Documentation

- [Content Attribution](ATTRIBUTION.md) — MSD Manuals & third-party licenses
- [Backend Setup & Architecture](docs/README.md)
- [CA Testing Guide](docs/CA_TESTING.md)
- [API Specification](docs/api-spec.md)
- [Database Design](docs/database-design.md)
- [Development Prompts](docs/prompts.md)
