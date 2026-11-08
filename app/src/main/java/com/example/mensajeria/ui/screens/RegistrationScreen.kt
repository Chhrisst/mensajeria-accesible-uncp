package com.example.mensajeria.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Accessible
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mensajeria.data.model.RegisterMethod
import com.example.mensajeria.data.model.RegisterStep
import com.example.mensajeria.data.model.RegistrationForm
import com.example.mensajeria.data.model.UserProfile
import com.example.mensajeria.ui.components.AccessibleBackButton
import com.example.mensajeria.ui.components.AccessibleOptionCard
import com.example.mensajeria.ui.components.AccessiblePrimaryButton
import com.example.mensajeria.ui.components.AccessibleTextField
import com.example.mensajeria.ui.components.RegistrationStepper
import com.example.mensajeria.ui.theme.AccessibleYellow
import com.example.mensajeria.ui.theme.DarkCardBackground
import com.example.mensajeria.ui.theme.MensajeriaTheme
import com.example.mensajeria.ui.theme.PureBlack
import com.example.mensajeria.ui.theme.TextSecondaryGray
import com.example.mensajeria.ui.viewmodel.AuthViewModel

/**
 * Contenedor orquestador del Flujo de Registro Accesible en 4 Pasos.
 */
@Composable
fun RegistrationScreen(
    authViewModel: AuthViewModel,
    onBackToLogin: () -> Unit,
    onRegistrationFinished: (UserProfile) -> Unit
) {
    val step by authViewModel.registerStep.collectAsState()
    val form by authViewModel.registrationForm.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
    ) {
        when (step) {
            RegisterStep.METHOD_SELECTION -> {
                RegisterMethodScreen(
                    onBackClick = onBackToLogin,
                    onSelectMethod = { method ->
                        authViewModel.updateRegisterMethod(method)
                    },
                    onLoginClick = onBackToLogin
                )
            }
            RegisterStep.PERSONAL_DATA -> {
                RegisterStep1PersonalScreen(
                    initialName = form.fullName,
                    initialDni = form.dni,
                    initialBirthDate = form.birthDate,
                    onBackClick = { authViewModel.setRegisterStep(RegisterStep.METHOD_SELECTION) },
                    onNext = { name, dni, birth ->
                        authViewModel.updatePersonalData(name, dni, birth)
                    }
                )
            }
            RegisterStep.CONTACT_INFO -> {
                RegisterStep2ContactScreen(
                    initialPhone = form.phone,
                    initialEmail = form.email,
                    onBackClick = { authViewModel.setRegisterStep(RegisterStep.PERSONAL_DATA) },
                    onNext = { phone, email ->
                        authViewModel.updateContactData(phone, email)
                    }
                )
            }
            RegisterStep.DISABILITY_QUESTION -> {
                RegisterStep3DisabilityScreen(
                    hasDisability = form.hasDisability,
                    onBackClick = { authViewModel.setRegisterStep(RegisterStep.CONTACT_INFO) },
                    onNext = { hasDisability ->
                        authViewModel.updateDisabilitySelection(hasDisability)
                    }
                )
            }
            RegisterStep.VISUAL_CONDITION -> {
                RegisterStep3VisualConditionScreen(
                    selectedCondition = form.visualCondition,
                    onBackClick = { authViewModel.setRegisterStep(RegisterStep.DISABILITY_QUESTION) },
                    onNext = { condition ->
                        authViewModel.updateVisualCondition(condition)
                    }
                )
            }
            RegisterStep.SUMMARY_REVIEW -> {
                RegisterStep4SummaryScreen(
                    form = form,
                    onBackClick = {
                        if (form.hasDisability) {
                            authViewModel.setRegisterStep(RegisterStep.VISUAL_CONDITION)
                        } else {
                            authViewModel.setRegisterStep(RegisterStep.DISABILITY_QUESTION)
                        }
                    },
                    onConfirm = {
                        authViewModel.submitRegistration { user ->
                            onRegistrationFinished(user)
                        }
                    }
                )
            }
            RegisterStep.COMPLETED -> {
                RegisterCompletedScreen(
                    onGoToLogin = onBackToLogin
                )
            }
        }
    }
}

