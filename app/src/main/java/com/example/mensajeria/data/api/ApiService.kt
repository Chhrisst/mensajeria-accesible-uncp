package com.example.mensajeria.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {

    @POST("api/auth/login")
    suspend fun login(@Body request: ApiLoginRequest): Response<ApiUserResponse>

    @POST("api/auth/register")
    suspend fun register(@Body request: ApiRegisterRequest): Response<ApiUserResponse>

    @GET("api/auth/users")
    suspend fun getUsers(): Response<List<ApiUserResponse>>

    @GET("api/contacts/{userId}")
    suspend fun getContacts(@Path("userId") userId: String): Response<List<ApiContactResponse>>

    @POST("api/contacts/add")
    suspend fun addContact(@Body request: ApiAddContactRequest): Response<ApiContactResponse>

    @retrofit2.http.DELETE("api/contacts/{userId}/{contactId}")
    suspend fun deleteContact(
        @Path("userId") userId: String,
        @Path("contactId") contactId: String
    ): Response<Map<String, Any>>

    @GET("api/messages/{userId}/{contactId}")
    suspend fun getMessages(
        @Path("userId") userId: String,
        @Path("contactId") contactId: String
    ): Response<List<ApiMessageResponse>>

    @POST("api/messages")
    suspend fun sendMessage(@Body request: ApiSendMessageRequest): Response<ApiMessageResponse>

    // ==========================================
    // ENDPOINTS DE INTELIGENCIA ARTIFICIAL (GEMINI)
    // ==========================================
    @POST("api/ai/summarize")
    suspend fun summarizeMessage(@Body request: ApiAiSummarizeRequest): Response<ApiAiResponse>

    @POST("api/ai/describe-image")
    suspend fun describeImage(@Body request: ApiAiDescribeImageRequest): Response<ApiAiResponse>

    @POST("api/ai/refine-speech")
    suspend fun refineSpeech(@Body request: ApiAiRefineSpeechRequest): Response<ApiAiResponse>
}
