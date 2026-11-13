package com.example.mensajeria.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mensajeria.data.model.Contact
import com.example.mensajeria.data.model.UserProfile
import com.example.mensajeria.ui.components.AccessibleButton
import com.example.mensajeria.ui.theme.BackgroundDark
import com.example.mensajeria.ui.theme.ErrorRed
import com.example.mensajeria.ui.theme.HighContrastYellow
import com.example.mensajeria.ui.theme.SurfaceDark
import com.example.mensajeria.ui.theme.SurfaceVariantDark
import com.example.mensajeria.ui.theme.TextDark
import com.example.mensajeria.ui.theme.TextPrimary
import com.example.mensajeria.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    currentUser: UserProfile?,
    contacts: List<Contact>,
    onSelectContact: (Contact) -> Unit,
    onLogout: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Messenger",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = HighContrastYellow,
                        modifier = Modifier.semantics { heading() }
                    )
                },
                actions = {
                    AccessibleButton(
                        text = "🚪 Salir",
                        contentDescription = "Cerrar sesión del sistema",
                        onClick = onLogout,
                        modifier = Modifier.padding(end = 8.dp),
                        backgroundColor = ErrorRed,
                        textColor = TextPrimary
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
                .padding(16.dp)
        ) {
            // Tarjeta de Identificación del Socio conectado
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
                    .semantics {
                        this.contentDescription = "Sesión iniciada como ${currentUser?.name ?: "Socio UNCP"}. ${currentUser?.role ?: ""}."
                    }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "👤 Sesión Activa",
                        color = HighContrastYellow,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = currentUser?.name ?: "Socio de la UNCP",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${currentUser?.role ?: "Socio"} • ${currentUser?.description ?: ""}",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }

            Text(
                text = "Contactos disponibles:",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 6.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(contacts, key = { it.id }) { contact ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 68.dp)
                            .clickable { onSelectContact(contact) }
                            .semantics {
                                this.role = Role.Button
                                this.contentDescription = "Contacto: ${contact.name}. ${contact.description}. Toque dos veces para abrir la conversación."
                            }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = contact.name,
                                color = HighContrastYellow,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = contact.description,
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Botón directo a la conversación principal de la UNCP
            AccessibleButton(
                text = "💬 ABRIR CHAT UNCP",
                contentDescription = "Abrir directamente la conversación de mensajería adaptada",
                onClick = {
                    contacts.firstOrNull()?.let { onSelectContact(it) }
                },
                backgroundColor = HighContrastYellow,
                textColor = TextDark
            )
        }
    }
}
