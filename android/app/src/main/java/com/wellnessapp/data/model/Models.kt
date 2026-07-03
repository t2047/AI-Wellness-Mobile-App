package com.wellnessapp.data.model

import com.google.gson.annotations.SerializedName

/**
 * Data models for the Wellness App.
 * Maps to backend DTOs.
 *
 * @author WellnessApp Team
 */

// --- Auth ---

data class LoginRequest(
    @SerializedName("username") val username: String,
    @SerializedName("password") val password: String
)

data class RegisterRequest(
    @SerializedName("username") val username: String,
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

data class AuthResponse(
    @SerializedName("token") val token: String,
    @SerializedName("tokenType") val tokenType: String,
    @SerializedName("username") val username: String,
    @SerializedName("userId") val userId: Long
)

// --- Wellness Record ---

data class WellnessRecord(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("sleepHours") val sleepHours: Double? = null,
    @SerializedName("activityName") val activityName: String? = null,
    @SerializedName("activityDurationMinutes") val activityDurationMinutes: Int? = null,
    @SerializedName("recordDate") val recordDate: String,
    @SerializedName("notes") val notes: String? = null
)

// --- Chat ---

data class ChatRequest(
    @SerializedName("message") val message: String
)

data class ChatResponse(
    @SerializedName("reply") val reply: String,
    @SerializedName("timestamp") val timestamp: String? = null
)

data class ChatMessage(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("userMessage") val userMessage: String,
    @SerializedName("botResponse") val botResponse: String,
    @SerializedName("createdAt") val createdAt: String? = null
)

// --- Recommendation ---

data class Recommendation(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("recommendationText") val recommendationText: String,
    @SerializedName("analysisSummary") val analysisSummary: String? = null,
    @SerializedName("generatedAt") val generatedAt: String? = null,
    @SerializedName("isRead") val isRead: Boolean = false
)

// --- Common API Response Wrapper ---

data class ApiResponse<T>(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: T? = null
)

data class ErrorResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("errorCode") val errorCode: String? = null
)

// --- RAG (Drug Knowledge) ---

data class RagAskRequest(
    @SerializedName("question") val question: String,
    @SerializedName("topK") val topK: Int? = null
)

data class RagAskResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("answer") val answer: String? = null,
    @SerializedName("sources") val sources: List<RagSource>? = null
)

data class RagSource(
    @SerializedName("rank") val rank: Int,
    @SerializedName("score") val score: Double,
    @SerializedName("title") val title: String,
    @SerializedName("section_title") val sectionTitle: String,
    @SerializedName("chunk_id") val chunkId: Int,
    @SerializedName("source_url") val sourceUrl: String,
    @SerializedName("snippet") val snippet: String
)

data class RagStatusResponse(
    @SerializedName("indexPath") val indexPath: String? = null,
    @SerializedName("builtAt") val builtAt: String? = null,
    @SerializedName("documentCount") val documentCount: Int = 0,
    @SerializedName("sourceCount") val sourceCount: Int = 0,
    @SerializedName("corpusGlob") val corpusGlob: String? = null,
    @SerializedName("deepseekModel") val deepseekModel: String? = null,
    @SerializedName("doubaoModel") val doubaoModel: String? = null
)

data class RagReindexResponse(
    @SerializedName("taskId") val taskId: String,
    @SerializedName("status") val status: String
)

data class RagReindexStatusResponse(
    @SerializedName("taskId") val taskId: String,
    @SerializedName("status") val status: String,
    @SerializedName("progress") val progress: Int = 0,
    @SerializedName("phase") val phase: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("startedAt") val startedAt: String? = null,
    @SerializedName("completedAt") val completedAt: String? = null
)
