package com.example.mensajeria.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mensajeria.data.model.Contact
import com.example.mensajeria.data.model.UserProfile
import com.example.mensajeria.data.repository.ChatRepository
import com.example.mensajeria.ui.theme.PureBlack
import com.example.mensajeria.ui.theme.TextSecondaryGray
import kotlinx.coroutines.launch

/**
 * Pantalla 3.2: Lista de conversaciones estilo WhatsApp (Modo Sin Discapacidad).
 * Permite agregar nuevos contactos mediante su identificador único (@username).
 */
@Composable
fun StandardContactListScreen(
    currentUser: UserProfile,
    contacts: List<Contact>,
    chatRepository: ChatRepository? = null,
    onSelectContact: (Contact) -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val whatsappGreen = Color(0xFF25D366)
    val headerDark = Color(0xFF1F2C34)
    val backgroundDark = Color(0xFF0C1317)

    var showAddDialog by remember { mutableStateOf(false) }
    var newContactUsername by remember { mutableStateOf("") }
    var isAdding by remember { mutableStateOf(false) }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Text(
                    text = "Agregar Contacto",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Ingresa el nombre de usuario único (@usuario) de la persona que deseas agregar:",
                        color = TextSecondaryGray,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newContactUsername,
                        onValueChange = { newContactUsername = it },
                        placeholder = { Text("@usuario_ejemplo", color = TextSecondaryGray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = whatsappGreen,
                            unfocusedBorderColor = Color(0xFF2A3942),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = newContactUsername.trim().removePrefix("@")
                        if (clean.isNotBlank() && chatRepository != null) {
                            isAdding = true
                            coroutineScope.launch {
                                val result = chatRepository.addContactByUsername(currentUser.id, clean)
                                isAdding = false
                                if (result.isSuccess) {
                                    showAddDialog = false
                                    newContactUsername = ""
                                    val added = result.getOrNull()!!
                                    val displayName = if (added.username.isNotBlank()) "@${added.username}" else added.name
                                    Toast.makeText(context, "Contacto $displayName agregado", Toast.LENGTH_SHORT).show()
                                } else {
                                    val err = result.exceptionOrNull()?.message ?: "Usuario no encontrado"
                                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = whatsappGreen,
                        contentColor = PureBlack
                    ),
                    enabled = !isAdding && newContactUsername.isNotBlank()
                ) {
                    Text(if (isAdding) "Buscando..." else "Agregar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancelar", color = TextSecondaryGray)
                }
            },
            containerColor = headerDark
        )
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
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Mensajería UNCP",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Mi usuario: @${currentUser.username.ifBlank { currentUser.name.lowercase().replace(" ", "_") }}",
                        color = whatsappGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = "Agregar contacto por username",
                            tint = whatsappGreen
                        )
                    }
                    IconButton(onClick = onLogout) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Cerrar sesión",
                            tint = TextSecondaryGray
                        )
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
                text = "CHATS (${contacts.size})",
                color = whatsappGreen,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        // LISTA DE CONTACTOS ESTILO WHATSAPP
        if (contacts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Aún no tienes contactos en tu lista.",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Toca el botón superior para agregar a un contacto mediante su @usuario.",
                        color = TextSecondaryGray,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
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
                                if (contact.username.isNotBlank()) {
                                    Text(
                                        text = "@${contact.username}",
                                        color = whatsappGreen,
                                        fontSize = 12.sp
                                    )
                                }
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
}
