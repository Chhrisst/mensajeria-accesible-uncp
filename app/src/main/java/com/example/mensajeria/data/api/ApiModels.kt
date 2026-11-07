package com.example.mensajeria.data.api

import com.google.gson.annotations.SerializedName

data class ApiLoginRequest(
    @SerializedName("query") val query: String,
    @SerializedName("password_or_pin") val passwordOrPin: String
)

data class ApiRegisterRequest(
    @SerializedName("full_name") val fullName: String,
    @SerializedName("email") val email: String,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("dni") val dni: String? = null,
    @SerializedName("birth_date") val birthDate: String? = null,
    @SerializedName("pin") val pin: String = "1234",
    @SerializedName("side_pattern") val sidePattern: String = "LRLR",
    @SerializedName("has_disability") val hasDisability: Boolean = true,
    @SerializedName("visual_condition") val visualCondition: String = "Baja visión"
)

data class ApiUserResponse(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("email") val email: String,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("dni") val dni: String? = null,
    @SerializedName("code") val code: String = "1234",
    @SerializedName("side_pattern") val sidePattern: String = "LRLR",
    @SerializedName("role") val role: String = "Socio UNCP",
    @SerializedName("description") val description: String? = null,
    @SerializedName("has_disability") val hasDisability: Boolean = true,
    @SerializedName("visual_condition") val visualCondition: String = "Baja visión"
)

data class ApiContactResponse(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("email") val email: String,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("role") val role: String,
    @SerializedName("visual_condition") val visualCondition: String,
    @SerializedName("last_message") val lastMessage: String? = null,
    @SerializedName("last_message_time") val lastMessageTime: String? = null,
    @SerializedName("unread_count") val unreadCount: Int? = 0
)

data class ApiSendMessageRequest(
    @SerializedName("sender_id") val senderId: String,
    @SerializedName("receiver_id") val receiverId: String,
    @SerializedName("content") val content: String,
    @SerializedName("message_type") val messageType: String = "TEXT",
    @SerializedName("audio_url") val audioUrl: String? = null,
    @SerializedName("audio_duration_seconds") val audioDurationSeconds: Int = 0
)

data class ApiMessageResponse(
    @SerializedName("id") val id: String,
    @SerializedName("sender_id") val senderId: String,
    @SerializedName("receiver_id") val receiverId: String,
    @SerializedName("content") val content: String?,
    @SerializedName("message_type") val messageType: String,
    @SerializedName("audio_url") val audioUrl: String? = null,
    @SerializedName("audio_duration_seconds") val audioDurationSeconds: Int = 0,
    @SerializedName("is_read") val isRead: Boolean = false,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("is_from_me") val isFromMe: Boolean = false
)

// ==========================================
// MODELOS DE ASISTENCIA POR INTELIGENCIA ARTIFICIAL (GEMINI)
// ==========================================
data class ApiAiSummarizeRequest(
    @SerializedName("text") val text: String
)

data class ApiAiDescribeImageRequest(
    @SerializedName("image_base64") val imageBase64: String,
    @SerializedName("mime_type") val mimeType: String = "image/jpeg"
)

data class ApiAiRefineSpeechRequest(
    @SerializedName("raw_text") val rawText: String
)

data class ApiAiResponse(
    @SerializedName("result") val result: String,
    @SerializedName("status") val status: String = "success"
)
