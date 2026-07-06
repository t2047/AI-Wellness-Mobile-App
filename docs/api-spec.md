# Wellness App REST API Specification

**Base URL:** `http://localhost:8080`
**Version:** 1.1
**Date:** 2026-07-06

---

## 1. Authentication

All protected endpoints require:
```
Authorization: Bearer <jwt_token>
```

### 1.1 Register

```
POST /api/auth/register
```

**Request Body:**
```json
{
    "username": "demo_user",
    "email": "demo@example.com",
    "password": "Test1234!"
}
```

**Response (201 Created):**
```json
{
    "success": true,
    "message": "User registered successfully",
    "data": {
        "token": "eyJhbGciOiJIUzI1NiJ9...",
        "tokenType": "Bearer",
        "username": "demo_user",
        "userId": 1
    }
}
```

### 1.2 Login

```
POST /api/auth/login
```

**Request Body:**
```json
{
    "username": "demo_user",
    "password": "Test1234!"
}
```

**Response (200 OK):**
```json
{
    "success": true,
    "message": "Login successful",
    "data": {
        "token": "eyJhbGciOiJIUzI1NiJ9...",
        "tokenType": "Bearer",
        "username": "demo_user",
        "userId": 1
    }
}
```

---

## 2. Wellness Records

All endpoints require `Authorization: Bearer <token>`.

### 2.1 Get All Records

```
GET /api/wellness-records
```

**Response (200 OK):**
```json
{
    "success": true,
    "message": null,
    "data": [
        {
            "id": 1,
            "sleepHours": 7.5,
            "activityName": "Running",
            "activityDurationMinutes": 30,
            "recordDate": "2026-06-25",
            "notes": "Felt good today"
        }
    ]
}
```

### 2.2 Create Record

```
POST /api/wellness-records
```

**Request Body:**
```json
{
    "sleepHours": 7.5,
    "activityName": "Running",
    "activityDurationMinutes": 30,
    "recordDate": "2026-06-25",
    "notes": "Evening run around the park"
}
```

**Response (201 Created):**
```json
{
    "success": true,
    "message": "Record created successfully",
    "data": {
        "id": 1,
        "sleepHours": 7.5,
        "activityName": "Running",
        "activityDurationMinutes": 30,
        "recordDate": "2026-06-25",
        "notes": "Evening run around the park"
    }
}
```

### 2.3 Update Record

```
PUT /api/wellness-records/{id}
```

### 2.4 Delete Record

```
DELETE /api/wellness-records/{id}
```

**Response (200 OK):**
```json
{
    "success": true,
    "message": "Record deleted successfully",
    "data": null
}
```

---

## 3. Chatbot

Requires `Authorization: Bearer <token>`.

The chatbot uses a **3-tier fallback pipeline**:
1. **Python Agent RAG** (DeepSeek + [MSD Manuals](../ATTRIBUTION.md) medical knowledge base)
2. **Direct DeepSeek** (no RAG, with conversation history)
3. **Static fallback** (offline message)

### 3.1 Send Chat Message

```
POST /api/chat
```

**Request Body:**
```json
{
    "message": "How many hours should I sleep each night?"
}
```

**Response (200 OK) — Tier 1 (RAG):**
```json
{
    "success": true,
    "message": null,
    "data": {
        "reply": "Adults should aim for 7-9 hours of sleep per night. Consistency is key! [1]",
        "timestamp": "2026-06-25T14:30:00",
        "sources": [
            {
                "rank": 1,
                "title": "Sleep Disorders",
                "section": "Overview",
                "sourceUrl": "https://www.msdmanuals.com/...",
                "score": 0.92
            }
        ],
        "toolCalls": [
            {"tool": "rag_search", "arguments": {"question": "..."}, "result_summary": "..."}
        ]
    }
}
```

**Response (200 OK) — Tier 2 (Direct DeepSeek, no RAG):**
```json
{
    "success": true,
    "message": null,
    "data": {
        "reply": "Adults should aim for 7-9 hours of sleep per night. Consistency is key!",
        "timestamp": "2026-06-25T14:30:00",
        "sources": [],
        "toolCalls": []
    }
}
```

### 3.2 Get Chat History

```
GET /api/chat/history
```

---

## 4. Agentic AI Recommendations

Requires `Authorization: Bearer <token>`.

### 4.1 Trigger Recommendation Generation

```
POST /api/agent/recommendations
```

**Response (200 OK):**
```json
{
    "success": true,
    "message": "Recommendation generated successfully",
    "data": {
        "id": 1,
        "recommendationText": "Your sleep has been below 6 hours for 3 consecutive days...",
        "analysisSummary": "Sleep deficit trend detected over the past week...",
        "generatedAt": "2026-06-25T08:00:00",
        "isRead": false
    }
}
```

### 4.2 Get All Recommendations

```
GET /api/recommendations
```

**Response (200 OK):**
```json
{
    "success": true,
    "message": null,
    "data": [
        {
            "id": 1,
            "recommendationText": "...",
            "analysisSummary": "...",
            "generatedAt": "2026-06-25T08:00:00",
            "isRead": false
        }
    ]
}
```

---

## 5. Analytics Dashboard

Requires `Authorization: Bearer <token>`.

### 5.1 Get Dashboard Metrics

```
GET /api/analytics/dashboard?days=30
```

`days` is optional, defaults to `30`, and must be between `1` and `365`.