// Alias para compatibilidad
@Composable
fun FigmaRegistrationContainer(
    authViewModel: AuthViewModel,
    onBackToLogin: () -> Unit,
    onRegistrationFinished: (UserProfile) -> Unit
) = RegistrationScreen(authViewModel, onBackToLogin, onRegistrationFinished)

/**
 * Pantalla Selección de Método de Registro.
 */
@Composable
fun RegisterMethodScreen(
    onBackClick: () -> Unit,
    onSelectMethod: (RegisterMethod) -> Unit,
    onLoginClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Spacer(modifier = Modifier.height(10.dp))
            AccessibleBackButton(onClick = onBackClick)

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = "Crear cuenta",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = AccessibleYellow,
                modifier = Modifier.semantics { heading() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Elige cómo deseas registrarte en la aplicación.",
                fontSize = 16.sp,
                color = TextSecondaryGray,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(36.dp))

            AccessibleOptionCard(
                title = "Registrarme con correo electrónico",
                subtitle = "Usa tu dirección de email",
                leadingIcon = Icons.Default.Email,
                onClick = { onSelectMethod(RegisterMethod.EMAIL) }
            )

            Spacer(modifier = Modifier.height(18.dp))

            AccessibleOptionCard(
                title = "Registrarme con número de celular",
                subtitle = "Recibe un código SMS",
                leadingIcon = Icons.Default.Phone,
                onClick = { onSelectMethod(RegisterMethod.PHONE) }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "¿Ya tienes una cuenta? ",
                color = TextSecondaryGray,
                fontSize = 15.sp
            )
            Text(
                text = "Iniciar sesión",
                color = AccessibleYellow,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .clickable(onClick = onLoginClick)
                    .semantics {
                        this.role = Role.Button
                        this.contentDescription = "Ir a iniciar sesión con cuenta existente."
                    }
            )
        }
    }
}

/**
 * Paso 1: Datos Personales.
 */
@Composable
fun RegisterStep1PersonalScreen(
    initialName: String,
    initialDni: String,
    initialBirthDate: String,
    onBackClick: () -> Unit,
    onNext: (String, String, String) -> Unit
) {
    var fullName by remember { mutableStateOf(initialName) }
    var dni by remember { mutableStateOf(initialDni) }
    var birthDate by remember { mutableStateOf(initialBirthDate) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Spacer(modifier = Modifier.height(10.dp))
            AccessibleBackButton(onClick = onBackClick)

            Spacer(modifier = Modifier.height(16.dp))
            RegistrationStepper(currentStep = 1)

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Datos personales",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = AccessibleYellow,
                modifier = Modifier.semantics { heading() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Cuéntanos un poco sobre ti para poder brindarte una mejor experiencia.",
                fontSize = 15.sp,
                color = TextSecondaryGray,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(28.dp))

            AccessibleTextField(
                value = fullName,
                onValueChange = { fullName = it },
                placeholder = "Nombre completo",
                leadingIcon = Icons.Default.Person
            )

            Spacer(modifier = Modifier.height(16.dp))

            AccessibleTextField(
                value = dni,
                onValueChange = { dni = it },
                placeholder = "DNI",
                leadingIcon = Icons.Default.Badge,
                keyboardType = KeyboardType.Number
            )

            Spacer(modifier = Modifier.height(16.dp))

            AccessibleTextField(
                value = birthDate,
                onValueChange = { birthDate = it },
                placeholder = "Fecha de nacimiento",
                leadingIcon = Icons.Default.CalendarMonth
            )
        }

        AccessiblePrimaryButton(
            text = "Siguiente",
            onClick = { onNext(fullName, dni, birthDate) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )
    }
}

/**
 * Paso 2: Información de Contacto.
 */
