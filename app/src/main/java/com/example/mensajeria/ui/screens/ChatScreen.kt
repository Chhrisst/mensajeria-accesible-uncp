package com.example.mensajeria.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mensajeria.data.model.VoiceState
import com.example.mensajeria.ui.components.AccessibleButton
import com.example.mensajeria.ui.components.MessageBubble
import com.example.mensajeria.ui.components.SmartReplyCard
import com.example.mensajeria.ui.theme.BackgroundDark
import com.example.mensajeria.ui.theme.ErrorRed
import com.example.mensajeria.ui.theme.HighContrastYellow
import com.example.mensajeria.ui.theme.SuccessGreen
import com.example.mensajeria.ui.theme.SurfaceDark
import com.example.mensajeria.ui.theme.SurfaceVariantDark
import com.example.mensajeria.ui.theme.TextDark
import com.example.mensajeria.ui.theme.TextPrimary
import com.example.mensajeria.ui.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    // Auto-scroll al último mensaje cuando cambie la lista
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.contact.name,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = "En línea • Modo accesible",
                            fontSize = 14.sp,
                            color = HighContrastYellow
                        )
                    }
                },
                actions = {
                    AccessibleButton(
                        text = "🔊 Escuchar",
                        contentDescription = "Volver a escuchar el último mensaje",
                        onClick = { viewModel.repeatLastMessage() },
                        modifier = Modifier.padding(end = 8.dp),
                        backgroundColor = SurfaceVariantDark,
                        textColor = HighContrastYellow
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        },
        containerColor = BackgroundDark
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp)
        ) {
            // Lista de mensajes (LazyColumn)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(state.messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        onSpeakMessage = { text -> viewModel.repeatLastMessage() }
                    )
                }
            }

            // Sección de Respuestas Rápidas por Inteligencia Artificial (Smart Replies)
            if (state.smartReplies.isNotEmpty() && state.voiceState != VoiceState.CONFIRMING) {
                Text(
                    text = "Sugerencias inteligentes (1 toque):",
                    color = HighContrastYellow,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.smartReplies.forEachIndexed { index, reply ->
                        SmartReplyCard(
                            replyText = reply,
                            index = index,
                            onClick = { viewModel.selectSmartReply(reply) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Panel de Confirmación de Mensaje Dictado (Flujo de accesibilidad vital para invidentes)
            if (state.voiceState == VoiceState.CONFIRMING) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "¿Deseas enviar este mensaje?",
                            color = HighContrastYellow,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "\"${state.pendingSpokenText}\"",
                            color = TextPrimary,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AccessibleButton(
                                text = "✓ ENVIAR",
                                contentDescription = "Confirmar y enviar mensaje",
                                onClick = { viewModel.confirmAndSendMessage() },
                                backgroundColor = SuccessGreen,
                                textColor = TextDark,
                                modifier = Modifier.weight(1f)
                            )
                            AccessibleButton(
                                text = "✕ CANCELAR",
                                contentDescription = "Cancelar mensaje y volver a dictar",
                                onClick = { viewModel.cancelPendingMessage() },
                                backgroundColor = ErrorRed,
                                textColor = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            } else {
                // Barra inferior accesible de dictado por voz
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    if (state.voiceState == VoiceState.LISTENING) {
                        AccessibleButton(
                            text = "🔴 ESCUCHANDO... (Pulsar para detener)",
                            contentDescription = "El micrófono está escuchando. Dicte su mensaje o pulse para detener.",
                            onClick = { viewModel.stopListening() },
                            backgroundColor = ErrorRed,
                            textColor = TextPrimary
                        )
                    } else {
                        AccessibleButton(
                            text = "🎙️ DICTAR MENSAJE POR VOZ",
                            contentDescription = "Botón principal de dictado. Pulse para hablar su mensaje.",
                            onClick = { viewModel.startListening() },
                            backgroundColor = HighContrastYellow,
                            textColor = TextDark
                        )
                    }
                }
            }
        }
    }
}
