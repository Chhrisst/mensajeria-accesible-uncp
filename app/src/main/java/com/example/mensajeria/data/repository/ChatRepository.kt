package com.example.mensajeria.data.repository

import android.content.Context
import android.util.Log
import com.example.mensajeria.data.api.ApiAddContactRequest
import com.example.mensajeria.data.api.ApiContactResponse
import com.example.mensajeria.data.api.ApiMessageResponse
import com.example.mensajeria.data.api.ApiSendMessageRequest
import com.example.mensajeria.data.api.RetrofitClient
import com.example.mensajeria.data.model.Contact
import com.example.mensajeria.data.model.Message
import com.example.mensajeria.domain.service.CallSignalParser
import com.example.mensajeria.domain.service.CallSignalType
import com.example.mensajeria.domain.service.DecryptedCallStreamItem
import com.example.mensajeria.domain.service.E2EEncryptionManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

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

    // Estado real de presencia por contacto (calculado estrictamente con actividad entrante !isFromMe)
    private val contactIncomingPresence = ConcurrentHashMap<String, Pair<Boolean, String>>()
    private val contactLastRealMessage = ConcurrentHashMap<String, String>()

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    @Volatile var onCallStreamMessagesLoaded: ((contactId: String, signals: List<DecryptedCallStreamItem>) -> Unit)? = null
    @Volatile var onCallSignalDetectedFromContact: ((Contact, String) -> Unit)? = null
    @Volatile var onNewIncomingMessageReceived: ((Contact, Message) -> Unit)? = null

    private val knownMessageIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val initialSyncedContacts = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val decryptedCache = ConcurrentHashMap<String, String>()

    init {
        if (context != null) {
            sharedContext = context.applicationContext
        }
    }

    private fun getEffectiveContext(): Context? = context ?: sharedContext

    fun setSessionUser(userId: String) {
        currentActiveUserId = userId
        knownMessageIds.clear()
        initialSyncedContacts.clear()
        currentConversationKey = ""
        loadPersistedContacts(userId)
        for (c in _contacts.value) {
            val local = readPersistedMessagesOnly(userId, c.id)
            for (m in local) {
                if (m.id.isNotBlank()) knownMessageIds.add(m.id)
            }
        }
        currentConversationKey = ""
    }

    fun readPersistedMessagesOnly(userId: String, contactId: String): List<Message> {
        val conversationKey = getMessagesPrefKey(userId, contactId)
        val ctx = getEffectiveContext() ?: return emptyList()
        try {
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(conversationKey, null)
            if (!json.isNullOrBlank()) {
                val type = object : TypeToken<List<Message>>() {}.type
                val savedList: List<Message?> = gson.fromJson(json, type) ?: emptyList()
                return savedList.mapNotNull { sanitizeMessage(it) }
            }
        } catch (_: Exception) {}
        return emptyList()
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
                    .map { c ->
                        val known = contactIncomingPresence[c.id]
                        if (known != null) {
                            c.copy(isOnline = known.first, lastSeenTime = known.second)
                        } else {
                            c.copy(isOnline = false)
                        }
                    }
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
        val rawTime = (raw.lastSeenTime as String?) ?: ""
        val unread = try { (raw.unreadCount as Number?)?.toInt()?.coerceAtLeast(0) ?: 0 } catch (_: Exception) { 0 }
        return Contact(
            id = id,
            name = name,
            username = username,
            description = (raw.description as String?) ?: "",
            isOnline = raw.isOnline,
            lastSeenTime = rawTime,
            unreadCount = unread
        )
    }

    private fun sanitizeMessage(raw: Message?): Message? {
        if (raw == null) return null
        val id = (raw.id as String?)?.takeIf { it.isNotBlank() } ?: return null
        val rawAudio = (raw.audioBase64 as String?) ?: ""
        val rawTime = (raw.formattedTime as String?) ?: ""
        return Message(
            id = id,
            text = (raw.text as String?) ?: "",
            isOutgoing = raw.isOutgoing,
            timestamp = raw.timestamp,
            senderName = (raw.senderName as String?) ?: if (raw.isOutgoing) "Tú" else "Contacto",
            isVoiceNote = raw.isVoiceNote,
            voiceDurationSeconds = raw.voiceDurationSeconds,
            audioBase64 = rawAudio,
            formattedTime = rawTime
        )
    }

    /** Mapea un contacto del backend; devuelve null si le faltan datos imprescindibles. */
    private fun mapApiContact(c: ApiContactResponse?): Contact? {
        if (c == null) return null
        val id = c.id?.takeIf { it.isNotBlank() } ?: return null
        val username = c.username ?: ""
        val name = c.name?.takeIf { it.isNotBlank() } ?: username.ifBlank { return null }
        val rawLastMsg = c.lastMessage ?: ""
        val rawDecrypted = if (rawLastMsg.isNotBlank()) {
            E2EEncryptionManager.decrypt(rawLastMsg, currentActiveUserId, id)
        } else {
            c.visualCondition ?: ""
        }
        val isCallSignal = CallSignalParser.isCallControlContent(rawDecrypted)
        val parsedSignal = if (isCallSignal) CallSignalParser.parse(rawDecrypted) else null
        if (!isCallSignal && rawLastMsg.isNotBlank() && rawDecrypted.isNotBlank()) {
            contactLastRealMessage[id] = rawDecrypted
        }
        val displayDesc = if (isCallSignal) {
            if (parsedSignal?.type == CallSignalType.PRESENCE || parsedSignal?.type == CallSignalType.OFFLINE) {
                contactLastRealMessage[id] ?: (c.visualCondition ?: "")
            } else {
                "📞 Llamada de voz"
            }
        } else {
            rawDecrypted
        }
        // Determinar estado de conexión estrictamente desde la actividad entrante del propio contacto (!isFromMe),
        // nunca desde c.lastMessageTime (que incluye mensajes enviados por el usuario actual).
        val knownPresence = contactIncomingPresence[id]
        val isOnlineNow = (c.isOnline == true) || (knownPresence?.first == true)
        val resolvedLastSeen = knownPresence?.second ?: ""
        val existingUnread = _contacts.value.firstOrNull { it.id == id }?.unreadCount ?: (c.unreadCount ?: 0)
        val contactObj = Contact(
            id = id,
            name = name,
            username = username,
            description = displayDesc,
            isOnline = isOnlineNow,
            lastSeenTime = resolvedLastSeen,
            unreadCount = existingUnread
        )
        if (isCallSignal && parsedSignal?.type == CallSignalType.INVITE) {
            onCallSignalDetectedFromContact?.invoke(contactObj, rawDecrypted)
        }
        return contactObj
    }

    /**
     * Convierte la marca temporal enviada por el servidor en UTC (ya sea en formato ISO 8601 o "HH:mm")
     * a la zona horaria local del dispositivo del usuario (ej: UTC-5 Perú).
     */
    fun formatServerTimeToLocal(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank() || trimmed.equals("Ahora", ignoreCase = true) || trimmed.equals("Hoy", ignoreCase = true)) {
            return trimmed
        }

        // 1. Si viene en formato ISO 8601 completo (ej: 2026-11-13T05:56:00Z o con offset de zona)
        try {
            if (trimmed.contains("T")) {
                val instant = java.time.Instant.parse(trimmed)
                val localTime = instant.atZone(java.time.ZoneId.systemDefault())
                return localTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
            }
        } catch (_: Exception) {}

        // 2. Si viene en formato HH:mm producido por el servidor (calculado en horario UTC)
        val timeParts = trimmed.split(":")
        if (timeParts.size == 2) {
            val h = timeParts[0].toIntOrNull()
            val m = timeParts[1].toIntOrNull()
            if (h != null && m != null && h in 0..23 && m in 0..59) {
                return try {
                    val utcCal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                    utcCal.set(java.util.Calendar.HOUR_OF_DAY, h)
                    utcCal.set(java.util.Calendar.MINUTE, m)
                    utcCal.set(java.util.Calendar.SECOND, 0)
                    utcCal.set(java.util.Calendar.MILLISECOND, 0)

                    val localFmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    localFmt.timeZone = java.util.TimeZone.getDefault()
                    localFmt.format(utcCal.time)
                } catch (_: Exception) {
                    trimmed
                }
            }
        }
        return trimmed
    }

    private fun isTimeWithinMinutes(hhMm: String, maxMinutes: Int): Boolean {
        if (!Regex("^\\d{1,2}:\\d{2}$").matches(hhMm.trim())) return false
        return try {
            val parts = hhMm.trim().split(":")
            val msgHour = parts[0].toInt()
            val msgMin = parts[1].toInt()
            val now = java.util.Calendar.getInstance()
            val nowTotal = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
            val msgTotal = msgHour * 60 + msgMin
            kotlin.math.abs(nowTotal - msgTotal) <= maxMinutes
        } catch (_: Exception) {
            false
        }
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
                val contactWithUsername = if (newContact.username.isBlank()) {
                    newContact.copy(username = clean)
                } else {
                    newContact
                }
                _contacts.update { current ->
                    val filtered = current.filter { it.id != contactWithUsername.id && !it.id.startsWith("c_") }
                    listOf(contactWithUsername) + filtered
                }
                persistContacts(userId)
                Result.success(contactWithUsername)
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
            username = name.trim().lowercase().replace(" ", ""),
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

    fun clearUnreadCount(contactId: String) {
        if (contactId.isBlank()) return
        _contacts.update { list ->
            list.map { c ->
                if (c.id == contactId && c.unreadCount > 0) c.copy(unreadCount = 0) else c
            }
        }
        if (currentActiveUserId.isNotBlank()) {
            persistContacts(currentActiveUserId)
        }
    }

    fun clearActiveConversation() {
        currentConversationKey = ""
    }

    fun loadPersistedMessages(userId: String, contactId: String): List<Message> {
        val conversationKey = getMessagesPrefKey(userId, contactId)
        val isNewConversation = conversationKey != currentConversationKey
        currentConversationKey = conversationKey
        clearUnreadCount(contactId)
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
                val parsed = parseApiMessages(userId, contactId, apiMsgs)
                if (parsed.callSignals.isNotEmpty()) {
                    onCallStreamMessagesLoaded?.invoke(contactId, parsed.callSignals)
                }
                updateContactPresenceFromApiMessages(contactId, parsed)
                val serverIds = parsed.messages.mapTo(HashSet()) { it.id }
                // Conservar los mensajes locales aún no confirmados por el servidor
                val pendingLocal = _messages.value.filter { it.id in unsyncedMessageIds && it.id !in serverIds }
                val merged = parsed.messages + pendingLocal
                _messages.value = merged
                persistMessages(userId, contactId)

                // Alertar si entraron mensajes nuevos mientras se está en la conversación activa
                processIncomingMessages(userId, contactId, parsed.messages)

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

    private data class ParsedMessagesResult(
        val messages: List<Message>,
        val callSignals: List<DecryptedCallStreamItem>,
        val lastIncomingHhMm: String,
        val lastIncomingWasOffline: Boolean
    )

    private fun parseApiMessages(
        userId: String,
        contactId: String,
        apiMsgs: List<ApiMessageResponse?>
    ): ParsedMessagesResult {
        val callStreamItems = mutableListOf<DecryptedCallStreamItem>()
        val timeFmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        var lastIncomingHhMm = ""
        var lastIncomingWasOffline = false

        val mapped = apiMsgs.mapNotNull { m ->
            val id = m?.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val isFromMe = m.isFromMe || (m.senderId == userId)
            val clearContent = decryptedCache.getOrPut("${id}_c") {
                E2EEncryptionManager.decrypt(m.content ?: "", userId, contactId)
            }
            val clearAudio = if (!m.audioUrl.isNullOrBlank()) {
                decryptedCache.getOrPut("${id}_a") {
                    E2EEncryptionManager.decrypt(m.audioUrl, userId, contactId)
                }
            } else ""
            val localMsgTime = formatServerTimeToLocal(m.createdAt ?: "")

            if (m.messageType == "CALL_STREAM") {
                val item = DecryptedCallStreamItem(
                    id = id,
                    senderId = m.senderId ?: "",
                    receiverId = m.receiverId ?: "",
                    isFromMe = isFromMe,
                    content = clearContent,
                    audioBase64 = clearAudio,
                    createdAt = m.createdAt ?: ""
                )
                callStreamItems.add(item)
                if (!isFromMe) {
                    val sig = CallSignalParser.parse(clearContent)
                    lastIncomingWasOffline = (sig?.type == CallSignalType.OFFLINE)
                    val sigTime = if (sig != null && sig.timestampMs > 0L) {
                        timeFmt.format(java.util.Date(sig.timestampMs))
                    } else {
                        localMsgTime
                    }
                    if (sigTime.isNotBlank()) lastIncomingHhMm = sigTime
                }
                return@mapNotNull null
            }

            if (!isFromMe) {
                lastIncomingWasOffline = false
                if (localMsgTime.isNotBlank()) lastIncomingHhMm = localMsgTime
            }

            Message(
                id = id,
                text = clearContent,
                isOutgoing = isFromMe,
                senderName = if (isFromMe) "Tú" else "Contacto",
                isVoiceNote = (m.messageType == "AUDIO"),
                voiceDurationSeconds = m.audioDurationSeconds,
                audioBase64 = clearAudio,
                formattedTime = localMsgTime
            )
        }
        return ParsedMessagesResult(
            messages = mapped,
            callSignals = callStreamItems,
            lastIncomingHhMm = lastIncomingHhMm,
            lastIncomingWasOffline = lastIncomingWasOffline
        )
    }

    private fun updateContactPresenceFromApiMessages(
        contactId: String,
        parsed: ParsedMessagesResult
    ) {
        val lastReal = parsed.messages.lastOrNull()?.text?.takeIf { it.isNotBlank() }
        if (lastReal != null) {
            contactLastRealMessage[contactId] = lastReal
        }

        val (signalOnline, _) = CallSignalParser.computeContactPresence(parsed.callSignals)
        val lastIncomingMsg = parsed.messages.lastOrNull { !it.isOutgoing }
        val isOnlineNow = if (parsed.lastIncomingWasOffline) {
            false
        } else {
            signalOnline || (lastIncomingMsg != null && isTimeWithinMinutes(lastIncomingMsg.formattedTime, 3))
        }
        val lastSeenHhMm = parsed.lastIncomingHhMm
        contactIncomingPresence[contactId] = isOnlineNow to lastSeenHhMm

        _contacts.update { list ->
            list.map { c ->
                if (c.id == contactId) {
                    c.copy(
                        isOnline = isOnlineNow,
                        lastSeenTime = lastSeenHhMm,
                        description = lastReal ?: c.description
                    )
                } else c
            }
        }
        if (currentActiveUserId.isNotBlank()) {
            persistContacts(currentActiveUserId)
        }
    }

    /**
     * Consulta los paquetes CALL_STREAM de un contacto sin alterar la conversación activa en _messages,
     * aprovechando la respuesta para actualizar el estado real de presencia y última conexión.
     */
    suspend fun fetchCallStreamSignals(userId: String, contactId: String): List<DecryptedCallStreamItem> {
        if (userId.isBlank() || contactId.isBlank()) return emptyList()
        return try {
            val response = RetrofitClient.api.getMessages(userId, contactId)
            val apiMsgs = response.body() ?: return emptyList()
            if (!response.isSuccessful) return emptyList()
            // Optimización de latencia: para señales y audio en tiempo real, procesar los últimos 20 elementos
            val msgsToProcess = if (apiMsgs.size > 20) apiMsgs.takeLast(20) else apiMsgs
            val parsed = parseApiMessages(userId, contactId, msgsToProcess)
            updateContactPresenceFromApiMessages(contactId, parsed)

            // Procesar y alertar sobre nuevos mensajes entrantes de este contacto
            processIncomingMessages(userId, contactId, parsed.messages)

            parsed.callSignals
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun processIncomingMessages(userId: String, contactId: String, remoteMessages: List<Message>) {
        if (remoteMessages.isEmpty()) return
        val contact = _contacts.value.firstOrNull { it.id == contactId } ?: Contact(id = contactId, name = "Contacto")
        val isFirstSync = initialSyncedContacts.add(contactId)

        if (isFirstSync) {
            // Primer sincronizado de este contacto en esta sesión: sembrar IDs existentes sin alertar
            for (m in remoteMessages) {
                if (m.id.isNotBlank()) knownMessageIds.add(m.id)
            }
            return
        }

        // Buscar mensajes entrantes (!isOutgoing) no procesados
        val newIncoming = remoteMessages.filter { !it.isOutgoing && it.id.isNotBlank() && knownMessageIds.add(it.id) }
        if (newIncoming.isNotEmpty()) {
            val conversationKey = getMessagesPrefKey(userId, contactId)
            val isCurrentConversation = (conversationKey == currentConversationKey && currentConversationKey.isNotBlank())
            if (isCurrentConversation) {
                val currentIds = _messages.value.mapTo(HashSet()) { it.id }
                val toAdd = newIncoming.filter { it.id !in currentIds }
                if (toAdd.isNotEmpty()) {
                    _messages.update { it + toAdd }
                }
            } else {
                _contacts.update { list ->
                    list.map { c ->
                        if (c.id == contactId) {
                            c.copy(unreadCount = c.unreadCount + newIncoming.size)
                        } else c
                    }
                }
                persistContacts(userId)
            }
            persistMessages(userId, contactId)

            for (msg in newIncoming) {
                onNewIncomingMessageReceived?.invoke(contact, msg)
            }
        }
    }

    suspend fun sendMessageRemote(
        userId: String,
        contactId: String,
        text: String,
        isVoiceNote: Boolean = false,
        voiceDurationSeconds: Int = 0,
        audioBase64: String = "",
        messageTypeOverride: String? = null
    ): Result<Message> {
        val nowTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        val localMessage = Message(
            text = text,
            isOutgoing = true,
            senderName = "Tú",
            isVoiceNote = isVoiceNote,
            voiceDurationSeconds = voiceDurationSeconds,
            audioBase64 = audioBase64,
            formattedTime = nowTime
        )
        // Añadir a la UI de inmediato (optimistic update)
        val isInternalCallStream = messageTypeOverride == "CALL_STREAM"
        if (!isInternalCallStream) {
            unsyncedMessageIds.add(localMessage.id)
            if (getMessagesPrefKey(userId, contactId) == currentConversationKey) {
                _messages.update { it + localMessage }
                persistMessages(userId, contactId)
            }
        }

        return try {
            val encryptedPayload = E2EEncryptionManager.encrypt(text, userId, contactId)
            val encryptedAudio = if (audioBase64.isNotBlank()) {
                E2EEncryptionManager.encrypt(audioBase64, userId, contactId)
            } else null
            val request = ApiSendMessageRequest(
                senderId = userId,
                receiverId = contactId,
                content = encryptedPayload,
                messageType = messageTypeOverride ?: if (isVoiceNote) "AUDIO" else "TEXT",
                audioUrl = encryptedAudio,
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

    fun sendMessage(
        text: String,
        isVoiceNote: Boolean = false,
        voiceDurationSeconds: Int = 0,
        audioBase64: String = ""
    ) {
        val nowTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        val newMessage = Message(
            text = text,
            isOutgoing = true,
            senderName = "Tú",
            isVoiceNote = isVoiceNote,
            voiceDurationSeconds = voiceDurationSeconds,
            audioBase64 = audioBase64,
            formattedTime = nowTime
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
