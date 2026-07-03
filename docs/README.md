# Wellness App Backend

## Environment Setup

### Prerequisites
- Java 17+
- Maven 3.8+
- Python 3.10+ (for the AI agent component)
- MySQL 8.0+ (optional; H2 in-memory database used by default for development)

### Configuration (`.env`)

All API keys are managed through a single `.env` file at the project root:

```
CA/
├── .env                  # <-- Fill this in before running
├── backend/
├── agent/
└── ...
```

Copy the template and fill in your keys:

```bash
# From the project root
cp .env.example .env
# Then edit .env with your real API keys
```

Required keys:

| Variable | Description |
|----------|-------------|
| `DEEPSEEK_API_KEY` | DeepSeek LLM API key |
| `DOUBAO_API_KEY` | Doubao Embedding API key (for RAG) |

The `.env` file is loaded by:
- **Java Backend**: `DotenvConfig` (via `dotenv-java` `EnvironmentPostProcessor`) at startup
- **Python Agent**: `load_dotenv("../.env")` via `python-dotenv`

> **Priority**: OS environment variables > `.env` file > defaults in code

### Run with H2 (default, no MySQL needed)

```bash
# Terminal 1 — Python Agent (RAG + AI chat)
cd agent
pip install -r requirements.txt
python main.py                    # → http://localhost:5001

# Terminal 2 — Java Backend
cd backend
mvn spring-boot:run               # → http://localhost:8080
```

H2 Console: http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:mem:wellnessdb`
- Username: `sa`
- Password: (empty)

### Run with MySQL

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

### API Base URL

`http://localhost:8080`

---

## Architecture

### Chat: 3-Tier Fallback Pipeline

```
Client → POST /api/chat
          │
          ├─ Tier 1: Python Agent /chat ─── DeepSeek + RAG (MSD Medical Knowledge Base)
          │   └─ 失败 → 走 Tier 2
          │
          ├─ Tier 2: AIClientService 直连 DeepSeek (无 RAG，但有历史对话)
          │   └─ 失败 → 走 Tier 3
          │
          └─ Tier 3: 静态 Fallback 消息
```

The chatbot intelligently degrades:
1. **Best case**: RAG-augmented answers with medical citations from the MSD Manuals knowledge base (see [ATTRIBUTION.md](../ATTRIBUTION.md) for content licensing)
2. **Degraded**: Direct DeepSeek with conversation history but no RAG
3. **Offline**: Simple static message when both services are unavailable

### AI Component: Python Agent

The Python agent (`agent/`) is a separate Flask microservice that provides:

| Endpoint | Description |
|----------|-------------|
| `POST /chat` | LLM chat with RAG tool calling (LLM decides when to search) |
| `POST /rag/reindex` | Rebuild the RAG index from scraper output |
| `POST /rag/ask` | One-shot RAG question-answering |
| `GET /rag/status` | Check RAG index status |

---

## Project Structure

```
backend/
├── pom.xml
├── src/main/java/com/wellnessapp/
│   ├── WellnessApplication.java          # Main entry point
│   ├── config/
│   │   └── DotenvConfig.java             # Loads .env via EnvironmentPostProcessor
│   ├── controller/
│   │   ├── AuthController.java           # Register / Login
│   │   ├── WellnessRecordController.java # CRUD wellness records
│   │   ├── ChatController.java           # Chat endpoints
│   │   ├── RecommendationController.java # AI recommendations
│   │   └── RagController.java            # RAG proxy endpoints
│   ├── dto/                              # Data Transfer Objects
│   ├── entity/                           # JPA entities
│   ├── repository/                       # Spring Data repositories
│   ├── security/                         # JWT & Spring Security
│   └── service/
│       ├── AIClientService.java          # Direct DeepSeek API (Tier-2 fallback)
│       ├── ChatService.java              # 3-tier fallback chat logic
│       ├── RagService.java               # Proxy to Python agent RAG endpoints
│       ├── AuthService.java              # Authentication logic
│       ├── WellnessRecordService.java    # Wellness records CRUD
│       ├── RecommendationService.java    # AI recommendations
│       └── JwtUtilProvider.java          # JWT utility bean
└── src/main/resources/
    ├── application.yml                   # Application configuration
    └── META-INF/spring/
        └── org.springframework.boot.env.EnvironmentPostProcessor  # SPI registration
```

Important: After cloning, you must start the Python agent first, then the backend.
