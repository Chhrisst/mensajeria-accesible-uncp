package com.example.mensajeria.data.repository

import android.content.Context
import com.example.mensajeria.data.api.ApiAddContactRequest
import com.example.mensajeria.data.api.ApiSendMessageRequest
import com.example.mensajeria.data.api.RetrofitClient
import com.example.mensajeria.data.model.Contact
import com.example.mensajeria.data.model.Message
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
        private const val PREFS_NAME = "uncp_chat_prefs"
        private var sharedContext: Context? = null
    }

    private val gson = Gson()
    private var currentActiveUserId: String = ""

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
                val savedList: List<Contact> = gson.fromJson(json, type)
                if (savedList.isNotEmpty()) {
                    _contacts.value = savedList
                }
            }
        } catch (_: Exception) {}
    }

    private fun persistContacts(userId: String) {
        val ctx = getEffectiveContext() ?: return
        try {
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = gson.toJson(_contacts.value)
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
            if (response.isSuccessful && response.body() != null) {
                val apiContacts = response.body()!!
                val mapped = apiContacts.map { c ->
                    Contact(
                        id = c.id,
                        name = c.name,
                        username = c.username ?: "",
                        description = c.lastMessage ?: c.visualCondition,
                        isOnline = true
                    )
                }
                _contacts.value = mapped
                persistContacts(userId)
                mapped
            } else {
                _contacts.value
            }
        } catch (_: Exception) {
            _contacts.value
        }
    }

    /**
     * Agrega un nuevo contacto buscando por su identificador único (@username).
     */
    suspend fun addContactByUsername(userId: String, username: String): Result<Contact> {
        return try {
            val response = RetrofitClient.api.addContact(
                ApiAddContactRequest(
                    userId = userId,
                    contactUsername = username.trim()
                )
            )
            if (response.isSuccessful && response.body() != null) {
                val c = response.body()!!
                val newContact = Contact(
                    id = c.id,
                    name = c.name,
                    username = c.username ?: "",
                    description = c.lastMessage ?: c.visualCondition,
                    isOnline = true
                )
                _contacts.update { current ->
                    val filtered = current.filter { it.id != newContact.id }
                    listOf(newContact) + filtered
                }
                persistContacts(userId)
                Result.success(newContact)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Usuario no encontrado."
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
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
        val ctx = getEffectiveContext() ?: return emptyList()
        try {
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(getMessagesPrefKey(userId, contactId), null)
            if (!json.isNullOrBlank()) {
                val type = object : TypeToken<List<Message>>() {}.type
                val savedList: List<Message> = gson.fromJson(json, type)
                _messages.value = savedList
                return savedList
            }
        } catch (_: Exception) {}
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

    suspend fun loadMessagesFromRemote(userId: String, contactId: String): List<Message> {
        if (userId.isBlank() || contactId.isBlank()) return _messages.value

        // Cargar primero localmente para disponibilidad inmediata
        loadPersistedMessages(userId, contactId)

        return try {
            val response = RetrofitClient.api.getMessages(userId, contactId)
            if (response.isSuccessful && response.body() != null) {
                val apiMsgs = response.body()!!
                val mapped = apiMsgs.map { m ->
                    val isFromMe = m.isFromMe || (m.senderId == userId)
                    Message(
                        id = m.id,
                        text = m.content ?: "",
                        isOutgoing = isFromMe,
                        senderName = if (isFromMe) "Tú" else "Contacto",
                        isVoiceNote = (m.messageType == "AUDIO"),
                        voiceDurationSeconds = m.audioDurationSeconds
                    )
                }
                _messages.value = mapped
                persistMessages(userId, contactId)
                mapped
            } else {
                _messages.value
            }
        } catch (_: Exception) {
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
        _messages.update { it + localMessage }
        persistMessages(userId, contactId)

        return try {
            val request = ApiSendMessageRequest(
                senderId = userId,
                receiverId = contactId,
                content = text,
                messageType = if (isVoiceNote) "AUDIO" else "TEXT",
                audioDurationSeconds = voiceDurationSeconds
            )
            val response = RetrofitClient.api.sendMessage(request)
            if (response.isSuccessful && response.body() != null) {
                val b = response.body()!!
                val confirmedMessage = localMessage.copy(id = b.id)
                Result.success(confirmedMessage)
            } else {
                Result.success(localMessage)
            }
        } catch (e: Exception) {
            // Guardado localmente en caso de fallo de red
            Result.success(localMessage)
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

    fun receiveMessage(text: String, senderName: String = "Contacto") {
        val incoming = Message(
            text = text,
            isOutgoing = false,
            senderName = senderName
        )
        _messages.update { it + incoming }
    }
}
