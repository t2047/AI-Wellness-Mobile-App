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
AI_PROVIDER=deepseek
DEEPSEEK_API_KEY=
DOUBAO_API_KEY=
MYSQL_USER=root
MYSQL_PASSWORD=
```

> **Note**: Users can also configure their own model keys via the Android app's
> **Settings** tab, which saves per-user model configs to the database. See
> [api-spec.md](docs/api-spec.md#7-model-configuration-user-ai-settings) for details.

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
│             │     │  Spring Boot  │     │  Flask           │
│  5 tabs:    │     │              │     │  ├── /chat (RAG) │
│  ─────────  │     │  Chat: 3-tier│     │  ├── /rag/*      │
│  Dashboard  │     │  fallback:   │     │  └── /analyze    │
│  Records    │     │  1. Python   │     └──────────────────┘
│  Coach      │     │     Agent    │
│  Chat       │     │  2. User     │     ┌──────────────────┐
│  Knowledge  │     │     Config   │────▶│  User's own AI   │
│  Settings───│────▶│  3. Global   │     │  (per-user keys) │
└─────────────┘     │     .env     │     └──────────────────┘
                    └──────────────┘
```

## Tech Stack
- **Android:** Kotlin, Retrofit, ViewModel, LiveData, MPAndroidChart
- **Backend:** Spring Boot 3.2, Spring Security, JPA, JWT (jjwt)
- **Database:** MySQL 8.0 / H2 (dev)
- **AI:** DeepSeek (primary LLM), Doubao (embeddings), DashScope/OpenAI-compatible fallback
- **RAG:** Document embedding with cosine similarity retrieval on MSD medical corpus

## Documentation

- [Content Attribution](ATTRIBUTION.md) — MSD Manuals & third-party licenses
- [Backend Setup & Architecture](docs/README.md)
- [CA Testing Guide](docs/CA_TESTING.md)
- [API Specification](docs/api-spec.md)
- [Database Design](docs/database-design.md)
- [Development Prompts](docs/prompts.md)
- [Android Updates & Bug Fixes](android/updates.md)
- [Project Updates & Changelog](docs/updates.md)
