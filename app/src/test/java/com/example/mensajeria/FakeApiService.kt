package com.example.mensajeria

import com.example.mensajeria.data.api.*
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

/**
 * ApiService falso para pruebas JVM: si [failure] no es null, toda llamada lo lanza
 * (IOException, SocketTimeoutException...). Si no, devuelve las respuestas configuradas.
 */
class FakeApiService : ApiService {
    var failure: Throwable? = null
    var loginResponse: Response<ApiUserResponse> = error(401)
    var contactsResponse: Response<List<ApiContactResponse>> = Response.success(emptyList())
    var addContactResponse: Response<ApiContactResponse> = error(404)
    var messagesResponse: Response<List<ApiMessageResponse>> = Response.success(emptyList())
    var sendMessageResponse: Response<ApiMessageResponse> = error(500)
    val sentMessages = mutableListOf<ApiSendMessageRequest>()
    val loginRequests = mutableListOf<ApiLoginRequest>()
    var calls = 0

    private fun hit() { calls++; failure?.let { throw it } }

    override suspend fun login(request: ApiLoginRequest) = hit().let { loginRequests += request; loginResponse }
    override suspend fun register(request: ApiRegisterRequest): Response<ApiUserResponse> = hit().let { error(500) }
    override suspend fun getUsers(): Response<List<ApiUserResponse>> = hit().let { Response.success(emptyList()) }
    override suspend fun getContacts(userId: String) = hit().let { contactsResponse }
    override suspend fun addContact(request: ApiAddContactRequest) = hit().let { addContactResponse }
    override suspend fun deleteContact(userId: String, contactId: String): Response<Map<String, Any>> = hit().let { error(500) }
    override suspend fun getMessages(userId: String, contactId: String) = hit().let { messagesResponse }
    override suspend fun sendMessage(request: ApiSendMessageRequest) = hit().let { sentMessages += request; sendMessageResponse }
    override suspend fun summarizeMessage(request: ApiAiSummarizeRequest): Response<ApiAiResponse> = hit().let { error(500) }
    override suspend fun describeImage(request: ApiAiDescribeImageRequest): Response<ApiAiResponse> = hit().let { error(500) }
    override suspend fun refineSpeech(request: ApiAiRefineSpeechRequest): Response<ApiAiResponse> = hit().let { error(500) }

    companion object {
        fun <T> error(code: Int): Response<T> = Response.error(code, "{}".toResponseBody(null))
    }
}
