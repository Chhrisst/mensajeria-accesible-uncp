package com.example.mensajeria

import com.example.mensajeria.data.api.ApiContactResponse
import com.example.mensajeria.data.api.ApiMessageResponse
import com.example.mensajeria.data.api.ApiUserResponse
import com.example.mensajeria.data.api.RetrofitClient
import com.example.mensajeria.data.model.RegistrationForm
import com.example.mensajeria.data.repository.AuthRepository
import com.example.mensajeria.data.repository.ChatRepository
import com.example.mensajeria.ui.viewmodel.AuthViewModel
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.util.UUID

/** Entradas maliciosas, vacías o nulas: validadores y repositorios deben rechazarlas sin fallar. */
class SecurityInputTest {

    private val api = FakeApiService()

    private val injectionPayloads = listOf(
        "' OR '1'='1",
        "admin'--",
        "\"; DROP TABLE users; --",
        "<script>alert(1)</script>",
        "{\"\$gt\": \"\"}",
        "../../etc/passwd",
        "a@b.com\nBcc: x@y.com",
        "\u0000"
    )

    @Before fun setUp() { RetrofitClient.apiOverride = api }
    @After fun tearDown() { RetrofitClient.apiOverride = null }

    // ---------- Validadores ----------

    @Test
    fun emailValidator_rejectsInjectionPayloads() {
        injectionPayloads.forEach { assertNotNull("Debe rechazar: $it", AuthViewModel.validateEmail(it)) }
        assertNotNull(AuthViewModel.validateEmail("x' OR 1=1--@dominio.com"))
        assertNotNull(AuthViewModel.validateEmail("<script>@dominio.com"))
    }

    @Test
    fun loginField_rejectsInjectionLookingLikeEmail() {
        assertNotNull(AuthViewModel.validateUserOrEmail("' OR 1=1 --@x"))
        assertNotNull(AuthViewModel.validateUserOrEmail("<img src=x onerror=alert(1)>@"))
    }

    @Test
    fun passwordValidator_rejectsEmptyBlankAndShort() {
        listOf("", " ", "      ", "\t\n", "12345").forEach {
            assertNotNull("Debe rechazar: '$it'", AuthViewModel.validatePassword(it))
        }
    }

    @Test
    fun validators_handleNullFromJavaInteropAsEmpty() {
        // Un String nulo proveniente de Java/Gson se normaliza a "" antes de validar
        val fromJava: String? = null
        assertNotNull(AuthViewModel.validateEmail(fromJava.orEmpty()))
        assertNotNull(AuthViewModel.validatePassword(fromJava.orEmpty()))
        assertNotNull(AuthViewModel.validateUserOrEmail(fromJava.orEmpty()))
    }

    // ---------- Repositorio de autenticación ----------

    @Test
    fun offlineLogin_rejectsEmptyOrBlankPasswordForExistingUser() = runBlocking {
        val auth = AuthRepository()
        val name = "Segura ${UUID.randomUUID().toString().take(6)}"
        api.failure = IOException("sin red")
        auth.registerRemote(RegistrationForm(fullName = name, email = "s@s.pe", pin = "clave99"))
        auth.logout()

        assertTrue(auth.loginRemote(name, "").isFailure)
        assertTrue(auth.loginRemote(name, "   ").isFailure)
        assertTrue(auth.loginRemote(name, "incorrecta").isFailure)
        assertNull(auth.currentUser.value)
        assertTrue("Con la clave correcta sí entra", auth.loginRemote(name, "clave99").isSuccess)
    }

    @Test
    fun offlineLogin_rejectsInjectionCredentials() = runBlocking {
        val auth = AuthRepository()
        api.failure = IOException("sin red")
        injectionPayloads.forEach { payload ->
            assertTrue("Debe rechazar: $payload", auth.loginRemote(payload, payload).isFailure)
        }
        assertNull(auth.currentUser.value)
    }

    @Test
    fun serverRejection_withInjectionPayload_doesNotCreateSession() = runBlocking {
        val auth = AuthRepository()
        api.loginResponse = FakeApiService.error(401)

        val result = auth.loginRemote("' OR '1'='1", "' OR '1'='1")

        assertTrue(result.isFailure)
        assertNull(auth.currentUser.value)
        // El payload viaja como dato JSON literal, nunca concatenado en la URL/consulta
        assertEquals("' OR '1'='1", api.loginRequests.single().query)
    }

    @Test
    fun serverResponseWithNullId_isRejected() = runBlocking {
        val auth = AuthRepository()
        api.loginResponse = Response.success(ApiUserResponse(id = null, name = null))

        assertTrue(auth.loginRemote("nadie-${UUID.randomUUID()}", "clave99").isFailure)
        assertNull(auth.currentUser.value)
    }

    // ---------- Repositorio de chat ----------

    @Test
    fun addContact_rejectsEmptyOrBlankUsernameWithoutCallingServer() = runBlocking {
        val chat = ChatRepository()
        listOf("", "   ", "@", " @ ").forEach {
            assertTrue("Debe rechazar: '$it'", chat.addContactByUsername("u", it).isFailure)
        }
        assertEquals(0, api.calls)
    }

    @Test
    fun injectionInMessage_isSentAsLiteralEscapedJson() = runBlocking {
        val chat = ChatRepository()
        val userId = UUID.randomUUID().toString()
        val contactId = UUID.randomUUID().toString()
        val payload = "<script>alert('x')</script>\"; DROP TABLE messages; --"
        api.sendMessageResponse = Response.success(ApiMessageResponse("m1", userId, contactId, payload, "TEXT"))

        assertTrue(chat.sendMessageRemote(userId, contactId, payload).isSuccess)

        val sent = api.sentMessages.single()
        assertEquals(payload, sent.content)
        val json = Gson().toJson(sent)
        assertTrue("Las comillas deben ir escapadas en el JSON", json.contains("\\\"; DROP"))
        assertEquals(payload, Gson().fromJson(json, sent.javaClass).content)
    }

    @Test
    fun nullAndMalformedServerData_isDiscardedWithoutCrash() = runBlocking {
        val chat = ChatRepository()
        val userId = UUID.randomUUID().toString()
        val contactId = UUID.randomUUID().toString()
        chat.setSessionUser(userId)
        // Gson ignora la nulabilidad de Kotlin: simula listas con elementos y campos nulos
        val contactsJson = """[null, {"id": null, "name": "SinId"}, {"id": "", "name": "IdVacio"}, {"id": "ok", "name": "Valido"}]"""
        val contacts: List<ApiContactResponse> = Gson().fromJson(contactsJson, Array<ApiContactResponse>::class.java).toList()
        api.contactsResponse = Response.success(contacts)
        val messagesJson = """[null, {"id": null, "content": "x"}, {"id": "m1", "content": null}]"""
        api.messagesResponse = Response.success(Gson().fromJson(messagesJson, Array<ApiMessageResponse>::class.java).toList())

        assertEquals(listOf("Valido"), chat.syncContactsFromRemote().map { it.name })
        val msgs = chat.loadMessagesFromRemote(userId, contactId)
        assertEquals(listOf("m1"), msgs.map { it.id })
        assertEquals("", msgs.single().text)
    }
}
