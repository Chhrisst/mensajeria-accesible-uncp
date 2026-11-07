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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.VolumeUp
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
import com.example.mensajeria.ui.components.AccessibleBackButton
import com.example.mensajeria.ui.components.AccessiblePrimaryButton
import com.example.mensajeria.ui.components.MessageBubble
import com.example.mensajeria.ui.theme.AccessibleYellow
import com.example.mensajeria.ui.theme.DarkCardBackground
import com.example.mensajeria.ui.theme.PureBlack
import com.example.mensajeria.ui.theme.TextSecondaryGray
import com.example.mensajeria.ui.viewmodel.ChatViewModel
import kotlin.math.abs

/**
 * Pantalla 2.5: Conversación accesible optimizada para superar la experiencia de WhatsApp.
 * - Encabezado limpio con botón salir accesible y nombre del contacto.
 * - Narración breve dicha solo una vez.
 * - Exploración táctil continua de mensajes sin sobrecarga de metadatos.
 * - Resumen inteligente con Inteligencia Artificial (Gemini) en 1 toque.
 * - Dictado asistido por voz con puntuación automática.
 * - Confirmación bimanual intuitiva (pulsar ambos lados).
 */
@Composable
fun AccessibleChatScreen(
    contact: Contact,
    chatViewModel: ChatViewModel,
    onBackToContacts: () -> Unit
) {
    val uiState by chatViewModel.uiState.collectAsState()
    var focusedMessageIndex by remember { mutableIntStateOf(-1) }
    val listState = rememberLazyListState()

    // Narración inicial breve dicha una sola vez
    LaunchedEffect(contact) {
        chatViewModel.setContact(contact)
        chatViewModel.ttsService.speak("Chat con ${contact.name}. Desliza para explorar mensajes.")
    }

    fun focusMessage(index: Int) {
        val messages = uiState.messages
        if (messages.isEmpty()) return
        val clamped = index.coerceIn(0, messages.size - 1)
        if (clamped != focusedMessageIndex) {
            focusedMessageIndex = clamped
            chatViewModel.soundService.playButtonClickSound()
            val msg = messages[clamped]
            val speaker = if (msg.isOutgoing) "Tú" else contact.name
            chatViewModel.ttsService.speak("$speaker: ${msg.text}")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .navigationBarsPadding()
            .pointerInput(uiState.messages) {
                awaitPointerEventScope {
                    var lastLeftTouch = 0L
                    var lastRightTouch = 0L
                    var lastConfirmTime = 0L

                    while (true) {
                        val event = awaitPointerEvent()
                        val now = System.currentTimeMillis()
                        val width = size.width

                        val pressedPointers = event.changes.filter { it.pressed }
                        // Gesto bimanual: tocar ambos lados de la pantalla
                        if (pressedPointers.size >= 2) {
                            val hasLeft = pressedPointers.any { it.position.x < width * 0.45f }
                            val hasRight = pressedPointers.any { it.position.x > width * 0.55f }
                            if ((hasLeft && hasRight || pressedPointers.size >= 2) && now - lastConfirmTime > 1000L) {
                                lastConfirmTime = now
                                event.changes.forEach { it.consume() }
                                if (uiState.voiceState == VoiceState.CONFIRMING) {
                                    chatViewModel.confirmAndSendMessage()
                                } else {
                                    chatViewModel.summarizeLastMessage()
                                }
                            }
                        } else if (pressedPointers.size == 1) {
                            val change = pressedPointers[0]
                            val x = change.position.x
                            val y = change.position.y

                            if (x < width * 0.40f) {
                                lastLeftTouch = now
                            } else if (x > width * 0.60f) {
                                lastRightTouch = now
                            }

                            // Toque alternado rápido entre lados (<500ms)
                            if (abs(lastLeftTouch - lastRightTouch) < 500L && now - lastConfirmTime > 1000L) {
                                lastConfirmTime = now
                                lastLeftTouch = 0L
                                lastRightTouch = 0L
                                change.consume()
                                if (uiState.voiceState == VoiceState.CONFIRMING) {
                                    chatViewModel.confirmAndSendMessage()
                                } else {
                                    chatViewModel.summarizeLastMessage()
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
            // ENCABEZADO SUPERIOR ACCESIBLE
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Botón Salir grande y accesible
                AccessibleBackButton(
                    onClick = {
                        chatViewModel.soundService.playButtonClickSound()
                        chatViewModel.ttsService.speak("Saliendo de la conversación.")
                        onBackToContacts()
                    },
                    modifier = Modifier.size(50.dp)
                )

                Spacer(modifier = Modifier.size(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = contact.name,
                        color = AccessibleYellow,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = if (contact.isOnline) "● En línea" else "Desconectado",
                        color = if (contact.isOnline) Color(0xFF00E676) else TextSecondaryGray,
                        fontSize = 13.sp
                    )
                }

                // Botón simulador de mensaje entrante accesible
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(DarkCardBackground)
                        .border(1.5.dp, AccessibleYellow.copy(alpha = 0.5f), CircleShape)
                        .clickable {
                            chatViewModel.simulateIncomingMessage("Estimado socio, la asamblea general de la UNCP será este viernes a las 4 pm en el auditorio. Por favor confirme su asistencia.")
                        }
                        .semantics {
                            this.role = Role.Button
                            this.contentDescription = "Simular mensaje recibido para probar resumen con Inteligencia Artificial."
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = AccessibleYellow,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // LISTA DE MENSAJES ACCESIBLES
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
                    val isFocused = actualIndex == focusedMessageIndex
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                focusMessage(actualIndex)
                            }
                    ) {
                        MessageBubble(
                            message = message,
                            onSpeakMessage = { text ->
                                focusMessage(actualIndex)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // FASE DE CONFIRMACIÓN O ACCIONES PRINCIPALES
            if (uiState.voiceState == VoiceState.CONFIRMING) {
                // PANEL DE CONFIRMACIÓN DE MENSAJE DICTADO
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
                            // CANCELAR
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                                    .clickable { chatViewModel.cancelPendingMessage() },
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

                            // CONFIRMAR Y ENVIAR
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                                    .clickable { chatViewModel.confirmAndSendMessage() },
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
                // ACCIONES ACCESIBLES: DICTAR MENSAJE Y RESUMEN IA
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // BOTÓN 1: DICTAR MENSAJE (Voz a Texto con puntuación IA)
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(72.dp)
                                .clickable {
                                    chatViewModel.soundService.playButtonClickSound()
                                    chatViewModel.ttsService.speak("Iniciando dictado. Habla después del tono.")
                                    chatViewModel.startListening()
                                }
                                .semantics {
                                    this.role = Role.Button
                                    this.contentDescription = "Dictar mensaje por voz"
                                },
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (uiState.voiceState == VoiceState.LISTENING) AccessibleYellow else DarkCardBackground
                            ),
                            border = BorderStroke(2.5.dp, AccessibleYellow)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RecordVoiceOver,
                                    contentDescription = null,
                                    tint = if (uiState.voiceState == VoiceState.LISTENING) PureBlack else AccessibleYellow,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.size(10.dp))
                                Column {
                                    Text(
                                        text = if (uiState.voiceState == VoiceState.LISTENING) "Escuchando..." else "Dictar",
                                        color = if (uiState.voiceState == VoiceState.LISTENING) PureBlack else AccessibleYellow,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp
                                    )
                                    Text(
                                        text = "Voz a texto",
                                        color = if (uiState.voiceState == VoiceState.LISTENING) PureBlack else Color.White,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        // BOTÓN 2: RESUMEN CON IA (Google Gemini)
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(72.dp)
                                .clickable {
                                    chatViewModel.soundService.playButtonClickSound()
                                    chatViewModel.summarizeLastMessage()
                                }
                                .semantics {
                                    this.role = Role.Button
                                    this.contentDescription = "Resumir con Inteligencia Artificial"
                                },
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkCardBackground),
                            border = BorderStroke(2.5.dp, AccessibleYellow)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = AccessibleYellow,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.size(10.dp))
                                Column {
                                    Text(
                                        text = "Resumen IA",
                                        color = AccessibleYellow,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp
                                    )
                                    Text(
                                        text = "Sintetizar",
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    // BOTÓN INFERIOR: REPETIR ÚLTIMO MENSAJE
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clickable {
                                chatViewModel.soundService.playButtonClickSound()
                                chatViewModel.repeatLastMessage()
                            }
                            .semantics {
                                this.role = Role.Button
                                this.contentDescription = "Escuchar el último mensaje"
                            },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCardBackground),
                        border = BorderStroke(1.dp, AccessibleYellow.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = AccessibleYellow,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = "Escuchar último mensaje",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
