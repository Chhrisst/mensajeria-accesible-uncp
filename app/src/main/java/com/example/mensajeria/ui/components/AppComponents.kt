package com.example.mensajeria.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import com.example.mensajeria.domain.service.SoundNotificationService
import com.example.mensajeria.domain.service.VibrationService
import com.example.mensajeria.ui.theme.AccessibleYellow
import com.example.mensajeria.ui.theme.DarkCardBackground
import com.example.mensajeria.ui.theme.PureBlack
import com.example.mensajeria.ui.theme.TextPlaceholderGray
import com.example.mensajeria.ui.theme.TextSecondaryGray

/**
 * Emite sonido de confirmación auditivo y vibración háptica instantánea en cada interacción con botones.
 */
fun playAccessibleClickFeedback(context: Context) {
    try {
        SoundNotificationService.getInstance(context).playButtonClickSound()
        VibrationService.getInstance(context).vibrateClick()
    } catch (_: Exception) {}
}

/**
 * Logo interactivo del Asistente de Voz:
 * Círculo central con micrófono, aro exterior y ondas acústicas animadas.
 */
@Composable
fun VoiceAssistantLogo(
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    isPulsing: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by if (isPulsing) {
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1100),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseScale"
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .exploreTarget(id = "voice_assistant_logo", label = "Micrófono")
            .then(
                if (onClick != null) {
                    Modifier
                        .clickable {
                            playAccessibleClickFeedback(context)
                            onClick()
                        }
                        .semantics {
                            this.role = Role.Button
                            this.contentDescription = "Asistente de voz. Presione para activar."
                        }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        // Corchete / barra acústica izquierda
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(width = 4.dp, height = size * 0.45f)
                .background(AccessibleYellow, RoundedCornerShape(2.dp))
        )

        // Corchete / barra acústica derecha
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(width = 4.dp, height = size * 0.45f)
                .background(AccessibleYellow, RoundedCornerShape(2.dp))
        )

        // Aro exterior punteado / fino
        Box(
            modifier = Modifier
                .size(size * 0.82f)
                .border(3.dp, AccessibleYellow, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Círculo central amarillo
            Box(
                modifier = Modifier
                    .size(size * 0.62f)
                    .clip(CircleShape)
                    .background(AccessibleYellow),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = PureBlack,
                    modifier = Modifier.size(size * 0.34f)
                )
            }
        }
    }
}

/**
 * Botón accesible de retroceso (Círculo oscuro con flecha amarilla).
 */
@Composable
fun AccessibleBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    exploreLabel: String = "Volver"
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .size(48.dp)
            .exploreTarget(id = "back_btn_$exploreLabel", label = exploreLabel)
            .clip(CircleShape)
            .background(Color(0xFF1C1C1E))
            .clickable {
                playAccessibleClickFeedback(context)
                onClick()
            }
            .semantics {
                this.role = Role.Button
                this.contentDescription = exploreLabel
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            tint = AccessibleYellow,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Campo de texto accesible de alto contraste:
 * Borde redondeado amarillo, ícono izquierdo amarillo, texto de alta visibilidad.
 */
@Composable
fun AccessibleTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    var passwordVisible by remember { mutableStateOf(!isPassword) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 58.dp),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color(0xFF0D0D0D),
            unfocusedContainerColor = Color(0xFF0D0D0D),
            focusedBorderColor = AccessibleYellow,
            unfocusedBorderColor = AccessibleYellow,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = AccessibleYellow
        ),
        placeholder = {
            Text(
                text = placeholder,
                color = TextPlaceholderGray,
                fontSize = 16.sp
            )
        },
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = AccessibleYellow,
                modifier = Modifier.size(22.dp)
            )
        },
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña",
                        tint = TextSecondaryGray
                    )
                }
            }
        } else null,
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true
    )
}

/**
 * Botón Principal accesible (Amarillo sólido, texto negro en negrita, altura mínima 56dp).
 */
@Composable
fun AccessiblePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null
) {
    val context = LocalContext.current
    Button(
        onClick = {
            playAccessibleClickFeedback(context)
            onClick()
        },
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = AccessibleYellow,
            contentColor = PureBlack,
            disabledContainerColor = Color(0xFF4A3E1B),
            disabledContentColor = Color.DarkGray
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .exploreTarget(id = "primary_btn_$text", label = text)
            .semantics {
                this.role = Role.Button
                this.contentDescription = text
            }
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = PureBlack,
                modifier = Modifier
                    .size(22.dp)
                    .padding(end = 6.dp)
            )
        }
        Text(
            text = text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = PureBlack
        )
    }
}

/**
 * Botón con Contorno accesible (Borde amarillo, texto amarillo, fondo transparente).
 */
