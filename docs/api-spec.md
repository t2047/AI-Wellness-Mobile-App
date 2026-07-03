# Wellness App REST API Specification

**Base URL:** `http://localhost:8080`
**Version:** 1.0
**Date:** 2026-06-25

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

## 5. Error Responses

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