**Response (200 OK):**
```json
{
    "success": true,
    "message": null,
    "data": {
        "summary": {
            "startDate": "2026-06-04",
            "endDate": "2026-07-03",
            "days": 30,
            "totalRecords": 12,
            "recordedDays": 10,
            "recordCompletionRate": 33.3,
            "currentStreakDays": 4,
            "latestRecordDate": "2026-07-03",
            "averageSleepHours": 7.2,
            "minSleepHours": 5.5,
            "maxSleepHours": 8.5,
            "totalActivityMinutes": 360,
            "averageActivityMinutes": 30.0,
            "topActivityName": "Running",
            "unreadRecommendations": 2,
            "totalRecommendations": 5,
            "chatMessageCount": 8
        },
        "dailyMetrics": [
            {
                "date": "2026-07-03",
                "averageSleepHours": 7.5,
                "totalActivityMinutes": 30,
                "recordCount": 1,
                "hasRecord": true
            }
        ],
        "activityBreakdown": [
            {
                "activityName": "Running",
                "totalMinutes": 180,
                "recordCount": 4
            }
        ]
    }
}
```

---

## 6. Error Responses

All errors follow this format:
```json
{
    "success": false,
    "message": "Human-readable error description",
    "errorCode": "ERROR_CODE"
}
```

### HTTP Status Codes

| Code | Meaning |
|------|---------|
| 200  | Success |
| 201  | Created |
| 400  | Bad Request / Validation Error |
| 401  | Unauthorized (missing/invalid/expired JWT) |
| 403  | Forbidden |
| 404  | Resource Not Found |
| 409  | Conflict (e.g., duplicate username) |
| 500  | Internal Server Error |

### Error Codes

| Code | Description |
|------|-------------|
| `INVALID_CREDENTIALS` | Wrong username or password |
| `USERNAME_TAKEN` | Username already exists |
| `EMAIL_TAKEN` | Email already registered |
| `TOKEN_EXPIRED` | JWT has expired |
| `TOKEN_INVALID` | JWT is malformed |
| `RECORD_NOT_FOUND` | Wellness record ID does not exist |
| `ACCESS_DENIED` | Record belongs to another user |
| `AI_SERVICE_ERROR` | Chatbot/AI call failed |
| `VALIDATION_ERROR` | Request field validation failed |
| `CONFIG_NOT_FOUND` | User model config not found |
| `CONFIG_NOT_OWNED` | Config belongs to a different user |
| `CONFIG_INVALID` | User model config has invalid fields |

---

## 7. Model Configuration (User AI Settings)

Users can configure their preferred AI model parameters (provider, base URL, API key, model name) via these endpoints. All endpoints require JWT authentication.

The active config is used by `ChatService` Tier-2 fallback when the Python agent is unavailable.

### 7.1 List All Configs

```
GET /api/model-config
```

**Response:**
```json
{
    "success": true,
    "data": [
        {
            "id": 1,
            "providerName": "openai",
            "baseUrl": "https://api.openai.com/v1",
            "apiKeyMasked": "sk-...abcd",
            "modelName": "gpt-4o-mini",
            "isActive": true,
            "createdAt": "2026-07-06T10:00:00",
            "updatedAt": "2026-07-06T10:00:00"
        }
    ]
}
```

> **Note**: `apiKeyMasked` shows only the first 4 and last 4 characters. The full key is never exposed in API responses.

### 7.2 Get Active Config

```
GET /api/model-config/active
```

**Response (config exists):**
```json
{
    "success": true,
    "data": {
        "id": 1,
        "providerName": "openai",
        "baseUrl": "https://api.openai.com/v1",
        "apiKeyMasked": "sk-...abcd",
        "modelName": "gpt-4o-mini",
        "isActive": true,
        "createdAt": "2026-07-06T10:00:00",
        "updatedAt": "2026-07-06T10:00:00"
    }
}
```

**Response (no config):**
```json
{
    "success": true,
    "message": "No active config",
    "data": null
}
```

### 7.3 Save Config (Create or Update)

```
POST /api/model-config
```

**Request Body:**
```json
{
    "providerName": "openai",
    "baseUrl": "https://api.openai.com/v1",
    "apiKey": "sk-your-actual-api-key",
    "modelName": "gpt-4o-mini",
    "isActive": true
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `providerName` | string | Yes | Provider identifier: `openai`, `deepseek`, `doubao`, or custom |
| `baseUrl` | string | Yes | API endpoint base URL (e.g. `https://api.deepseek.com`) |
| `apiKey` | string | Yes | Full API key (stored in DB, masked in responses) |
| `modelName` | string | Yes | Model identifier (e.g. `deepseek-chat`, `gpt-4o-mini`) |
| `isActive` | boolean | No | Set to `true` to immediately activate this config (default: `true`) |

**Behavior**: If `isActive` is `true`, all other configs for this user are automatically deactivated. If a config with the same `providerName` already exists, it is updated; otherwise, a new config is created.

### 7.4 Delete Config

```
DELETE /api/model-config/{id}
```

**Response:**
```json
{
    "success": true,
    "message": "Config deleted",
    "data": null
}
```

### 7.5 Activate Config

```
PUT /api/model-config/{id}/activate
```

Activates the specified config and deactivates all other configs for the same user.

**Response:**
```json
{
    "success": true,
    "data": {
        "id": 1,
        "providerName": "deepseek",
        "baseUrl": "https://api.deepseek.com",
        "apiKeyMasked": "sk-...abcd",
        "modelName": "deepseek-chat",
        "isActive": true,
        "updatedAt": "2026-07-06T10:00:00"
    }
}
```
