package com.example.mensajeria

import com.example.mensajeria.data.api.ApiContactResponse
import com.example.mensajeria.data.api.ApiMessageResponse
import com.example.mensajeria.data.api.RetrofitClient
import com.example.mensajeria.data.model.RegistrationForm
import com.example.mensajeria.data.repository.AuthRepository
import com.example.mensajeria.data.repository.ChatRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.UUID

/**
 * Resiliencia ante fallos de red: ninguna llamada debe propagar excepciones no controladas
 * y el estado local (mensajes/contactos/sesión) debe quedar coherente.
 */
class NetworkResilienceTest {

    private val api = FakeApiService()
    private val userId = UUID.randomUUID().toString()
    private val contactId = UUID.randomUUID().toString()
    private lateinit var chat: ChatRepository

    @Before
    fun setUp() {
        RetrofitClient.apiOverride = api
        chat = ChatRepository()
        chat.setSessionUser(userId)
    }

    @After
    fun tearDown() {
        RetrofitClient.apiOverride = null
    }

    @Test
    fun sendMessage_ioException_returnsFailureAndKeepsMessageLocally() = runBlocking {
        api.failure = IOException("sin red")
        chat.loadMessagesFromRemote(userId, contactId) // abre la conversación (también falla, sin lanzar)

        val result = chat.sendMessageRemote(userId, contactId, "Hola sin red")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("Sin conexión"))
        assertEquals(listOf("Hola sin red"), chat.messages.value.map { it.text })
    }

    @Test
    fun sendMessage_timeout_returnsFailureWithoutThrowing() = runBlocking {
        api.failure = SocketTimeoutException("timeout")
        chat.loadMessagesFromRemote(userId, contactId)

        val result = chat.sendMessageRemote(userId, contactId, "Mensaje lento")

        assertTrue(result.isFailure)
        assertEquals(1, chat.messages.value.size)
    }

    @Test
    fun sendMessage_http500_isReportedAsFailureNotSuccess() = runBlocking {
        chat.loadMessagesFromRemote(userId, contactId)
        api.sendMessageResponse = FakeApiService.error(500)

        val result = chat.sendMessageRemote(userId, contactId, "Hola")

        assertTrue(result.isFailure)
        assertEquals(1, chat.messages.value.size)
    }

    @Test
    fun pendingMessage_survivesRefreshAfterNetworkRecovers() = runBlocking {
        api.failure = IOException("sin red")
        chat.loadMessagesFromRemote(userId, contactId)
        chat.sendMessageRemote(userId, contactId, "Pendiente")

        // La red vuelve: el servidor no conoce el mensaje pendiente, pero no debe desaparecer
        api.failure = null
        api.messagesResponse = Response.success(
            listOf(ApiMessageResponse("srv1", contactId, userId, "Del servidor", "TEXT"))
        )
        val merged = chat.loadMessagesFromRemote(userId, contactId, loadLocalFirst = false)

        assertEquals(listOf("Del servidor", "Pendiente"), merged.map { it.text })
    }

    @Test
    fun loadMessages_ioException_keepsCurrentMessages() = runBlocking {
        chat.loadMessagesFromRemote(userId, contactId)
        chat.receiveMessage("Mensaje previo")
        api.failure = IOException("sin red")

        val result = chat.loadMessagesFromRemote(userId, contactId, loadLocalFirst = false)

        assertEquals(listOf("Mensaje previo"), result.map { it.text })
    }

    @Test
    fun syncContacts_timeout_keepsCachedContacts() = runBlocking {
        api.contactsResponse = Response.success(listOf(ApiContactResponse("id-ana", "Ana")))
        chat.syncContactsFromRemote()
        api.failure = SocketTimeoutException("timeout")

        val result = chat.syncContactsFromRemote()

        assertEquals(listOf("Ana"), result.map { it.name })
        assertEquals(listOf("Ana"), chat.contacts.value.map { it.name })
    }

    @Test
    fun addContact_ioException_returnsSpanishFailureAndLeavesContactsUntouched() = runBlocking {
        api.failure = IOException("sin red")

        val result = chat.addContactByUsername(userId, "@maria")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("Sin conexión"))
        assertTrue(chat.contacts.value.isEmpty())
    }

    @Test
    fun addContact_userNotFound_returnsClearMessage() = runBlocking {
        api.addContactResponse = FakeApiService.error(404)

        val result = chat.addContactByUsername(userId, "fantasma")

        assertEquals("El usuario @fantasma no existe en la aplicación de mensajería.", result.exceptionOrNull()?.message)
    }

    @Test
    fun login_ioException_unknownUser_failsWithoutSession() = runBlocking {
        api.failure = IOException("sin red")
        val auth = AuthRepository()

        val result = auth.loginRemote("usuario-inexistente-${UUID.randomUUID()}", "secreto9")

        assertTrue(result.isFailure)
        assertNull(auth.currentUser.value)
    }

    @Test
    fun register_ioException_fallsBackToLocalAccount() = runBlocking {
        api.failure = SocketTimeoutException("timeout")
        val auth = AuthRepository()
        val name = "Offline ${UUID.randomUUID().toString().take(6)}"

        val result = auth.registerRemote(RegistrationForm(fullName = name, email = "off@line.pe", pin = "clave99"))

        assertTrue(result.isSuccess)
        assertEquals(name, auth.currentUser.value?.name)
    }
}
