package com.example.mensajeria.data.repository

import android.content.Context
import android.util.Log
import com.example.mensajeria.data.api.ApiAddContactRequest
import com.example.mensajeria.data.api.ApiContactResponse
import com.example.mensajeria.data.api.ApiSendMessageRequest
import com.example.mensajeria.data.api.RetrofitClient
import com.example.mensajeria.data.model.Contact
import com.example.mensajeria.data.model.Message
import com.example.mensajeria.domain.service.E2EEncryptionManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Repositorio de mensajería para gestionar conversaciones y contactos.
 * Garantiza la Tríada CIA (Confidencialidad, Integridad y Disponibilidad):
 * - Los contactos pertenecen estrictamente a cada usuario logueado.
 * - Los mensajes se transmiten y consultan de forma aislada e íntegra.
 * - Sincroniza en tiempo real con el backend PostgreSQL en la nube de la UNCP.
 */
class ChatRepository(private val context: Context? = null) {

    companion object {
        private const val TAG = "ChatRepository"
        private const val PREFS_NAME = "uncp_chat_prefs"
        private var sharedContext: Context? = null
    }

    private val gson = Gson()
    private var currentActiveUserId: String = ""

    // Conversación cuyos mensajes están actualmente en _messages (userId_contactId)
    @Volatile private var currentConversationKey: String = ""

    // Mensajes enviados desde este dispositivo que el servidor aún no confirmó (en vuelo o fallidos).
    // Se conservan al refrescar desde el servidor para que no "desaparezcan" de la conversación.
    private val unsyncedMessageIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    init {
        if (context != null) {
            sharedContext = context.applicationContext
        }
    }

    private fun getEffectiveContext(): Context? = context ?: sharedContext

    fun setSessionUser(userId: String) {
        currentActiveUserId = userId
        loadPersistedContacts(userId)
    }

    private fun getContactsPrefKey(userId: String): String {
        return if (userId.isNotBlank()) "contacts_$userId" else "contacts_default"
    }

    private fun getMessagesPrefKey(userId: String, contactId: String): String {
        return "messages_${userId}_${contactId}"
    }

