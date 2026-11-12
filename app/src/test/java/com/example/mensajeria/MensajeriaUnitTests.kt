package com.example.mensajeria

import com.example.mensajeria.data.repository.ChatRepository
import com.example.mensajeria.domain.service.SmartReplyService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pruebas unitarias de la Fase de Producción (Metodología Mobile-D).
 * Valida la lógica de negocio del repositorio y el generador de Smart Replies asistido por IA.
 */
class MensajeriaUnitTests {

    private lateinit var smartReplyService: SmartReplyService
    private lateinit var chatRepository: ChatRepository

    @Before
    fun setup() {
        smartReplyService = SmartReplyService()
        chatRepository = ChatRepository()
    }

    @Test
    fun testSmartReply_GeneratesThreeSuggestionsForConfirmation() {
        val incoming = "¿Confirmas tu asistencia para la sesión de hoy en la UNCP?"
        val replies = smartReplyService.generateReplies(incoming)

        assertEquals("Debe generar exactamente 3 sugerencias", 3, replies.size)
        assertTrue("Debe incluir sugerencia afirmativa", replies.any { it.contains("confirmo", ignoreCase = true) })
    }

    @Test
    fun testSmartReply_GeneratesThreeSuggestionsForGreeting() {
        val incoming = "Hola, buenos días a todos"
        val replies = smartReplyService.generateReplies(incoming)

        assertEquals(3, replies.size)
        assertTrue(replies.any { it.contains("Hola", ignoreCase = true) || it.contains("Buenos", ignoreCase = true) })
    }

    @Test
    fun testSmartReply_GeneratesThreeSuggestionsForLocation() {
        val incoming = "¿Dónde estás ahora? Ya inició la reunión"
        val replies = smartReplyService.generateReplies(incoming)

        assertEquals(3, replies.size)
        assertTrue(replies.any { it.contains("camino", ignoreCase = true) || it.contains("UNCP", ignoreCase = true) })
    }

    @Test
    fun testChatRepository_SendMessageUpdatesMessageList() {
        val initialCount = chatRepository.messages.value.size
        chatRepository.sendMessage("Mensaje de prueba de envío accesible")

        val updatedMessages = chatRepository.messages.value
        assertEquals(initialCount + 1, updatedMessages.size)

        val lastMessage = updatedMessages.last()
        assertEquals("Mensaje de prueba de envío accesible", lastMessage.text)
        assertTrue(lastMessage.isOutgoing)
        assertEquals("Tú", lastMessage.senderName)
    }

    @Test
    fun testChatRepository_ReceiveMessageUpdatesMessageList() {
        val initialCount = chatRepository.messages.value.size
        chatRepository.receiveMessage("Mensaje entrante de coordinación", "Coordinador")

        val updatedMessages = chatRepository.messages.value
        assertEquals(initialCount + 1, updatedMessages.size)

        val lastMessage = updatedMessages.last()
        assertEquals("Mensaje entrante de coordinación", lastMessage.text)
        assertTrue(!lastMessage.isOutgoing)
        assertEquals("Coordinador", lastMessage.senderName)
    }

    @Test
    fun testAuthRepository_RegisterNewUserFromForm() {
        val authRepo = com.example.mensajeria.data.repository.AuthRepository()
        val form = com.example.mensajeria.data.model.RegistrationForm(
            fullName = "Ana María Quispe",
            dni = "78912345",
            phone = "988776655",
            email = "ana@gmail.com",
            hasDisability = true,
            visualCondition = "Baja visión",
            pin = "456"
        )
        val registeredUser = authRepo.registerUser(form)
        assertEquals("Ana María Quispe", registeredUser.name)
        assertEquals("456", registeredUser.code)
        assertEquals("Con discapacidad", registeredUser.visualCondition)

        val searched = authRepo.findUserByQuery("Ana María")
        assertTrue("El usuario recién registrado debe poder ser encontrado", searched != null)
    }

    @Test
    fun testAuthRepository_LoginByVoiceInputNameOrCode() {
        val authRepo = com.example.mensajeria.data.repository.AuthRepository()
        val form = com.example.mensajeria.data.model.RegistrationForm(
            fullName = "Christian Beteta",
            dni = "12345678",
            phone = "999888777",
            email = "christian@uncp.edu.pe",
            hasDisability = false,
            pin = "123"
        )
        authRepo.registerUser(form)

        val userByName = authRepo.loginByInput("Quiero ingresar como Christian Beteta")
        assertTrue("Debe identificar usuario por dictado de nombre", userByName != null && userByName.name.contains("Christian"))

        val userByCode = authRepo.loginByInput("123")
        assertTrue("Debe identificar usuario por PIN", userByCode != null && userByCode.code == "123")
    }

    @Test
    fun testAuthRepository_VerifyPin() {
        val authRepo = com.example.mensajeria.data.repository.AuthRepository()
        val form = com.example.mensajeria.data.model.RegistrationForm(
            fullName = "Romell Rivera",
            dni = "87654321",
            phone = "987654321",
            email = "romell@uncp.edu.pe",
            hasDisability = true,
            pin = "123"
        )
        val romell = authRepo.registerUser(form)
        assertTrue("Debe encontrar la cuenta de Romell Rivera", romell != null)
        assertTrue("PIN correcto debe ser validado", authRepo.verifyPin(romell, "123"))
        assertTrue("PIN incorrecto debe ser rechazado", !authRepo.verifyPin(romell, "999"))
    }

    @Test
    fun testAuthRepository_LogoutClearsUser() {
        val authRepo = com.example.mensajeria.data.repository.AuthRepository()
        val form = com.example.mensajeria.data.model.RegistrationForm(
            fullName = "Usuario Prueba",
            dni = "11223344",
            phone = "911223344",
            email = "test@uncp.edu.pe",
            hasDisability = false,
            pin = "1234"
        )
        val user = authRepo.registerUser(form)
        authRepo.loginWithProfile(user)
        assertEquals(user, authRepo.currentUser.value)

        authRepo.logout()
        assertTrue(authRepo.currentUser.value == null)
    }
}


