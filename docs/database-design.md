# Database Design

## ERD

```
┌─────────────────┐       ┌────────────────────────┐
│     users        │       │   wellness_records      │
├─────────────────┤       ├────────────────────────┤
│ id (PK)         │──┐    │ id (PK)                 │
│ username (UQ)   │  │    │ user_id (FK → users.id) │
│ email (UQ)      │  ├───>│ sleep_hours             │
│ password_hash    │  │    │ activity_name           │
│ created_at      │  │    │ activity_duration_mins  │
│ updated_at      │  │    │ record_date             │
└─────────────────┘  │    │ notes                   │
       │              │    │ created_at              │
       │              │    │ updated_at              │
       │              │    └────────────────────────┘
       │              │
       │              │    ┌────────────────────────┐
       │              │    │    chat_messages        │
       │              │    ├────────────────────────┤
       │              │    │ id (PK)                 │
       │              ├───>│ user_id (FK → users.id) │
       │              │    │ user_message            │
       │              │    │ bot_response            │
       │              │    │ created_at              │
       │              │    └────────────────────────┘
       │              │
       │              │    ┌────────────────────────┐
       │              │    │   recommendations       │
       │              │    ├────────────────────────┤
       │              ├───>│ id (PK)                 │
       │              │    │ user_id (FK → users.id) │
       │              │    │ recommendation_text     │
       │              │    │ analysis_summary        │
       │              │    │ generated_at            │
       │              │    │ is_read                 │
       │              │    └────────────────────────┘
       │              │
       │              │    ┌────────────────────────┐
       │              │    │   weekly_summaries      │
       │              │    ├────────────────────────┤
       │              ├───>│ id (PK)                 │
       │              │    │ user_id (FK → users.id) │
       │              │    │ week_start_date         │
       │              │    │ week_end_date           │
       │              │    │ average_sleep_hours     │
       │              │    │ total_activity_minutes  │
       │              │    │ active_days             │
       │              │    │ record_count            │
       │              │    │ summary_text            │
       │              │    │ recommendation_text     │
       │              │    │ generated_at            │
       │              │    └────────────────────────┘
       │              │
       │              │    ┌──────────────────────────┐
       │              │    │    user_model_configs     │
       │              │    ├──────────────────────────┤
       │              └───>│ id (PK)                   │
       │                   │ user_id (FK → users.id)   │
       │                   │ provider_name             │
       │                   │ base_url                  │
       │                   │ api_key                   │
       │                   │ model_name                │
       │                   │ is_active                 │
       │                   │ created_at                │
       │                   │ updated_at                │
       └─────────────────┘  └──────────────────────────┘
```

## Table Details

### users
| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| username | VARCHAR(50) | UNIQUE, NOT NULL | Login identifier |
| email | VARCHAR(100) | UNIQUE, NOT NULL | |
| password_hash | VARCHAR(255) | NOT NULL | BCrypt hash |
| created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | |
| updated_at | TIMESTAMP | ON UPDATE CURRENT_TIMESTAMP | |

### wellness_records
| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| user_id | BIGINT | FK → users.id, NOT NULL | Owner |
| sleep_hours | DECIMAL(4,1) | | Hours slept |
| activity_name | VARCHAR(100) | | Exercise/activity |
| activity_duration_minutes | INT | | Duration in minutes |
| record_date | DATE | NOT NULL | Date of record |
| notes | TEXT | | Free text notes |
| created_at / updated_at | TIMESTAMP | | Audit columns |

### chat_messages
| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| user_id | BIGINT | FK → users.id, NOT NULL | |
| user_message | TEXT | NOT NULL | |
| bot_response | TEXT | NOT NULL | |
| created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | |

### recommendations
| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| user_id | BIGINT | FK → users.id, NOT NULL | |
| recommendation_text | TEXT | NOT NULL | AI recommendation |
| analysis_summary | TEXT | | Trend analysis detail |
| generated_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | |
| is_read | BOOLEAN | DEFAULT FALSE | Read status |

### weekly_summaries
| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| user_id | BIGINT | FK → users.id, NOT NULL | |
| week_start_date | DATE | NOT NULL | |
| week_end_date | DATE | NOT NULL | |
| average_sleep_hours | DECIMAL(4,1) | | Average sleep in period |
| total_activity_minutes | INT | DEFAULT 0 | |
| active_days | INT | DEFAULT 0 | |
| record_count | INT | DEFAULT 0 | |
| summary_text | TEXT | | Generated weekly summary |
| recommendation_text | TEXT | | Generated recommendation |
| generated_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | |

### user_model_configs
| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| user_id | BIGINT | FK → users.id, NOT NULL | Owner |
| provider_name | VARCHAR(50) | NOT NULL | Provider identifier: `openai`, `deepseek`, `doubao`, `custom` |
| base_url | VARCHAR(500) | NOT NULL | API endpoint base URL |
| api_key | VARCHAR(500) | NOT NULL | Full API key (masked in API responses) |
| model_name | VARCHAR(100) | NOT NULL | Model identifier |
| is_active | BOOLEAN | DEFAULT TRUE | Whether this config is currently active |
| created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | |
| updated_at | TIMESTAMP | ON UPDATE CURRENT_TIMESTAMP | |

## Key Design Decisions

1. **Cascade deletes**: Deleting a user cascades to all child tables (records, messages, recommendations, summaries, model configs).
2. **Indexes**: Added on `user_id` foreign keys and commonly queried columns (`record_date`, `generated_at`, `provider_name`).
3. **DECIMAL(4,1)**: Sleep hours support values like 7.5 with one decimal place.
4. **TEXT columns**: Notes, messages, and recommendations use TEXT for flexibility.
5. **is_read flag**: Allows Android to show unread recommendation badges.
6. **user_model_configs**: Stores per-user AI model settings. Only one config is active per user at a time. API key is returned masked in API responses (`sk-...xxxx`).
