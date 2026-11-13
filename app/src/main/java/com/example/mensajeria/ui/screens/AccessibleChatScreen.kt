package com.example.mensajeria.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mensajeria.data.model.Contact
import com.example.mensajeria.data.model.VoiceState
import com.example.mensajeria.domain.service.VoiceAssistantManager
import com.example.mensajeria.domain.service.VoiceAssistantState
import com.example.mensajeria.ui.components.AccessibleBackButton
import com.example.mensajeria.ui.components.AccessibleVoiceAssistantBar
import com.example.mensajeria.ui.components.MessageBubble
import com.example.mensajeria.ui.components.VoiceCallOverlay
import com.example.mensajeria.ui.components.exploreTarget
import com.example.mensajeria.ui.theme.AccessibleYellow
import com.example.mensajeria.ui.theme.DarkCardBackground
import com.example.mensajeria.ui.theme.PureBlack
import com.example.mensajeria.ui.viewmodel.ChatViewModel
import kotlin.math.abs

/**
 * Pantalla 2.5: Conversación accesible simplificada.
 * - Encabezado limpio con botón salir, estado de conexión ("En línea" / "Conectado última vez a la(s) ...") y botón de llamada.
 * - Exploración táctil que dice únicamente el mensaje tal cual (y reproduce audio real en notas de voz).
 * - Un único botón inferior del Asistente de Voz IA para enviar mensajes de texto, notas de voz o consultar.
 */
