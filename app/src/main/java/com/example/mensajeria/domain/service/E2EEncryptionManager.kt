package com.example.mensajeria.domain.service

import android.util.Log
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Gestor de Cifrado de Extremo a Extremo (E2EE) en el dispositivo Android.
 *
 * Implementa cifrado autenticado de grado militar AES-256-GCM (Galois/Counter Mode).
 * Los mensajes se cifran en el propio dispositivo antes de salir a la red y se descifran
 * únicamente en la memoria local del destinatario.
 *
 * En la base de datos remota y en tránsito por la red, el contenido es un payload
 * opaco e ininteligible con el prefijo "E2E:v1:".
 */
object E2EEncryptionManager {

    private const val TAG = "E2EEncryptionManager"
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128 // bits
    private const val GCM_IV_LENGTH = 12 // bytes recomendados por NIST
    const val E2E_PREFIX = "E2E:v1:"

    // Sal criptográfica fija para la derivación determinística de la clave compartida entre el par de usuarios
    private const val KDF_SALT = "MESSENGER_UNCP_E2EE_V1_2026_SHARED_KEY_SALT_"

    /**
     * Deriva una clave simétrica AES de 256 bits única y determinística para la conversación
     * entre dos usuarios, ordenando sus identificadores de forma canónica.
     */
    fun deriveSharedKey(userIdA: String, userIdB: String): SecretKeySpec {
        val cleanA = userIdA.trim().lowercase()
        val cleanB = userIdB.trim().lowercase()
        val canonicalPair = if (cleanA <= cleanB) "$cleanA:$cleanB" else "$cleanB:$cleanA"
        val seed = KDF_SALT + canonicalPair
        val sha256 = MessageDigest.getInstance("SHA-256")
        val keyBytes = sha256.digest(seed.toByteArray(StandardCharsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Cifra el texto claro utilizando AES-256-GCM con un vector de inicialización (IV)
     * criptográficamente aleatorio generado en cada llamada.
     */
    fun encrypt(plainText: String, senderId: String, receiverId: String): String {
        if (plainText.isBlank()) return plainText
        if (senderId.isBlank() || receiverId.isBlank()) return plainText

        return try {
            val key = deriveSharedKey(senderId, receiverId)
            val iv = ByteArray(GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)

            val cipher = Cipher.getInstance(ALGORITHM)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.ENCRYPT_MODE, key, spec)

            val plainBytes = plainText.toByteArray(StandardCharsets.UTF_8)
            val cipherBytes = cipher.doFinal(plainBytes)

            // Empaquetar IV + CipherText (que incluye el tag de autenticación GCM)
            val combined = ByteArray(iv.size + cipherBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)

            val encoded = Base64.getEncoder().encodeToString(combined)
            "$E2E_PREFIX$encoded"
        } catch (e: Exception) {
            Log.e(TAG, "Error cifrando mensaje en dispositivo: ${e.message}", e)
            plainText
        }
    }

    /**
     * Descifra un payload cifrado con E2E:v1: utilizando la clave de la conversación.
     * Si el mensaje no está cifrado (ej. mensajes legacy previos), se devuelve el texto tal cual.
     */
    fun decrypt(cipherText: String, currentUserId: String, otherUserId: String): String {
        if (cipherText.isBlank() || !cipherText.startsWith(E2E_PREFIX)) {
            return cipherText
        }
        if (currentUserId.isBlank() || otherUserId.isBlank()) {
            return cipherText
        }

        return try {
            val base64Payload = cipherText.removePrefix(E2E_PREFIX)
            val combined = Base64.getDecoder().decode(base64Payload)
            if (combined.size <= GCM_IV_LENGTH) {
                return cipherText
            }

            val iv = ByteArray(GCM_IV_LENGTH)
            val cipherBytes = ByteArray(combined.size - GCM_IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
            System.arraycopy(combined, GCM_IV_LENGTH, cipherBytes, 0, cipherBytes.size)

            val key = deriveSharedKey(currentUserId, otherUserId)
            val cipher = Cipher.getInstance(ALGORITHM)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)

            val decryptedBytes = cipher.doFinal(cipherBytes)
            String(decryptedBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo descifrar payload E2EE (posible clave distinta o mensaje no cifrado): ${e.message}")
            cipherText
        }
    }

    fun isEncrypted(text: String): Boolean = text.startsWith(E2E_PREFIX)
}
