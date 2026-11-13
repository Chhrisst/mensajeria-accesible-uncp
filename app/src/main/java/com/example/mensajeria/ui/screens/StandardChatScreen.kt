package com.example.mensajeria.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.mensajeria.data.model.Contact
import com.example.mensajeria.ui.components.VoiceCallOverlay
import com.example.mensajeria.ui.theme.PureBlack
import com.example.mensajeria.ui.theme.TextSecondaryGray
import com.example.mensajeria.ui.viewmodel.ChatViewModel
import java.util.Locale

/**
 * Pantalla 3.3: Chat estándar estilo WhatsApp (Modo Sin Discapacidad).
 * - Indicador de conexión en tiempo real ("en línea" / "últ. vez a la(s) HH:mm").
 * - Botón de llamadas de voz en la cabecera.
 * - Grabación y reproducción real de notas de voz estilo WhatsApp (mantener pulsado y soltar, o pulsar para grabar/enviar).
 */
@Composable
fun StandardChatScreen(
    contact: Contact,
    chatViewModel: ChatViewModel,
    onBackToContacts: () -> Unit
) {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    val uiState by chatViewModel.uiState.collectAsState()

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    var pendingActionAfterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) {
            pendingActionAfterPermission?.invoke()
        }
        pendingActionAfterPermission = null
    }

    val whatsappGreen = Color(0xFF25D366)
    val headerDark = Color(0xFF1F2C34)
    val backgroundDark = Color(0xFF0C1317)
    val sentBubbleColor = Color(0xFF005C4B)
    val receivedBubbleColor = Color(0xFF202C33)

    val activeContact = if (uiState.contact.id == contact.id) uiState.contact else contact

    LaunchedEffect(contact.id) {
        chatViewModel.isDisabilityMode = false
        chatViewModel.setContact(contact)
    }

    fun requestAudioThen(action: () -> Unit) {
        if (hasAudioPermission) {
            action()
        } else {
            pendingActionAfterPermission = action
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundDark)
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // CABECERA ESTILO WHATSAPP CON ESTADO DE CONEXIÓN Y BOTÓN DE LLAMADA
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerDark)
                    .padding(horizontal = 8.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        chatViewModel.vibrationService.vibrateClick()
                        onBackToContacts()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = Color.White
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2A3942)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeContact.name,
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val lastSeen = activeContact.getLastSeenDisplay()
                        Text(
                            text = when {
                                activeContact.isOnline -> "en línea"
                                lastSeen.isNotBlank() -> "últ. vez a la(s) $lastSeen"
                                else -> "desconectado"
                            },
                            color = if (activeContact.isOnline) whatsappGreen else TextSecondaryGray,
                            fontSize = 12.sp
                        )
                    }

                    IconButton(
                        onClick = {
                            chatViewModel.vibrationService.vibrateClick()
                            requestAudioThen { chatViewModel.startVoiceCall() }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Llamar",
                            tint = whatsappGreen,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            val timeFormat = remember { java.text.SimpleDateFormat("HH:mm", Locale.getDefault()) }

            // LISTA DE MENSAJES ESTILO WHATSAPP
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                reverseLayout = true
            ) {
                items(uiState.messages.reversed()) { message ->
                    val isOutgoing = message.isOutgoing
                    val timeStr = message.formattedTime.ifBlank {
                        timeFormat.format(java.util.Date(message.timestamp))
                    }
                    val isThisPlaying = uiState.playingMessageId == message.id

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start
                    ) {
                        Card(
                            shape = RoundedCornerShape(
                                topStart = 14.dp,
                                topEnd = 14.dp,
                                bottomStart = if (isOutgoing) 14.dp else 2.dp,
                                bottomEnd = if (isOutgoing) 2.dp else 14.dp
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isOutgoing) sentBubbleColor else receivedBubbleColor
                            ),
                            modifier = Modifier.fillMaxWidth(0.82f)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                if (message.isVoiceNote) {
                                    // BURBUJA INTERACTIVA DE NOTA DE VOZ REAL (REPRODUCIBLE PARA AMBOS USUARIOS)
                                    val mins = message.voiceDurationSeconds / 60
                                    val secs = message.voiceDurationSeconds % 60
                                    val durationLabel = String.format(Locale.getDefault(), "%d:%02d", mins, secs)

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                chatViewModel.vibrationService.vibrateClick()
                                                chatViewModel.togglePlayVoiceNote(message)
                                            }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(whatsappGreen),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = if (isThisPlaying) "Pausar nota de voz" else "Reproducir nota de voz",
                                                tint = PureBlack,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            LinearProgressIndicator(
                                                progress = { if (isThisPlaying) uiState.playbackProgress else 0f },
                                                color = whatsappGreen,
                                                trackColor = Color.White.copy(alpha = 0.25f),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(5.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = durationLabel,
                                                    color = Color.White.copy(alpha = 0.85f),
                                                    fontSize = 12.sp
                                                )
                                                Icon(
                                                    imageVector = Icons.Default.Mic,
                                                    contentDescription = null,
                                                    tint = whatsappGreen,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Text(
                                        text = message.text,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        lineHeight = 20.sp
                                    )
                                }

                                Text(
                                    text = timeStr,
                                    color = TextSecondaryGray,
                                    fontSize = 11.sp,
                                    modifier = Modifier.align(Alignment.End)
                                )
                            }
                        }
                    }
                }
            }

            // BARRA INFERIOR DE ESCRITURA Y GRABACIÓN DE NOTA DE VOZ ESTILO WHATSAPP
            if (uiState.isRecordingVoiceNote) {
                val recInfinite = rememberInfiniteTransition(label = "rec_blink")
                val micAlpha by recInfinite.animateFloat(
                    initialValue = 1f,
                    targetValue = 0.3f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(550, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "rec_alpha"
                )
                val micScale by recInfinite.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.18f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "rec_scale"
                )
                val recMins = uiState.recordingSeconds / 60
                val recSecs = uiState.recordingSeconds % 60
                val recTimeStr = String.format(Locale.getDefault(), "%d:%02d", recMins, recSecs)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(headerDark)
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        chatViewModel.vibrationService.vibrateClick()
                        chatViewModel.cancelVoiceNoteRecording()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Cancelar nota de voz",
                            tint = Color(0xFFEF5350),
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF2A3942))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = Color(0xFFEF5350),
                            modifier = Modifier
                                .size(22.dp)
                                .scale(micScale)
                                .alpha(micAlpha)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = recTimeStr,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Grabando audio...",
                            color = TextSecondaryGray,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(whatsappGreen)
                            .clickable(role = Role.Button) {
                                chatViewModel.vibrationService.vibrateClick()
                                chatViewModel.finishAndSendVoiceNoteRecording()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Enviar nota de voz",
                            tint = PureBlack,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Mensaje", color = TextSecondaryGray, fontSize = 15.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(26.dp)),
                        shape = RoundedCornerShape(26.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = headerDark,
                            unfocusedContainerColor = headerDark,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        maxLines = 4
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    if (inputText.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(whatsappGreen)
                                .clickable(role = Role.Button) {
                                    chatViewModel.vibrationService.vibrateClick()
                                    chatViewModel.sendTextMessage(inputText)
                                    inputText = ""
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Enviar texto",
                                tint = PureBlack,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else {
                        // Botón estilo WhatsApp: soporta toque corto (graba hasta pulsar Enviar) o mantener pulsado y soltar para enviar
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(whatsappGreen)
                                .pointerInput(hasAudioPermission) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        val pressStart = System.currentTimeMillis()
                                        chatViewModel.vibrationService.vibrateClick()
                                        if (hasAudioPermission) {
                                            chatViewModel.startVoiceNoteRecording()
                                        } else {
                                            down.consume()
                                            requestAudioThen { chatViewModel.startVoiceNoteRecording() }
                                            return@awaitEachGesture
                                        }
                                        val up = waitForUpOrCancellation()
                                        val heldMs = System.currentTimeMillis() - pressStart
                                        // Si mantuvo pulsado > 550ms y soltó, enviar automáticamente estilo WhatsApp;
                                        // si fue un toque rápido, dejar la barra de grabación abierta para hablar tranquilo y pulsar Enviar.
                                        if (up != null && heldMs > 550L) {
                                            chatViewModel.vibrationService.vibrateClick()
                                            chatViewModel.finishAndSendVoiceNoteRecording()
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Grabar nota de voz",
                                tint = PureBlack,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }

        if (uiState.isInCall) {
            VoiceCallOverlay(
                contactName = uiState.callPartner?.name?.ifBlank { activeContact.name } ?: activeContact.name,
                statusText = uiState.callStatusText,
                durationSeconds = uiState.callDurationSeconds,
                isMuted = uiState.isCallMuted,
                isSpeakerphoneOn = uiState.isSpeakerphoneOn,
                isAccessibleMode = false,
                isIncomingRinging = uiState.isIncomingCall,
                onAcceptCall = { chatViewModel.acceptIncomingCall() },
                onRejectCall = { chatViewModel.rejectIncomingCall() },
                onToggleMute = { chatViewModel.toggleCallMute() },
                onToggleSpeaker = { chatViewModel.toggleSpeakerphone() },
                onEndCall = { chatViewModel.endVoiceCall() }
            )
        }
    }
}
