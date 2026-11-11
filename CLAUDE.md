# Proyecto Mensajería UNCP IA - Guía para Claude Code

Aplicación móvil de mensajería instantánea accesible asistida por Inteligencia Artificial para personas con discapacidad visual y usuarios estándar. Desarrollada para la Universidad Nacional del Centro del Perú (UNCP).

## 🛠️ Comandos de Compilación y Pruebas
- **Compilar APK de depuración:** `.\gradlew.bat assembleDebug`
- **Limpiar y compilar:** `.\gradlew.bat clean assembleDebug`
- **Ejecutar pruebas unitarias:** `.\gradlew.bat test`
- **Verificar entorno y JDK:** ejecutar `scripts\1_verificar_entorno.bat`

> **Nota de JDK:** Gradle 8.13 requiere JDK 17 o JDK 21. El script de diagnóstico prioriza automáticamente el JBR de Android Studio (`jbr/`).

---

## 🏛️ Arquitectura y Tecnologías
- **Lenguaje:** Kotlin 2.0.21
- **UI:** Jetpack Compose + Material 3 (soporte para modo estándar y modo accesible para baja visión/ceguera)
- **Min SDK:** 26 (Android 8.0) | **Target SDK:** 35 (Android 15)
- **Inyección de Dependencias / Patrón:** MVVM + Clean Architecture (`data`, `domain`, `ui`)
- **Backend HTTP/WebSocket:** Retrofit 2 + OkHttp 4
- **IA integrada en la app:** Google Gemini SDK (`GeminiDirectClient.kt`) para procesamiento de lenguaje natural y asistencia por voz de los usuarios con discapacidad visual.

---

## 🔒 Reglas Críticas de Seguridad y Configuración
1. **Credenciales y URLs del Servidor:**
   - La IP/URL del VPS y la API key de Gemini se gestionan **exclusivamente** en `local.properties`:
     - `BACKEND_BASE_URL="http://<IP_O_DOMINIO>:3000/"`
     - `GEMINI_API_KEY="AIza..."`
   - **NUNCA** escribir IPs fijas ni claves en archivos versionados (`.kt`, `.xml`, `.gradle`).
2. **IA en la App:**
   - La aplicación móvil utiliza **Google Gemini** para la interacción de voz y asistencia al usuario. No alterar la integración de Gemini con otros proveedores sin indicación explícita.
3. **Módulos de Accesibilidad:**
   - `TouchExploration.kt`: Manejo de exploración táctil para personas invidentes.
   - `VibrationService.kt`: Retroalimentación háptica adaptativa.
   - `VoiceAssistantManager.kt`: Reconocimiento y síntesis de voz continua.
