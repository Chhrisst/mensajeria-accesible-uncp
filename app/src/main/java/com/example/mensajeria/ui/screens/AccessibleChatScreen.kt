package com.example.mensajeria.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.runtime.mutableStateOf
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

/**
 * Pantalla 2.5: Conversación accesible con navegación por arrastre de dedo,
 * botón salir en esquina superior izquierda, mensaje de voz a texto,
 * nota de voz, sonido de notificación agradable y confirmación por lados.
 */
@Composable
fun AccessibleChatScreen(
    contact: Contact,
    chatViewModel: ChatViewModel,
    onBackToContacts: () -> Unit
) {
    val uiState by chatViewModel.uiState.collectAsState()
    var hasLeftPressed by remember { mutableStateOf(false) }
    var hasRightPressed by remember { mutableStateOf(false) }
    var activeAction by remember { mutableStateOf<String?>("VOZ_A_TEXTO") } // "VOZ_A_TEXTO", "NOTA_DE_VOZ", "SALIR"

    LaunchedEffect(contact) {
        chatViewModel.setContact(contact)
        chatViewModel.ttsService.speak(
            "Chat con ${contact.name}. Arrastra el dedo para explorar mensajes y botones. Para salir, el botón está en la esquina superior izquierda. Para seleccionar, pulsa ambos lados de la pantalla."
        )
    }

    fun executeSelectedAction() {
        when (activeAction) {
            "SALIR" -> {
                chatViewModel.soundService.playSuccessSound()
                chatViewModel.ttsService.speak("Saliendo del chat.")
                onBackToContacts()
            }
            "VOZ_A_TEXTO" -> {
                chatViewModel.ttsService.speak("Iniciando dictado de mensaje. Habla ahora.")
                chatViewModel.startListening()
            }
            "NOTA_DE_VOZ" -> {
                chatViewModel.sendVoiceNote(4)
            }
            else -> {
                chatViewModel.startListening()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .pointerInput(Unit) {
                // Detección de arrastre sobre pantalla para explorar botones o mensajes
                detectDragGestures { change, _ ->
                    val pos = change.position
                    if (pos.x < 150f && pos.y < 200f) {
                        if (activeAction != "SALIR") {
                            activeAction = "SALIR"
                            chatViewModel.ttsService.speak("Esquina superior izquierda: Botón salir del chat.")
                        }
                    } else if (pos.y > 1500f) {
                        if (pos.x < 500f) {
                            if (activeAction != "VOZ_A_TEXTO") {
                                activeAction = "VOZ_A_TEXTO"
                                chatViewModel.ttsService.speak("Botón: Enviar mensaje de voz a texto.")
                            }
                        } else {
                            if (activeAction != "NOTA_DE_VOZ") {
                                activeAction = "NOTA_DE_VOZ"
                                chatViewModel.ttsService.speak("Botón: Grabar y enviar nota de voz.")
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
            // CABECERA: Botón Salir en Esquina Superior Izquierda + Nombre del Contacto
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Botón Salir (Esquina Superior Izquierda accesible)
                    AccessibleBackButton(
                        onClick = {
                            chatViewModel.ttsService.speak("Saliendo de la conversación.")
                            onBackToContacts()
                        },
                        modifier = Modifier
                            .size(54.dp)
                            .semantics {
                                this.role = Role.Button
                                this.contentDescription = "Salir del chat. Regresar a la lista de contactos."
                            }
                    )

                    Spacer(modifier = Modifier.size(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = contact.name,
                            color = AccessibleYellow,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = if (contact.isOnline) "● En línea" else "Desconectado",
                            color = if (contact.isOnline) Color(0xFF00E676) else TextSecondaryGray,
                            fontSize = 13.sp
                        )
                    }

                    // Botón simulador de mensaje entrante para comprobar sonido agradable de notificación
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(DarkCardBackground)
                            .clickable {
                                chatViewModel.simulateIncomingMessage("Perfecto, nos vemos en el laboratorio accesible.")
                            }
                            .semantics {
                                this.role = Role.Button
                                this.contentDescription = "Simular mensaje entrante para escuchar el sonido de notificación."
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

                Spacer(modifier = Modifier.height(12.dp))

                // Estado de dictado o confirmación
                if (uiState.voiceState == VoiceState.CONFIRMING) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = AccessibleYellow)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "¿Deseas enviar este mensaje?",
                                color = PureBlack,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "\"${uiState.pendingSpokenText}\"",
                                color = Color(0xFF1C1C1E),
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                AccessiblePrimaryButton(
                                    text = "Confirmar y Enviar",
                                    onClick = { chatViewModel.confirmAndSendMessage() },
                                    modifier = Modifier.weight(1f)
                                )
                                AccessiblePrimaryButton(
                                    text = "Cancelar",
                                    onClick = { chatViewModel.cancelPendingMessage() },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // LISTA DE MENSAJES ACCESIBLES
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                reverseLayout = true
            ) {
                items(uiState.messages.reversed()) { message ->
                    MessageBubble(
                        message = message,
                        onSpeakMessage = { text ->
                            chatViewModel.ttsService.speak(
                                "${if (message.isOutgoing) "Tú dijiste" else "${contact.name} dijo"}: $text"
                            )
                        }
                    )
                }
            }

            // ACCIONES DEL CHAT Y SELECCIÓN LATERAL
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // OPCIÓN 1: VOZ A TEXTO VS OPCIÓN 2: MENSAJE DE VOZ
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Botón 1: Voz a Texto
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(68.dp)
                            .clickable {
                                activeAction = "VOZ_A_TEXTO"
                                chatViewModel.ttsService.speak("Iniciando mensaje de voz a texto. Habla ahora.")
                                chatViewModel.startListening()
                            }
                            .semantics {
                                this.role = Role.Button
                                this.contentDescription = "Enviar mensaje de voz a texto."
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (activeAction == "VOZ_A_TEXTO") AccessibleYellow else DarkCardBackground
                        ),
                        border = BorderStroke(2.dp, AccessibleYellow)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                tint = if (activeAction == "VOZ_A_TEXTO") PureBlack else AccessibleYellow,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = "Voz a Texto",
                                color = if (activeAction == "VOZ_A_TEXTO") PureBlack else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    // Botón 2: Mensaje de Voz (Nota de audio)
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(68.dp)
                            .clickable {
                                activeAction = "NOTA_DE_VOZ"
                                chatViewModel.sendVoiceNote(4)
                            }
                            .semantics {
                                this.role = Role.Button
                                this.contentDescription = "Enviar nota de voz grabada."
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (activeAction == "NOTA_DE_VOZ") AccessibleYellow else DarkCardBackground
                        ),
                        border = BorderStroke(2.dp, AccessibleYellow)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = if (activeAction == "NOTA_DE_VOZ") PureBlack else AccessibleYellow,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = "Mensaje de Voz",
                                color = if (activeAction == "NOTA_DE_VOZ") PureBlack else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // CONTROLES DE PULSACIÓN EN AMBOS LADOS DE LA PANTALLA
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clickable {
                                hasLeftPressed = true
                                chatViewModel.ttsService.speak("Lado izquierdo pulsado.")
                                if (hasRightPressed) {
                                    executeSelectedAction()
                                    hasLeftPressed = false
                                    hasRightPressed = false
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (hasLeftPressed) AccessibleYellow else DarkCardBackground
                        ),
                        border = BorderStroke(1.5.dp, AccessibleYellow)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "LADO IZQUIERDO",
                                color = if (hasLeftPressed) PureBlack else AccessibleYellow,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clickable {
                                hasRightPressed = true
                                chatViewModel.ttsService.speak("Lado derecho pulsado.")
                                if (hasLeftPressed) {
                                    executeSelectedAction()
                                    hasLeftPressed = false
                                    hasRightPressed = false
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (hasRightPressed) AccessibleYellow else DarkCardBackground
                        ),
                        border = BorderStroke(1.5.dp, AccessibleYellow)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "LADO DERECHO",
                                color = if (hasRightPressed) PureBlack else AccessibleYellow,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Botón para repetir último mensaje en voz alta
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { chatViewModel.repeatLastMessage() }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = TextSecondaryGray,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = "Tocar para escuchar el último mensaje",
                        color = TextSecondaryGray,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