@Composable
fun AccessibleOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null
) {
    val context = LocalContext.current
    Button(
        onClick = {
            playAccessibleClickFeedback(context)
            onClick()
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = AccessibleYellow
        ),
        border = BorderStroke(1.5.dp, AccessibleYellow),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .exploreTarget(id = "primary_btn_$text", label = text)
            .semantics {
                this.role = Role.Button
                this.contentDescription = text
            }
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = AccessibleYellow,
                modifier = Modifier
                    .size(22.dp)
                    .padding(end = 6.dp)
            )
        }
        Text(
            text = text,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = AccessibleYellow
        )
    }
}

/**
 * Tarjeta de Selección accesible (para método de registro o condición visual).
 */
@Composable
fun AccessibleOptionCard(
    title: String,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    isSelected: Boolean = false,
    showRadio: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cardBg = if (isSelected && showRadio) AccessibleYellow else DarkCardBackground
    val textColor = if (isSelected && showRadio) PureBlack else Color.White
    val subtextColor = if (isSelected && showRadio) Color(0xFF262626) else TextSecondaryGray
    val borderColor = AccessibleYellow

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.5.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 72.dp)
            .exploreTarget(id = "option_card_$title", label = title)
            .clickable {
                playAccessibleClickFeedback(context)
                onClick()
            }
            .semantics {
                this.role = Role.Button
                this.contentDescription = "$title. ${subtitle ?: ""}. ${if (isSelected) "Seleccionado" else "No seleccionado"}"
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected && showRadio) Color(0xFFD49C16) else Color(0xFF231E12)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = if (isSelected && showRadio) PureBlack else AccessibleYellow,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = textColor,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = subtextColor,
                        fontSize = 14.sp
                    )
                }
            }

            if (showRadio) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .border(
                            width = 2.dp,
                            color = if (isSelected) (if (cardBg == AccessibleYellow) PureBlack else AccessibleYellow) else AccessibleYellow,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(if (cardBg == AccessibleYellow) PureBlack else AccessibleYellow)
                        )
                    }
                }
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    tint = AccessibleYellow,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Stepper de Progreso (Pers. -> Contacto -> Discap. -> Finalizar).
 */
@Composable
fun RegistrationStepper(
    currentStep: Int,
    totalSteps: Int = 4,
    labels: List<String> = listOf("Pers.", "Contacto", "Discap.", "Finalizar"),
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            for (step in 1..totalSteps) {
                val isActive = step == currentStep
                val isCompleted = step < currentStep

                val circleBg = when {
                    isActive -> AccessibleYellow
                    isCompleted -> Color(0xFF4A3E1B)
                    else -> Color(0xFF1C1C1E)
                }
                val textNumColor = when {
                    isActive -> PureBlack
                    isCompleted -> AccessibleYellow
                    else -> TextSecondaryGray
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(circleBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$step",
                        color = textNumColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                if (step < totalSteps) {
                    val lineColor = if (step < currentStep) AccessibleYellow else Color(0xFF2C2C2E)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(2.5.dp)
                            .padding(horizontal = 4.dp)
                            .background(lineColor)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            labels.forEachIndexed { index, label ->
                val step = index + 1
                val isCurrentOrCompleted = step <= currentStep
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = if (step == currentStep) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrentOrCompleted) AccessibleYellow else TextSecondaryGray
                )
            }
        }
    }
}

/**
 * Indicador de puntos grandes del PIN de 4 toques/dígitos (discreto y seguro).
 */
@Composable
fun PinDotIndicator(
    enteredLength: Int,
    maxDigits: Int = 4,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 1..maxDigits) {
            val isFilled = i <= enteredLength
            Box(
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (isFilled) AccessibleYellow else Color(0xFF2C2C2E))
                    .border(2.dp, AccessibleYellow, CircleShape)
            )
        }
    }
}

// ==========================================
// Alias para compatibilidad
// ==========================================
@Composable
fun FigmaBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) = AccessibleBackButton(onClick, modifier)

@Composable
fun FigmaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
) = AccessibleTextField(value, onValueChange, placeholder, leadingIcon, modifier, isPassword, keyboardType)

@Composable
fun FigmaPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null
) = AccessiblePrimaryButton(text, onClick, modifier, enabled, leadingIcon)

@Composable
fun FigmaOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null
) = AccessibleOutlinedButton(text, onClick, modifier, leadingIcon)

@Composable
fun FigmaOptionCard(
    title: String,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    isSelected: Boolean = false,
    showRadio: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) = AccessibleOptionCard(title, subtitle, leadingIcon, isSelected, showRadio, onClick, modifier)

@Composable
fun FigmaStepper(
    currentStep: Int,
    totalSteps: Int = 4,
    labels: List<String> = listOf("Pers.", "Contacto", "Discap.", "Finalizar"),
    modifier: Modifier = Modifier
) = RegistrationStepper(currentStep, totalSteps, labels, modifier)