@Composable
fun RegisterStep2ContactScreen(
    initialPhone: String,
    initialEmail: String,
    onBackClick: () -> Unit,
    onNext: (String, String) -> Unit
) {
    var phone by remember { mutableStateOf(initialPhone.ifBlank { "987 654 321" }) }
    var email by remember { mutableStateOf(initialEmail.ifBlank { "juan@gmail.com" }) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Spacer(modifier = Modifier.height(10.dp))
            AccessibleBackButton(onClick = onBackClick)

            Spacer(modifier = Modifier.height(16.dp))
            RegistrationStepper(currentStep = 2)

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Información de contacto",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = AccessibleYellow,
                modifier = Modifier.semantics { heading() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Necesitamos esta información para poder comunicarnos contigo.",
                fontSize = 15.sp,
                color = TextSecondaryGray,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(28.dp))

            AccessibleTextField(
                value = phone,
                onValueChange = { phone = it },
                placeholder = "Número de celular",
                leadingIcon = Icons.Default.Phone,
                keyboardType = KeyboardType.Phone
            )

            Spacer(modifier = Modifier.height(16.dp))

            AccessibleTextField(
                value = email,
                onValueChange = { email = it },
                placeholder = "Correo electrónico",
                leadingIcon = Icons.Default.Email,
                keyboardType = KeyboardType.Email
            )
        }

        AccessiblePrimaryButton(
            text = "Siguiente",
            onClick = { onNext(phone, email) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )
    }
}

/**
 * Paso 3: Pregunta de Discapacidad.
 */
@Composable
fun RegisterStep3DisabilityScreen(
    hasDisability: Boolean,
    onBackClick: () -> Unit,
    onNext: (Boolean) -> Unit
) {
    var selectedYes by remember { mutableStateOf(hasDisability) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Spacer(modifier = Modifier.height(10.dp))
            AccessibleBackButton(onClick = onBackClick)

            Spacer(modifier = Modifier.height(16.dp))
            RegistrationStepper(currentStep = 3)

            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(AccessibleYellow)
                        .border(3.dp, Color(0xFF262010), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Accessible,
                        contentDescription = null,
                        tint = PureBlack,
                        modifier = Modifier.size(46.dp)
                    )
                }
            }

            Text(
                text = "¿Eres una persona con\ndiscapacidad?",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = AccessibleYellow,
                textAlign = TextAlign.Center,
                lineHeight = 32.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { heading() }
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Queremos brindarte una mejor experiencia. Por favor, selecciona la opción que corresponda a tu situación.",
                fontSize = 15.sp,
                color = TextSecondaryGray,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(28.dp))

            AccessibleOptionCard(
                title = "Sí, tengo discapacidad visual",
                leadingIcon = Icons.Default.Visibility,
                isSelected = selectedYes,
                showRadio = true,
                onClick = { selectedYes = true }
            )

            Spacer(modifier = Modifier.height(16.dp))

            AccessibleOptionCard(
                title = "No, no tengo discapacidad",
                leadingIcon = Icons.Default.Person,
                isSelected = !selectedYes,
                showRadio = true,
                onClick = { selectedYes = false }
            )
        }

        AccessiblePrimaryButton(
            text = "Siguiente",
            onClick = { onNext(selectedYes) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )
    }
}

/**
 * Paso 3 Sub-pantalla: Tipo de Discapacidad Visual.
 */
