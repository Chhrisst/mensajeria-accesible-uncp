package com.example.mensajeria.data.repository

import com.example.mensajeria.data.model.Contact
import com.example.mensajeria.data.model.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Repositorio de mensajería para gestionar conversaciones y contactos.
 */
class ChatRepository {

    private val _contacts = MutableStateFlow<List<Contact>>(
        listOf(
            Contact(
                id = "c1",
                name = "Lic. Ana Ramos",
                description = "Coordinadora de accesibilidad - UNCP",
                isOnline = true
            ),
            Contact(
                id = "c2",
                name = "Carlos Quispe",
                description = "Socio UNCP - Taller de cómputo",
                isOnline = true
            ),
            Contact(
                id = "c3",
                name = "Soporte Técnico UNCP",
                description = "Mesa de ayuda accesible",
                isOnline = true
            )
        )
    )
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

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
