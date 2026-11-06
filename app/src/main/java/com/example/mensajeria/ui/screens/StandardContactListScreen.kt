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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mensajeria.data.model.Contact
import com.example.mensajeria.data.model.UserProfile
import com.example.mensajeria.ui.theme.TextSecondaryGray

/**
 * Pantalla 3.2: Lista de conversaciones estilo WhatsApp (Modo Sin Discapacidad).
 */
@Composable
fun StandardContactListScreen(
    currentUser: UserProfile,
    contacts: List<Contact>,
    onSelectContact: (Contact) -> Unit,
    onLogout: () -> Unit
) {
    val whatsappGreen = Color(0xFF25D366)
    val headerDark = Color(0xFF1F2C34)
    val backgroundDark = Color(0xFF0C1317)

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
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Mensajería UNCP",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Search, contentDescription = "Buscar", tint = TextSecondaryGray)
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Cerrar sesión", tint = TextSecondaryGray)
                    }
                }
            }
        }

        // Pestaña activa CHATS
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerDark)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = "CHATS",
                color = whatsappGreen,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        // LISTA DE CONTACTOS ESTILO WHATSAPP
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(contacts) { contact ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectContact(contact) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar redondo con inicial o icono
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2A3942)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = contact.name,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "12:30",
                                color = TextSecondaryGray,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(3.dp))

                        Text(
                            text = contact.description.ifBlank { "Toca para iniciar la conversación" },
                            color = TextSecondaryGray,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                HorizontalDivider(color = Color(0xFF1F2C34), thickness = 0.8.dp)
            }
        }
    }
}
