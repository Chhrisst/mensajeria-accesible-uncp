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

    @GET("api/messages/{userId}/{contactId}")
    suspend fun getMessages(
        @Path("userId") userId: String,
        @Path("contactId") contactId: String
    ): Response<List<ApiMessageResponse>>

    @POST("api/messages")
    suspend fun sendMessage(@Body request: ApiSendMessageRequest): Response<ApiMessageResponse>
}
