package com.example.mensajeria

import com.example.mensajeria.domain.service.E2EEncryptionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class E2EEncryptionTest {

    private val userAlice = "11111111-1111-1111-1111-111111111111"
    private val userBob = "22222222-2222-2222-2222-222222222222"
    private val userEve = "33333333-3333-3333-3333-333333333333"

    @Test
    fun testKeyDerivationIsCanonicalAndBidirectional() {
        val keyAliceToBob = E2EEncryptionManager.deriveSharedKey(userAlice, userBob)
        val keyBobToAlice = E2EEncryptionManager.deriveSharedKey(userBob, userAlice)

        assertEquals("AES", keyAliceToBob.algorithm)
        assertEquals(32, keyAliceToBob.encoded.size) // 256 bits
        assertEquals(
            java.util.Base64.getEncoder().encodeToString(keyAliceToBob.encoded),
            java.util.Base64.getEncoder().encodeToString(keyBobToAlice.encoded)
        )
    }

    @Test
    fun testEncryptionProducesOpaquePayload() {
        val secretMessage = "Hola Romell, ¿a qué hora es la sustentación de tesis?"
        val encrypted = E2EEncryptionManager.encrypt(secretMessage, userAlice, userBob)

        assertTrue(encrypted.startsWith("E2E:v1:"))
        assertFalse(encrypted.contains("Romell"))
        assertFalse(encrypted.contains("sustentación"))
        assertTrue(E2EEncryptionManager.isEncrypted(encrypted))
    }

    @Test
    fun testEndToEndEncryptionAndDecryptionCycle() {
        val secretMessage = "Mensaje confidencial de prueba para accesibilidad sensorial 2026."
        val encrypted = E2EEncryptionManager.encrypt(secretMessage, userAlice, userBob)

        // El destinatario (Bob) descifra con su clave compartida
        val decrypted = E2EEncryptionManager.decrypt(encrypted, userBob, userAlice)
        assertEquals(secretMessage, decrypted)
    }

    @Test
    fun testThirdPartyCannotDecryptMessage() {
        val secretMessage = "Información privada entre Alice y Bob"
        val encrypted = E2EEncryptionManager.encrypt(secretMessage, userAlice, userBob)

        // Un tercero (Eve) intenta descifrar con una clave no correspondiente
        val decryptedByEve = E2EEncryptionManager.decrypt(encrypted, userEve, userBob)
        assertNotEquals(secretMessage, decryptedByEve)
    }

    @Test
    fun testRetrocompatibilityWithLegacyPlainTextMessages() {
        val legacyMessage = "Mensaje antiguo no cifrado en base de datos"
        val result = E2EEncryptionManager.decrypt(legacyMessage, userAlice, userBob)
        assertEquals(legacyMessage, result)
    }
}
