# Mensajería Móvil Accesible asistida por Inteligencia Artificial (UNCP)

![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![Min SDK](https://img.shields.io/badge/Min%20SDK-24%20(Android%207.0)-informational?style=for-the-badge)
![Target SDK](https://img.shields.io/badge/Target%20SDK-36-success?style=for-the-badge)
![Institution](https://img.shields.io/badge/Universidad-UPN-orange?style=for-the-badge)
![Beneficiary](https://img.shields.io/badge/Organizaci%C3%B3n-UNCP-blue?style=for-the-badge)

Repositorio oficial del código fuente del aplicativo móvil desarrollado para el proyecto de tesis:
> **«Implementación de un aplicativo móvil de mensajería asistido por inteligencia artificial para mejorar la autonomía comunicativa de personas con discapacidad visual severa en la Unión Nacional de Ciegos del Perú, Lima, 2026»**

---

## 📌 Descripción del Proyecto

Las personas con discapacidad visual severa o ceguera total enfrentan barreras significativas al utilizar aplicativos móviles comerciales de mensajería (como WhatsApp), requiriendo de lectores de pantalla complejos (TalkBack) o dependiendo frecuentemente del apoyo de terceros para redactar, revisar y escuchar mensajes.

Esta solución tecnológica inclusiva propone un entorno de interacción adaptado donde los usuarios pueden **redactar, verificar auditivamente, enviar y escuchar mensajes de texto y notas de voz con plena autonomía**, utilizando reconocimiento de voz asistido por inteligencia artificial, síntesis de voz guiada (TTS) y respuestas inteligentes automáticas.

---

## ✨ Características Principales

### 1. Modo Accesible (Alto Contraste y Navegación Guiada)
* **Diseño ergonómico inclusivo:** Paleta de alto contraste optimizada para baja visión y ceguera.
* **Componentes táctiles de gran escala:** Botones de área amplia para facilitar la localización táctil en pantalla.
* **Confirmación háptica y sonora:** Vibración personalizada y sonidos de estado al enviar, recibir o grabar mensajes.

### 2. Autonomía por Comandos de Voz (Speech-to-Text & TTS)
* **Dictado por voz sin teclado en pantalla:** El usuario dicta su mensaje mediante el botón de voz central.
* **Verificación auditiva antes de enviar:** El motor Text-to-Speech lee el mensaje reconocido en voz alta antes de confirmar el envío para evitar errores no deseados.
* **Lectura automática de mensajes entrantes:** Lectura por voz de remitente y contenido con un solo toque.

### 3. Asistencia Inteligente (Smart Reply)
* Sugerencia automática de respuestas rápidas contextuales (*"Sí", "No", "¿Dónde estás?", "Ya voy en camino"*) seleccionables mediante toque amplio o comando de voz.

### 4. Modo Estándar Integrado
* Incluye un modo visual convencional en tema oscuro tipo mensajería instantánea para usuarios normovisuales o acompañantes.

---

## 🏗️ Arquitectura de Software

El aplicativo móvil sigue el patrón arquitectónico **MVVM (Model - View - ViewModel)** combinado con principios de **Clean Architecture**:

```text
com.example.mensajeria/
├── data/
│   ├── api/             # RetrofitClient, ApiService y modelos DTO para comunicación REST
│   ├── model/           # Entidades de dominio (Usuario, Contacto, Mensaje)
│   └── repository/      # AuthRepository y ChatRepository (con fallback local/offline)
├── domain/
│   └── service/         # Servicios nativos del dispositivo:
│       ├── SpeechService.kt             # Reconocimiento de voz (SpeechRecognizer)
│       ├── TextToSpeechService.kt       # Síntesis de voz auditiva (TextToSpeech)
│       ├── SmartReplyService.kt         # Lógica de sugerencias inteligentes
│       ├── SoundNotificationService.kt # Sonidos de retroalimentación
│       └── VibrationService.kt          # Retroalimentación háptica táctil
└── ui/
    ├── components/      # Botones accesibles de alto contraste y tarjetas táctiles
    ├── screens/         # Pantallas de la aplicación (Modo Accesible y Modo Estándar)
    ├── theme/           # Paletas de color accesible, tipografía y estilos Compose
    └── viewmodel/       # AuthViewModel y ChatViewModel
```

---

## 🛠️ Tecnologías y Librerías

* **Lenguaje:** Kotlin 2.0+
* **Framework UI:** Jetpack Compose con Material 3
* **Diseño Reactivo:** StateFlow, Coroutines & Lifecycle ViewModel
* **Comunicaciones REST:** Retrofit 2 & OkHttp 3
* **Serialización:** Gson / Kotlin Serialization
* **Accesibilidad y Multimedia:**
  * Android Native `SpeechRecognizer` API
  * Android Native `TextToSpeech` Engine
  * Android `Vibrator` & Audio Feedback Services

---

## 📱 Requisitos e Instalación

### Requisitos del Dispositivo:
* Sistema Operativo: Android 7.0 (API nivel 24) o superior.
* Conectividad a Internet (Wi-Fi o datos móviles).
* Micrófono y altavoz operativos.
* Motor de síntesis de voz instalado (Google Text-to-Speech recomendado en español).

### Compilación desde el Código Fuente:
1. Clonar el repositorio:
   ```bash
   git clone https://github.com/Chhrisst/mensajeria-accesible-uncp.git
   cd mensajeria-accesible-uncp
   ```
2. Abrir el proyecto en **Android Studio** (versión Ladybug / Iguana o superior).
3. Sincronizar las dependencias de Gradle.
4. Conectar un dispositivo físico Android o iniciar un emulador con API 24+.
5. Ejecutar la tarea de compilación:
   ```bash
   ./gradlew assembleDebug
   ```
   El APK generado se ubicará en: `app/build/outputs/apk/debug/app-debug.apk`.

---

## 👥 Equipo de Investigación y Créditos

**Universidad Privada del Norte (UPN)**  
Facultad de Ingeniería – Carrera de Ingeniería de Sistemas Computacionales  
Lima, Perú – 2026

* **Christian Oneill Beteta Jiménez** — Código: N00321812
* **Romell Jhon Rivera Montero** — Código: N00408112
* **Miguel Ángel David Retuerto Anaya** — Código: N00206693

**Asesor de Tesis:**  
* **Mg. Fernando Alex Sierra Liñan**

**Institución Beneficiaria y de Validación:**  
* **Unión Nacional de Ciegos del Perú (UNCP)** — Sede Central Breña, Lima.
