# CA Testing Guide

This guide describes a safe local setup for the CA demo and submission checks. Do not commit `.env`, local database files, build outputs, IDE settings, keystores, or real API keys.

## Environment Variables

Create a project-root `.env` from `.env.example` and fill values locally. Use placeholders in documentation and shared screenshots.

Required or commonly used variables:

```env
MYSQL_PASSWORD=
AI_PROVIDER=deepseek
DEEPSEEK_API_KEY=
DOUBAO_API_KEY=
```

**Per-user config**: Users can also configure their own AI model keys via the Android
app's **Settings** tab, without needing server `.env` changes. See
[api-spec.md §7](api-spec.md#7-model-configuration-user-ai-settings).

## Start the Python Agent

From the project root:

```bash
cd agent
pip install -r requirements.txt
python main.py
```

The agent runs on `http://localhost:5001` by default.

If `DOUBAO_API_KEY` is not configured, the agent should still start. The Doubao/RAG endpoints remain present, but full RAG indexing is unavailable and chat/RAG ask can use the OpenAI-compatible GPT fallback when configured.

## Start the Backend with the CA Profile

From the project root:

```bash
mvn -f backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=CA
```

The CA profile uses a local H2 file database under `backend/data/`. This folder is ignored by Git and should not be staged.

Health check:

```bash
curl http://localhost:8080/api/health
```

## Start the Backend with MySQL

Make sure MySQL is running and that the configured user can create or access `wellness_db`.

```bash
export MYSQL_PASSWORD=your-local-password
mvn -f backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=mysql
```

If the MySQL profile fails with authentication or connection errors, check:

- MySQL service is running.
- The username in `application.yml` matches a real local MySQL user.
- `MYSQL_PASSWORD` is set in the local environment or `.env`.
- The user has permission to create or use `wellness_db`.
- Port `3306` is available and not blocked.

Do not write the real MySQL password into `application.yml`, README, screenshots, or commit messages.

## Android Studio Test

Open `android/` in Android Studio, sync Gradle, and run the app on an emulator.

The app is configured to call the backend at:

```text
http://10.0.2.2:8080
```

`10.0.2.2` is the Android emulator address for the host machine. Keep the backend running on host port `8080` during testing.

## Recommended CA Smoke Test

Run these checks before submission:

1. Start Python agent.
2. Start backend with the CA profile or MySQL profile.
3. Open Android app in emulator.
4. Register a new user.
5. Log in and confirm protected screens load.
6. Create, view, update, and delete a wellness record.
7. Generate a weekly health summary and view the history list.
8. Generate an AI recommendation.
9. Ask the chatbot a personal wellness question, such as `How was my sleep this week?`.
10. Check RAG status.
11. Ask a RAG question.
12. **Open Settings tab and configure a custom AI model**:
    - Enter Provider (e.g. `deepseek`), Base URL, API Key, Model Name
    - Tap Save Configuration
    - Confirm the config appears in the saved list with an `● ACTIVE` badge
13. Test chat with the custom config — send a wellness question.
14. Log out and confirm the session is cleared.

## Submission Safety Checklist

Before staging files:

- Confirm `.env` is ignored.
- Confirm `backend/data/` is ignored.
- Confirm `tmp/` is ignored.
- Confirm Android build outputs are ignored.
- Confirm no real API keys, tokens, JWTs, database passwords, keystores, or personal IDE files are staged.
