package com.example.mensajeria.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import com.example.mensajeria.ui.theme.PureBlack
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mensajeria.data.model.Contact
import com.example.mensajeria.ui.theme.TextSecondaryGray
import com.example.mensajeria.ui.viewmodel.ChatViewModel

/**
 * Pantalla 3.3: Chat estándar estilo WhatsApp (Modo Sin Discapacidad).
 */
@Composable
fun StandardChatScreen(
    contact: Contact,
    chatViewModel: ChatViewModel,
    onBackToContacts: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val uiState by chatViewModel.uiState.collectAsState()

    val whatsappGreen = Color(0xFF25D366)
    val headerDark = Color(0xFF1F2C34)
    val backgroundDark = Color(0xFF0C1317)
    val sentBubbleColor = Color(0xFF005C4B)
    val receivedBubbleColor = Color(0xFF202C33)

    LaunchedEffect(contact) {
        chatViewModel.isDisabilityMode = false
        chatViewModel.setContact(contact)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundDark)
    ) {
        // CABECERA ESTILO WHATSAPP
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
                IconButton(onClick = onBackToContacts) {
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
                        text = contact.name,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (contact.isOnline) {
                        Text(
                            text = "en línea",
                            color = whatsappGreen,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        val timeFormat = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }

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
                val timeStr = timeFormat.format(java.util.Date(message.timestamp))
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
                            if (!isOutgoing) {
                                Text(
                                    text = message.senderName,
                                    color = whatsappGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                text = message.text,
                                color = Color.White,
                                fontSize = 15.sp,
                                lineHeight = 20.sp
                            )

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

        // BARRA INFERIOR DE ESCRITURA ESTILO WHATSAPP
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

            // Botón de Enviar Texto o Enviar Nota de Voz
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(whatsappGreen)
                    .clickable {
                        if (inputText.isNotBlank()) {
                            chatViewModel.sendTextMessage(inputText)
                            inputText = ""
                        } else {
                            chatViewModel.sendVoiceNote(4)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (inputText.isNotBlank()) Icons.AutoMirrored.Filled.Send else Icons.Default.Mic,
                    contentDescription = if (inputText.isNotBlank()) "Enviar texto" else "Grabar nota de voz",
                    tint = PureBlack,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