    fun loadPersistedContacts(userId: String) {
        val ctx = getEffectiveContext() ?: return
        try {
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(getContactsPrefKey(userId), null)
            if (!json.isNullOrBlank()) {
                val type = object : TypeToken<List<Contact>>() {}.type
                val savedList: List<Contact?> = gson.fromJson(json, type) ?: emptyList()
                // Sanitizar: Eliminar contactos temporales o ficticios ("c_...") y registros corruptos.
                // Gson no respeta la nulabilidad de Kotlin: un JSON antiguo o dañado puede traer campos null
                // que luego provocarían NullPointerException en la UI o en el asistente de voz.
                val validContacts = savedList.mapNotNull { sanitizeContact(it) }
                    .filter { !it.id.startsWith("c_") }
                _contacts.value = validContacts
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudieron leer los contactos guardados: ${e.message}")
        }
    }

    private fun sanitizeContact(raw: Contact?): Contact? {
        if (raw == null) return null
        val id = (raw.id as String?)?.takeIf { it.isNotBlank() } ?: return null
        val username = (raw.username as String?) ?: ""
        val name = (raw.name as String?)?.takeIf { it.isNotBlank() } ?: username.ifBlank { return null }
        return Contact(
            id = id,
            name = name,
            username = username,
            description = (raw.description as String?) ?: "",
            isOnline = raw.isOnline
        )
    }

    private fun sanitizeMessage(raw: Message?): Message? {
        if (raw == null) return null
        val id = (raw.id as String?)?.takeIf { it.isNotBlank() } ?: return null
        return Message(
            id = id,
            text = (raw.text as String?) ?: "",
            isOutgoing = raw.isOutgoing,
            timestamp = raw.timestamp,
            senderName = (raw.senderName as String?) ?: if (raw.isOutgoing) "Tú" else "Contacto",
            isVoiceNote = raw.isVoiceNote,
            voiceDurationSeconds = raw.voiceDurationSeconds
        )
    }

    /** Mapea un contacto del backend; devuelve null si le faltan datos imprescindibles. */
    private fun mapApiContact(c: ApiContactResponse?): Contact? {
        if (c == null) return null
        val id = c.id?.takeIf { it.isNotBlank() } ?: return null
        val username = c.username ?: ""
        val name = c.name?.takeIf { it.isNotBlank() } ?: username.ifBlank { return null }
        val rawLastMsg = c.lastMessage ?: ""
        val decryptedDesc = if (rawLastMsg.isNotBlank()) {
            E2EEncryptionManager.decrypt(rawLastMsg, currentActiveUserId, id)
        } else {
            c.visualCondition ?: ""
        }
        return Contact(
            id = id,
            name = name,
            username = username,
            description = decryptedDesc,
            isOnline = c.isOnline ?: false
        )
    }

    private fun persistContacts(userId: String) {
        val ctx = getEffectiveContext() ?: return
        try {
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val validContacts = _contacts.value.filter { !it.id.startsWith("c_") && it.id.isNotBlank() }
            val json = gson.toJson(validContacts)
            prefs.edit().putString(getContactsPrefKey(userId), json).apply()
        } catch (_: Exception) {}
    }

    /**
     * Sincroniza los contactos autorizados del usuario desde la base de datos remota.
     * Tríada CIA (Confidencialidad): Solo se devuelven los contactos pertenecientes a este user_id.
     */
    suspend fun syncContactsFromRemote(userIdOrName: String = ""): List<Contact> {
        val userId = if (userIdOrName.length > 20) userIdOrName else currentActiveUserId
        if (userId.isBlank()) {
            return _contacts.value
        }

        return try {
            val response = RetrofitClient.api.getContacts(userId)
            val apiContacts = response.body()
            if (response.isSuccessful && apiContacts != null) {
                val mapped = apiContacts.mapNotNull { mapApiContact(it) }
                _contacts.value = mapped
                persistContacts(userId)
                mapped
            } else {
                Log.w(TAG, "getContacts respondió HTTP ${response.code()}")
                _contacts.value
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Sin conexión al sincronizar contactos: ${e.message}")
            _contacts.value
        }
    }

    /**
     * Agrega un nuevo contacto buscando por su identificador único (@username).
     */
    suspend fun addContactByUsername(userId: String, username: String): Result<Contact> {
        val clean = username.trim().removePrefix("@")
        if (clean.isBlank()) {
            return Result.failure(Exception("Debe ingresar un nombre de usuario válido."))
        }
        return try {
            val response = RetrofitClient.api.addContact(
                ApiAddContactRequest(
                    userId = userId,
                    contactUsername = clean
                )
            )
            val newContact = if (response.isSuccessful) mapApiContact(response.body()) else null
            if (newContact != null) {
                _contacts.update { current ->
                    val filtered = current.filter { it.id != newContact.id && !it.id.startsWith("c_") }
                    listOf(newContact) + filtered
                }
                persistContacts(userId)
                Result.success(newContact)
            } else {
                val rawError = response.errorBody()?.string() ?: ""
                val cleanError = try {
                    val jsonObj = org.json.JSONObject(rawError)
                    jsonObj.optString("detail", rawError)
                } catch (_: Exception) {
                    rawError
                }
                val finalMessage = if (response.code() == 404 || cleanError.contains("No se encontró", ignoreCase = true) || cleanError.contains("no existe", ignoreCase = true)) {
                    "El usuario @$clean no existe en la aplicación de mensajería."
                } else if (cleanError.isNotBlank()) {
                    cleanError
                } else {
                    "No se pudo agregar al usuario @$clean."
                }
                Result.failure(Exception(finalMessage))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception("Sin conexión con el servidor. No se pudo agregar al usuario @$clean."))
        }
    }

    fun addContact(name: String, phone: String = "", description: String = ""): Contact {
        val newContact = Contact(
            id = "c_${System.currentTimeMillis()}",
            name = name.trim(),
            username = name.trim().lowercase().replace(" ", "_"),
            description = description.ifBlank { if (phone.isNotBlank()) "Tel: $phone" else "Contacto UNCP" },
            isOnline = true
        )
        _contacts.update { current ->
            val filtered = current.filter { !it.name.equals(newContact.name, ignoreCase = true) }
            listOf(newContact) + filtered
        }
        if (currentActiveUserId.isNotBlank()) {
            persistContacts(currentActiveUserId)
        }
        return newContact
    }

    fun deleteContact(nameOrQuery: String): Contact? {
        val cleanQuery = nameOrQuery.trim().lowercase()
        val current = _contacts.value
        val toRemove = current.firstOrNull {
            it.name.lowercase().contains(cleanQuery) ||
            cleanQuery.contains(it.name.lowercase()) ||
            it.username.lowercase() == cleanQuery.removePrefix("@")
        }
        if (toRemove != null) {
            _contacts.update { list -> list.filter { it.id != toRemove.id } }
            if (currentActiveUserId.isNotBlank()) {
                persistContacts(currentActiveUserId)
            }
            return toRemove
        }
        return null
    }

    fun updateContact(oldNameOrQuery: String, newName: String, newDescription: String = ""): Contact? {
        val cleanQuery = oldNameOrQuery.trim().lowercase()
        val current = _contacts.value
        val index = current.indexOfFirst {
            it.name.lowercase().contains(cleanQuery) || cleanQuery.contains(it.name.lowercase())
        }
        if (index != -1) {
            val old = current[index]
            val updated = old.copy(
                name = newName.ifBlank { old.name }.trim(),
                description = newDescription.ifBlank { old.description }.trim()
            )
            val updatedList = current.toMutableList()
            updatedList[index] = updated
            _contacts.value = updatedList
            if (currentActiveUserId.isNotBlank()) {
                persistContacts(currentActiveUserId)
            }
            return updated
        }
        return null
    }

    // ==========================================
    // GESTIÓN DE MENSAJES END-TO-END Y BASE DE DATOS
    // ==========================================

    fun loadPersistedMessages(userId: String, contactId: String): List<Message> {
        val conversationKey = getMessagesPrefKey(userId, contactId)
        val isNewConversation = conversationKey != currentConversationKey
        currentConversationKey = conversationKey
        // Al cambiar de conversación se limpia la lista: antes, si el nuevo contacto no tenía caché,
        // se seguían mostrando (y leyendo en voz alta) los mensajes del contacto anterior.
        if (isNewConversation) {
            _messages.value = emptyList()
        }
        val ctx = getEffectiveContext() ?: return emptyList()
        try {
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(conversationKey, null)
            if (!json.isNullOrBlank()) {
                val type = object : TypeToken<List<Message>>() {}.type
                val savedList: List<Message?> = gson.fromJson(json, type) ?: emptyList()
                val clean = savedList.mapNotNull { sanitizeMessage(it) }
                _messages.value = clean
                return clean
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudieron leer los mensajes guardados: ${e.message}")
        }
        return emptyList()
    }

    private fun persistMessages(userId: String, contactId: String) {
        val ctx = getEffectiveContext() ?: return
        try {
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = gson.toJson(_messages.value)
            prefs.edit().putString(getMessagesPrefKey(userId, contactId), json).apply()
        } catch (_: Exception) {}
    }

    /**
     * @param loadLocalFirst true al abrir la conversación (muestra la caché al instante);
     *        false en el sondeo periódico, para no releer disco cada 4 s ni provocar parpadeos.
     */
    suspend fun loadMessagesFromRemote(
        userId: String,
        contactId: String,
        loadLocalFirst: Boolean = true
    ): List<Message> {
        if (userId.isBlank() || contactId.isBlank()) return _messages.value

        val conversationKey = getMessagesPrefKey(userId, contactId)
        if (loadLocalFirst || conversationKey != currentConversationKey) {
            // Cargar primero localmente para disponibilidad inmediata
            loadPersistedMessages(userId, contactId)
        }

        return try {
            val response = RetrofitClient.api.getMessages(userId, contactId)
            val apiMsgs = response.body()
            // Si mientras esperábamos la red el usuario abrió otra conversación, descartar esta respuesta
            if (conversationKey != currentConversationKey) return _messages.value
            if (response.isSuccessful && apiMsgs != null) {
                val mapped = apiMsgs.mapNotNull { m ->
                    val id = m?.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val isFromMe = m.isFromMe || (m.senderId == userId)
                    val clearContent = E2EEncryptionManager.decrypt(m.content ?: "", userId, contactId)
                    Message(
                        id = id,
                        text = clearContent,
                        isOutgoing = isFromMe,
                        senderName = if (isFromMe) "Tú" else "Contacto",
                        isVoiceNote = (m.messageType == "AUDIO"),
                        voiceDurationSeconds = m.audioDurationSeconds
                    )
                }
                val serverIds = mapped.mapTo(HashSet()) { it.id }
                // Conservar los mensajes locales aún no confirmados por el servidor
                val pendingLocal = _messages.value.filter { it.id in unsyncedMessageIds && it.id !in serverIds }
                val merged = mapped + pendingLocal
                _messages.value = merged
                persistMessages(userId, contactId)
                merged
            } else {
                Log.w(TAG, "getMessages respondió HTTP ${response.code()}")
                _messages.value
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Sin conexión al cargar mensajes: ${e.message}")
            _messages.value
        }
    }

    suspend fun sendMessageRemote(
        userId: String,
        contactId: String,
        text: String,
        isVoiceNote: Boolean = false,
        voiceDurationSeconds: Int = 0
    ): Result<Message> {
        val localMessage = Message(
            text = text,
            isOutgoing = true,
            senderName = "Tú",
            isVoiceNote = isVoiceNote,
            voiceDurationSeconds = voiceDurationSeconds
        )
        // Añadir a la UI de inmediato (optimistic update)
        unsyncedMessageIds.add(localMessage.id)
        if (getMessagesPrefKey(userId, contactId) == currentConversationKey) {
            _messages.update { it + localMessage }
            persistMessages(userId, contactId)
        }

        return try {
            val encryptedPayload = E2EEncryptionManager.encrypt(text, userId, contactId)
            val request = ApiSendMessageRequest(
                senderId = userId,
                receiverId = contactId,
                content = encryptedPayload,
                messageType = if (isVoiceNote) "AUDIO" else "TEXT",
                audioDurationSeconds = voiceDurationSeconds
            )
            val response = RetrofitClient.api.sendMessage(request)
            if (response.isSuccessful) {
                val serverId = response.body()?.id?.takeIf { it.isNotBlank() }
                val confirmedMessage = if (serverId != null) localMessage.copy(id = serverId) else localMessage
                // Sustituir el id local por el del servidor para que el siguiente refresco no lo duplique
                if (serverId != null) {
                    _messages.update { list -> list.map { if (it.id == localMessage.id) confirmedMessage else it } }
                    if (getMessagesPrefKey(userId, contactId) == currentConversationKey) {
                        persistMessages(userId, contactId)
                    }
                }
                unsyncedMessageIds.remove(localMessage.id)
                Result.success(confirmedMessage)
            } else {
                // Antes se devolvía success aunque el servidor rechazara el mensaje, y la app anunciaba
                // "Mensaje enviado" a un usuario que no puede verificarlo visualmente.
                Log.w(TAG, "sendMessage respondió HTTP ${response.code()}")
                Result.failure(Exception("El servidor no aceptó el mensaje (HTTP ${response.code()})."))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Queda guardado localmente (y visible) en caso de fallo de red
            Log.w(TAG, "Sin conexión al enviar mensaje: ${e.message}")
            Result.failure(Exception("Sin conexión con el servidor. El mensaje quedó guardado en el teléfono."))
        }
    }

    fun sendMessage(text: String, isVoiceNote: Boolean = false, voiceDurationSeconds: Int = 0) {
        val newMessage = Message(
            text = text,
            isOutgoing = true,
            senderName = "Tú",
            isVoiceNote = isVoiceNote,
            voiceDurationSeconds = voiceDurationSeconds
        )
        _messages.update { it + newMessage }
    }

    fun receiveMessage(text: String, senderName: String = "Remitente") {
        val newMessage = Message(
            text = text,
            isOutgoing = false,
            senderName = senderName
        )
        _messages.update { it + newMessage }
    }
}
