-- @author: Jia Qianrui
-- @author: Liu Zhuocheng
-- @author: Tao Yuchen

-- =====================================================
-- Wellness App Database Initialization Script
-- Author: WellnessApp Team
-- Date: 2026-06-25
-- Description: Creates the core tables for the AI-Enabled
--              Wellness Mobile App.
-- =====================================================

CREATE DATABASE IF NOT EXISTS wellness_db;
USE wellness_db;

-- ----------------------------------------------------
-- Table: users
-- Description: Stores registered user accounts.
-- ----------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_users_username (username),
    INDEX idx_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------
-- Table: wellness_records
-- Description: Stores user health/wellness records.
-- ----------------------------------------------------
CREATE TABLE IF NOT EXISTS wellness_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    sleep_hours DECIMAL(4,1) COMMENT 'Hours of sleep',
    activity_name VARCHAR(100) COMMENT 'Name of exercise/activity',
    activity_duration_minutes INT COMMENT 'Duration of activity in minutes',
    record_date DATE NOT NULL COMMENT 'Date of the record',
    notes TEXT COMMENT 'Additional notes',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_records_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_records_user_id (user_id),
    INDEX idx_records_date (record_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------
-- Table: chat_messages
-- Description: Stores chatbot conversation history.
-- ----------------------------------------------------
CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    user_message TEXT NOT NULL COMMENT 'Message sent by user',
    bot_response TEXT NOT NULL COMMENT 'Response from AI chatbot',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_chat_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------
-- Table: recommendations
-- Description: Stores agentic AI generated recommendations.
-- ----------------------------------------------------
CREATE TABLE IF NOT EXISTS recommendations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    recommendation_text TEXT NOT NULL COMMENT 'AI-generated health recommendation',
    analysis_summary TEXT COMMENT 'Summary of trend analysis that led to this recommendation',
    generated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_read BOOLEAN DEFAULT FALSE COMMENT 'Whether user has read this recommendation',
    CONSTRAINT fk_recommendation_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_recommendation_user_id (user_id),
    INDEX idx_recommendation_generated_at (generated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------
-- Table: weekly_summaries
-- Description: Stores manually generated weekly health summaries.
-- ----------------------------------------------------
CREATE TABLE IF NOT EXISTS weekly_summaries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    week_start_date DATE NOT NULL,
    week_end_date DATE NOT NULL,
    average_sleep_hours DECIMAL(4,1) COMMENT 'Average sleep hours in this summary period',
    total_activity_minutes INT DEFAULT 0 COMMENT 'Total activity minutes in this summary period',
    active_days INT DEFAULT 0 COMMENT 'Number of days with recorded activity',
    record_count INT DEFAULT 0 COMMENT 'Number of wellness records summarized',
    summary_text TEXT COMMENT 'Generated weekly summary',
    recommendation_text TEXT COMMENT 'Generated recommendation for next week',
    generated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_weekly_summaries_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_weekly_summaries_user_id (user_id),
    INDEX idx_weekly_summaries_generated_at (generated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------
-- Table: user_model_configs
-- Description: Stores per-user AI model configuration (base URL, API key, model).
-- ----------------------------------------------------
CREATE TABLE IF NOT EXISTS user_model_configs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    provider_name VARCHAR(50) NOT NULL COMMENT 'openai, deepseek, doubao, custom',
    base_url VARCHAR(500) NOT NULL COMMENT 'API endpoint base URL',
    api_key VARCHAR(500) NOT NULL COMMENT 'Encrypted API key',
    model_name VARCHAR(100) NOT NULL COMMENT 'Model identifier (e.g. gpt-4o-mini)',
    is_active BOOLEAN DEFAULT TRUE COMMENT 'Whether this config is currently active',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_model_config_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_model_config_user_id (user_id),
    INDEX idx_model_config_provider (user_id, provider_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------
-- Insert test user for development
-- Password: Test1234! (BCrypt hashed)
-- ----------------------------------------------------
-- INSERT INTO users (username, email, password_hash) VALUES
-- ('demo', 'demo@wellness.com', '$2a$10$placeholder');
