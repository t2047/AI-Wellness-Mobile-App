package com.wellnessapp.data.api

import com.wellnessapp.data.model.*
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit API service interface defining all backend endpoints.
 *
 * @author WellnessApp Team
 */
interface ApiService {

    // --- Auth ---

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<ApiResponse<AuthResponse>>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<AuthResponse>>

    // --- Wellness Records ---

    @GET("api/wellness-records")
    suspend fun getWellnessRecords(): Response<ApiResponse<List<WellnessRecord>>>

    @POST("api/wellness-records")
    suspend fun createWellnessRecord(@Body record: WellnessRecord): Response<ApiResponse<WellnessRecord>>

    @PUT("api/wellness-records/{id}")
    suspend fun updateWellnessRecord(
        @Path("id") id: Long,
        @Body record: WellnessRecord
    ): Response<ApiResponse<WellnessRecord>>

    @DELETE("api/wellness-records/{id}")
    suspend fun deleteWellnessRecord(@Path("id") id: Long): Response<ApiResponse<Unit>>

    // --- Chat ---

    @POST("api/chat")
    suspend fun sendChatMessage(@Body request: ChatRequest): Response<ApiResponse<ChatResponse>>

    @GET("api/chat/history")
    suspend fun getChatHistory(): Response<ApiResponse<List<ChatMessage>>>

    // --- Recommendations ---

    @POST("api/agent/recommendations")
    suspend fun triggerRecommendation(): Response<ApiResponse<Recommendation>>

    @GET("api/recommendations")
    suspend fun getRecommendations(): Response<ApiResponse<List<Recommendation>>>

    // --- Weekly Summaries ---

    @POST("api/weekly-summaries/generate")
    suspend fun generateWeeklySummary(): Response<ApiResponse<WeeklySummary>>

    @GET("api/weekly-summaries")
    suspend fun getWeeklySummaries(): Response<ApiResponse<List<WeeklySummary>>>

    // --- RAG (Drug Knowledge) ---

    @GET("api/rag/status")
    suspend fun getRagStatus(): Response<ApiResponse<RagStatusResponse>>

    @POST("api/rag/reindex")
    suspend fun startRagReindex(@Body request: Map<String, Boolean>): Response<ApiResponse<RagReindexResponse>>

    @GET("api/rag/reindex-status/{taskId}")
    suspend fun getRagReindexStatus(@Path("taskId") taskId: String): Response<ApiResponse<RagReindexStatusResponse>>

    @POST("api/rag/ask")
    suspend fun askRag(@Body request: RagAskRequest): Response<ApiResponse<RagAskResponse>>
}