@Composable
fun RegisterStep3VisualConditionScreen(
    selectedCondition: String,
    onBackClick: () -> Unit,
    onNext: (String) -> Unit
) {
    var selected by remember { mutableStateOf(selectedCondition) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Spacer(modifier = Modifier.height(10.dp))
            AccessibleBackButton(onClick = onBackClick)

            Spacer(modifier = Modifier.height(36.dp))

            Text(
                text = "¿Qué tipo de\ndiscapacidad visual\ntienes?",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = AccessibleYellow,
                lineHeight = 34.sp,
                modifier = Modifier.semantics { heading() }
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Selecciona la opción que mejor describa tu situación.",
                fontSize = 15.sp,
                color = TextSecondaryGray,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            AccessibleOptionCard(
                title = "Ceguera total",
                leadingIcon = Icons.Default.VisibilityOff,
                isSelected = selected == "Ceguera total",
                showRadio = true,
                onClick = { selected = "Ceguera total" }
            )

            Spacer(modifier = Modifier.height(16.dp))

            AccessibleOptionCard(
                title = "Baja visión",
                leadingIcon = Icons.Default.Visibility,
                isSelected = selected == "Baja visión",
                showRadio = true,
                onClick = { selected = "Baja visión" }
            )

            Spacer(modifier = Modifier.height(16.dp))

            AccessibleOptionCard(
                title = "Otra condición visual",
                leadingIcon = Icons.Default.Info,
                isSelected = selected == "Otra condición visual",
                showRadio = true,
                onClick = { selected = "Otra condición visual" }
            )
        }

        AccessiblePrimaryButton(
            text = "Siguiente",
            onClick = { onNext(selected) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )
    }
}

/**
 * Paso 4: Revisa tu información.
 */
@Composable
fun RegisterStep4SummaryScreen(
    form: RegistrationForm,
    onBackClick: () -> Unit,
    onConfirm: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Spacer(modifier = Modifier.height(10.dp))
            AccessibleBackButton(onClick = onBackClick)

            Spacer(modifier = Modifier.height(16.dp))
            RegistrationStepper(currentStep = 4)

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .border(2.dp, AccessibleYellow, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Assignment,
                        contentDescription = null,
                        tint = AccessibleYellow,
                        modifier = Modifier.size(42.dp)
                    )
                }
            }

            Text(
                text = "Revisa tu información",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = AccessibleYellow,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { heading() }
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Antes de continuar, verifica que tus datos sean correctos.",
                fontSize = 15.sp,
                color = TextSecondaryGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardBackground),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    SummaryRow(
                        icon = Icons.Default.Person,
                        label = "Nombre completo",
                        value = form.fullName.ifBlank { "No especificado" }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    SummaryRow(
                        icon = Icons.Default.Phone,
                        label = "Celular",
                        value = form.phone.ifBlank { "No especificado" }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    SummaryRow(
                        icon = Icons.Default.Email,
                        label = "Correo electrónico",
                        value = form.email.ifBlank { "No especificado" }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    SummaryRow(
                        icon = Icons.Default.Visibility,
                        label = "Discapacidad visual",
                        value = if (form.hasDisability) form.visualCondition else "Sin discapacidad"
                    )
                }
            }
        }

        AccessiblePrimaryButton(
            text = "Confirmar",
            onClick = onConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )
    }
}

@Composable
private fun SummaryRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF231E12)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AccessibleYellow,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = label,
                color = TextSecondaryGray,
                fontSize = 13.sp
            )
            Text(
                text = value,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Pantalla Final: Registro completado exitosamente.
 */
@Composable
fun RegisterCompletedScreen(
    onGoToLogin: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 100.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF141414))
                    .border(3.dp, AccessibleYellow, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(AccessibleYellow),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = PureBlack,
                        modifier = Modifier.size(58.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(44.dp))

            Text(
                text = "¡Registro completado!",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = AccessibleYellow,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Tu cuenta ha sido creada correctamente. Ahora puedes iniciar sesión y comenzar a usar la aplicación.",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        AccessiblePrimaryButton(
            text = "Ir a iniciar sesión",
            onClick = onGoToLogin,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
        )
    }
}

@Preview(name = "Registro - Paso 1: Método", showBackground = true, showSystemUi = true)
@Composable
fun Step1RegisterMethodPreview() {
    MensajeriaTheme {
        RegisterMethodScreen(
            onBackClick = {},
            onSelectMethod = {},
            onLoginClick = {}
        )
    }
}

@Preview(name = "Registro - Paso 2: Datos Personales", showBackground = true, showSystemUi = true)
@Composable
fun Step2PersonalDataPreview() {
    MensajeriaTheme {
        RegisterStep1PersonalScreen(
            initialName = "Juan Pérez",
            initialDni = "74567890",
            initialBirthDate = "15/05/1998",
            onBackClick = {},
            onNext = { _, _, _ -> }
        )
    }
}

@Preview(name = "Registro - Paso 4: Discapacidad Visual", showBackground = true, showSystemUi = true)
@Composable
fun Step4VisualConditionPreview() {
    MensajeriaTheme {
        RegisterStep3VisualConditionScreen(
            selectedCondition = "Baja visión",
            onBackClick = {},
            onNext = {}
        )
    }
}

@Preview(name = "Registro - Éxito", showBackground = true, showSystemUi = true)
@Composable
fun RegistrationCompletedPreview() {
    MensajeriaTheme {
        RegisterCompletedScreen(
            onGoToLogin = {}
        )
    }
}
