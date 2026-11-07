package com.example.mensajeria.data.repository

import android.content.Context
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
 * Soporta persistencia permanente en SharedPreferences y gestión dinámica por IA.
 */
class ChatRepository(private val context: Context? = null) {

    companion object {
        private const val PREFS_NAME = "uncp_chat_prefs"
        private const val KEY_CONTACTS = "contacts_list"
        private var sharedContext: Context? = null

        private val defaultContacts = listOf(
            Contact(
                id = "22222222-2222-2222-2222-222222222222",
                name = "Romell Rivera",
                description = "Con discapacidad",
                isOnline = true
            ),
            Contact(
                id = "33333333-3333-3333-3333-333333333333",
                name = "Miguel Retuerto",
                description = "Con discapacidad",
                isOnline = true
            ),
            Contact(
                id = "11111111-1111-1111-1111-111111111111",
                name = "Christian Beteta",
                description = "Con discapacidad",
                isOnline = true
            ),
            Contact(
                id = "44444444-4444-4444-4444-444444444444",
                name = "Juan Perez García",
                description = "Con discapacidad",
                isOnline = true
            )
        )

        private val _contacts = MutableStateFlow<List<Contact>>(defaultContacts)
    }

    private val gson = Gson()

    init {
        if (context != null) {
            sharedContext = context.applicationContext
        }
        loadPersistedContacts()
    }

    private fun getEffectiveContext(): Context? = context ?: sharedContext

    private fun loadPersistedContacts() {
        val ctx = getEffectiveContext() ?: return
        try {
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_CONTACTS, null)
            if (!json.isNullOrBlank()) {
                val type = object : TypeToken<List<Contact>>() {}.type
                val savedList: List<Contact> = gson.fromJson(json, type)
                
                // Si contiene descripciones antiguas no estandarizadas, reiniciar a la lista institucional
                val hasOldMockups = savedList.any {
                    it.description.contains("Coordinadora", ignoreCase = true) ||
                    it.description.contains("cómputo", ignoreCase = true) ||
                    it.description.contains("Mesa de ayuda", ignoreCase = true) ||
                    (it.description != "Con discapacidad" && it.description != "Sin discapacidad")
                }

                if (!hasOldMockups && savedList.isNotEmpty()) {
                    _contacts.value = savedList
                } else {
                    _contacts.value = defaultContacts
                    persistContacts()
                }
            }
        } catch (_: Exception) {}
    }

    private fun persistContacts() {
        val ctx = getEffectiveContext() ?: return
        try {
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = gson.toJson(_contacts.value)
            prefs.edit().putString(KEY_CONTACTS, json).apply()
        } catch (_: Exception) {}
    }

    suspend fun syncContactsFromRemote(currentUserName: String = ""): List<Contact> {
        return try {
            val response = com.example.mensajeria.data.api.RetrofitClient.api.getUsers()
            if (response.isSuccessful && response.body() != null) {
                val apiUsers = response.body()!!
                val cleanCurrent = currentUserName.trim().lowercase()
                val mapped = apiUsers
                    .filter { u ->
                        val uName = u.name.trim().lowercase()
                        cleanCurrent.isBlank() || (uName != cleanCurrent && !cleanCurrent.contains(uName) && !uName.contains(cleanCurrent))
                    }
                    .map { u ->
                        val desc = if (u.hasDisability) "Con discapacidad" else "Sin discapacidad"
                        Contact(
                            id = u.id,
                            name = u.name,
                            description = desc,
                            isOnline = true
                        )
                    }
                if (mapped.isNotEmpty()) {
                    _contacts.value = mapped
                    persistContacts()
                    mapped
                } else {
                    _contacts.value
                }
            } else {
                _contacts.value
            }
        } catch (_: Exception) {
            _contacts.value
        }
    }

    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    fun addContact(name: String, phone: String = "", description: String = ""): Contact {
        val newContact = Contact(
            id = "c_${System.currentTimeMillis()}",
            name = name.trim(),
            description = description.ifBlank { if (phone.isNotBlank()) "Tel: $phone" else "Contacto UNCP" },
            isOnline = true
        )
        _contacts.update { current ->
            val filtered = current.filter { !it.name.equals(newContact.name, ignoreCase = true) }
            listOf(newContact) + filtered
        }
        persistContacts()
        return newContact
    }

    fun deleteContact(nameOrQuery: String): Contact? {
        val cleanQuery = nameOrQuery.trim().lowercase()
        val current = _contacts.value
        val toRemove = current.firstOrNull {
            it.name.lowercase().contains(cleanQuery) || cleanQuery.contains(it.name.lowercase())
        }
        if (toRemove != null) {
            _contacts.update { list -> list.filter { it.id != toRemove.id } }
            persistContacts()
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
            persistContacts()
            return updated
        }
        return null
    }

    private val _messages = MutableStateFlow<List<Message>>(
        listOf(
            Message(
                text = "Hola, bienvenido al servicio de mensajería adaptado de la UNCP.",
                isOutgoing = false,
                senderName = "Lic. Ana Ramos"
            ),
            Message(
                text = "¿Confirmas tu asistencia para la sesión de práctica de hoy?",
                isOutgoing = false,
                senderName = "Lic. Ana Ramos"
            )
        )
    )
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

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

    fun receiveMessage(text: String, senderName: String = "Lic. Ana Ramos") {
        val incoming = Message(
            text = text,
            isOutgoing = false,
            senderName = senderName
        )
        _messages.update { it + incoming }
    }
}