@Composable
fun AccessibleChatScreen(
    contact: Contact,
    chatViewModel: ChatViewModel,
    voiceAssistantManager: VoiceAssistantManager? = null,
    onBackToContacts: () -> Unit
) {
    val uiState by chatViewModel.uiState.collectAsState()
    val assistantState = voiceAssistantManager?.state?.collectAsState()?.value ?: VoiceAssistantState()
    var focusedMessageIndex by remember { mutableIntStateOf(-1) }
    val listState = rememberLazyListState()

    val connectionStatus = remember(contact.id, contact.isOnline, contact.lastSeenTime, uiState.contact) {
        val activeContact = if (uiState.contact.id == contact.id) uiState.contact else contact
        activeContact.getConnectionStatusText()
    }
    val isContactOnline = (if (uiState.contact.id == contact.id) uiState.contact.isOnline else contact.isOnline)

    LaunchedEffect(contact) {
        chatViewModel.setContact(contact)
        chatViewModel.ttsService.speak(
            "Chat con ${contact.name}. $connectionStatus. Desliza para explorar mensajes o pulsa el botón inferior del asistente de voz."
        )
    }

    fun focusMessage(index: Int) {
        val messages = uiState.messages
        if (messages.isEmpty()) return
        val clamped = index.coerceIn(0, messages.size - 1)
        if (clamped != focusedMessageIndex || messages[clamped].isVoiceNote) {
            focusedMessageIndex = clamped
            chatViewModel.soundService.playButtonClickSound()
            val msg = messages[clamped]
            if (msg.isVoiceNote) {
                chatViewModel.togglePlayVoiceNote(msg)
            } else {
                // Solo decir el mensaje tal cual, sin anteponer el nombre del contacto
                chatViewModel.ttsService.speak(msg.text)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .navigationBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(uiState.messages, uiState.voiceState) {
                    awaitPointerEventScope {
                        var lastLeftTouch = 0L
                        var lastRightTouch = 0L
                        var lastConfirmTime = 0L

                        while (true) {
                            val event = awaitPointerEvent()
                            val now = System.currentTimeMillis()
                            val width = size.width

                            val pressedPointers = event.changes.filter { it.pressed }
                            if (pressedPointers.size >= 2) {
                                val hasLeft = pressedPointers.any { it.position.x < width * 0.45f }
                                val hasRight = pressedPointers.any { it.position.x > width * 0.55f }
                                if ((hasLeft && hasRight || pressedPointers.size >= 2) && now - lastConfirmTime > 1000L) {
                                    lastConfirmTime = now
                                    event.changes.forEach { it.consume() }
                                    if (uiState.voiceState == VoiceState.CONFIRMING) {
                                        chatViewModel.confirmAndSendMessage()
                                    }
                                }
                            } else if (pressedPointers.size == 1) {
                                val change = pressedPointers[0]
                                val x = change.position.x

                                if (x < width * 0.40f) {
                                    lastLeftTouch = now
                                } else if (x > width * 0.60f) {
                                    lastRightTouch = now
                                }

                                if (abs(lastLeftTouch - lastRightTouch) < 500L && now - lastConfirmTime > 1000L) {
                                    lastConfirmTime = now
                                    lastLeftTouch = 0L
                                    lastRightTouch = 0L
                                    if (uiState.voiceState == VoiceState.CONFIRMING) {
                                        change.consume()
                                        chatViewModel.confirmAndSendMessage()
                                    }
                                }
                            }
                        }
                    }
                }
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // ENCABEZADO SUPERIOR ACCESIBLE CON ESTADO DE CONEXIÓN Y BOTÓN LLAMAR
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AccessibleBackButton(
                        onClick = {
                            chatViewModel.soundService.playButtonClickSound()
                            chatViewModel.ttsService.speak("Saliendo de la conversación.")
                            onBackToContacts()
                        },
                        modifier = Modifier.size(50.dp),
                        exploreLabel = "Salir del chat"
                    )

                    Spacer(modifier = Modifier.size(14.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .exploreTarget(
                                id = "chat_contact_header",
                                label = "${contact.name}, $connectionStatus"
                            )
                    ) {
                        Text(
                            text = contact.name,
                            color = AccessibleYellow,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = if (isContactOnline) "● En línea" else connectionStatus,
                            color = if (isContactOnline) Color(0xFF00E676) else Color(0xFFB0BEC5),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // BOTÓN DE LLAMADA DE VOZ ACCESIBLE
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .exploreTarget(id = "chat_call_btn", label = "Llamar a ${contact.name}")
                            .clip(CircleShape)
                            .background(DarkCardBackground)
                            .border(2.dp, AccessibleYellow, CircleShape)
                            .clickable(role = Role.Button) {
                                chatViewModel.startVoiceCall()
                            }
                            .semantics {
                                this.role = Role.Button
                                this.contentDescription = "Llamar a ${contact.name}"
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = null,
                            tint = AccessibleYellow,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // LISTA DE MENSAJES ACCESIBLES (LECTURA DIRECTA DEL MENSAJE TAL CUAL)
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    state = listState,
                    reverseLayout = true
                ) {
                    itemsIndexed(uiState.messages.reversed()) { index, message ->
                        val actualIndex = uiState.messages.size - 1 - index
                        val exploreText = if (message.isVoiceNote) {
                            "${message.text}. Pulsa para escuchar audio."
                        } else {
                            message.text
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .exploreTarget(
                                    id = "chat_msg_${message.id}_$actualIndex",
                                    label = exploreText,
                                    onHover = { focusedMessageIndex = actualIndex }
                                )
                                .clickable {
                                    focusMessage(actualIndex)
                                }
                        ) {
                            MessageBubble(
                                message = message,
                                isPlayingVoiceNote = uiState.playingMessageId == message.id,
                                onSpeakMessage = {
                                    focusMessage(actualIndex)
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ZONA INFERIOR SIMPLIFICADA A UN ÚNICO BOTÓN DE ASISTENTE DE VOZ IA
                if (uiState.isRecordingVoiceNote) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFB71C1C)),
                        border = BorderStroke(2.5.dp, AccessibleYellow)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.size(10.dp))
                                Text(
                                    text = "Grabando audio (${uiState.recordingSeconds}s)",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .exploreTarget(id = "voice_rec_cancel", label = "Cancelar grabación")
                                        .clip(CircleShape)
                                        .background(PureBlack)
                                        .clickable { chatViewModel.cancelVoiceNoteRecording() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Cancelar", tint = Color.White)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .exploreTarget(id = "voice_rec_send", label = "Enviar nota de voz")
                                        .clip(CircleShape)
                                        .background(AccessibleYellow)
                                        .clickable { chatViewModel.finishAndSendVoiceNoteRecording() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = "Enviar", tint = PureBlack)
                                }
                            }
                        }
                    }
                } else if (uiState.voiceState == VoiceState.CONFIRMING) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = AccessibleYellow)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "¿Deseas enviar este mensaje?",
                                color = PureBlack,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "\"${uiState.pendingSpokenText}\"",
                                color = Color(0xFF1C1C1E),
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp)
                                        .exploreTarget(id = "chat_confirm_cancel", label = "Cancelar")
                                        .clickable(role = Role.Button) { chatViewModel.cancelPendingMessage() },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = PureBlack),
                                    border = BorderStroke(2.dp, PureBlack)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
                                        Spacer(modifier = Modifier.size(6.dp))
                                        Text("Cancelar", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    }
                                }

                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp)
                                        .exploreTarget(id = "chat_confirm_send", label = "Enviar")
                                        .clickable(role = Role.Button) { chatViewModel.confirmAndSendMessage() },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = PureBlack),
                                    border = BorderStroke(2.dp, PureBlack)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = AccessibleYellow)
                                        Spacer(modifier = Modifier.size(6.dp))
                                        Text("Enviar", color = AccessibleYellow, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // ÚNICO BOTÓN PRINCIPAL: ASISTENTE DE VOZ IA
                    if (voiceAssistantManager != null) {
                        AccessibleVoiceAssistantBar(
                            state = assistantState,
                            onAssistantClick = {
                                voiceAssistantManager.triggerExplicitActivation()
                            }
                        )
                    } else {
                        AccessibleVoiceAssistantBar(
                            state = VoiceAssistantState(feedbackMessage = "Pulsa para dictar mensaje o enviar audio"),
                            onAssistantClick = {
                                chatViewModel.soundService.playButtonClickSound()
                                chatViewModel.ttsService.speak(
                                    "Habla después del tono.",
                                    onDone = { chatViewModel.startListening() }
                                )
                            }
                        )
                    }
                }
            }
        }

        if (uiState.isInCall) {
            VoiceCallOverlay(
                contactName = contact.name,
                statusText = uiState.callStatusText,
                durationSeconds = uiState.callDurationSeconds,
                isMuted = uiState.isCallMuted,
                isSpeakerphoneOn = uiState.isSpeakerphoneOn,
                isAccessibleMode = true,
                onToggleMute = { chatViewModel.toggleCallMute() },
                onToggleSpeaker = { chatViewModel.toggleSpeakerphone() },
                onEndCall = { chatViewModel.endVoiceCall() }
            )
        }
    }
}
