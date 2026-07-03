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

Edit `CA/.env` at the project root with your API keys:

```env
DEEPSEEK_API_KEY=sk-your-key-here
DOUBAO_API_KEY=your-key-here
```

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
- **AI:** DeepSeek LLM + Doubao Embeddings + RAG (Python agent)

## Documentation

- [Content Attribution](ATTRIBUTION.md) — MSD Manuals & third-party licenses
- [Backend Setup & Architecture](docs/README.md)
- [API Specification](docs/api-spec.md)
- [Database Design](docs/database-design.md)
- [Development Prompts](docs/prompts.md)
