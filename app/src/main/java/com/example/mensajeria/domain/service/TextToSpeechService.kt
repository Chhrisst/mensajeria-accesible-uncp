package com.example.mensajeria.domain.service

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Servicio encapsulado de Síntesis de Voz (Text-to-Speech) adaptado para personas invidentes.
 * Soporta cola pendiente y garantiza que los callbacks se ejecuten en el hilo principal (Main Thread).
 */
class TextToSpeechService(
    context: Context,
    private val onInitSuccess: () -> Unit = {}
) : TextToSpeech.OnInitListener {

    private class UtteranceCallbacks(val onDone: () -> Unit, val onInterrupted: () -> Unit)

    // IMPORTANTE: estas propiedades deben declararse ANTES del bloque init. TextToSpeech puede invocar
    // onInit() de forma síncrona dentro de su constructor (p. ej. sin motor TTS instalado) y, si
    // mainHandler aún no estuviera inicializado, se produciría un NullPointerException.
    private val mainHandler = Handler(Looper.getMainLooper())
    private val callbacks = ConcurrentHashMap<String, UtteranceCallbacks>()
    private val utteranceCounter = AtomicLong(0)

    @Volatile private var isReady: Boolean = false
    @Volatile private var initFailed: Boolean = false
    private var pendingText: String? = null
    private var pendingCallbacks: UtteranceCallbacks? = null

    /**
     * Un único listener para todas las locuciones. Antes se reemplazaba en cada speak(), de modo que
     * cuando otra locución (exploración táctil, confirmación de contacto, etc.) interrumpía a la anterior,
     * el onDone original se perdía y el Asistente quedaba bloqueado en modo SPEAKING.
     */
    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(id: String?) {}

        override fun onDone(id: String?) {
            val cb = id?.let { callbacks.remove(it) } ?: return
            mainHandler.post { cb.onDone() }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(id: String?) {
            // Un error de síntesis no debe dejar el flujo colgado: se trata como finalización.
            val cb = id?.let { callbacks.remove(it) } ?: return
            mainHandler.post { cb.onDone() }
        }

        override fun onError(id: String?, errorCode: Int) {
            Log.w(TAG, "Error de síntesis ($errorCode) en $id")
            @Suppress("DEPRECATION")
            onError(id)
        }

        override fun onStop(id: String?, interrupted: Boolean) {
            // Locución detenida por stop() o descartada por un speak() con QUEUE_FLUSH.
            val cb = id?.let { callbacks.remove(it) } ?: return
            mainHandler.post { cb.onInterrupted() }
        }
    }

    private var tts: TextToSpeech? = null

    init {
        tts = try {
            TextToSpeech(context, this, "com.google.android.tts")
        } catch (_: Exception) {
            try {
                TextToSpeech(context, this)
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo crear el motor TTS: ${e.message}")
                null
            }
        }
        if (tts == null) markInitFailed()
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Log.e(TAG, "Inicialización del motor TTS fallida (status=$status)")
            markInitFailed()
            return
        }
        try {
            // Locales en español latinoamericano en orden de preferencia
            val latinLocales = listOf(
                Locale.forLanguageTag("es-PE"), // Perú (prioridad local)
                Locale.forLanguageTag("es-419"), // América Latina (estándar internacional)
                Locale.forLanguageTag("es-MX"), // México (máxima compatibilidad de voces latinas en Google TTS)
                Locale.forLanguageTag("es-US"), // Español EE.UU. (voz latina estándar en Android Google TTS)
                Locale.forLanguageTag("es-CO"), // Colombia
                Locale.forLanguageTag("es-AR"), // Argentina
                Locale.forLanguageTag("es-CL"), // Chile
                Locale("es")                    // Español genérico
            )

            // Probar el primer locale latino compatible
            var languageSet = false
            for (locale in latinLocales) {
                val res = tts?.setLanguage(locale)
                if (res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED) {
                    languageSet = true
                    Log.d(TAG, "Idioma TTS establecido a: ${locale.toLanguageTag()}")
                    break
                }
            }
            if (!languageSet) {
                Log.w(TAG, "No se encontró paquete de voz específico para variantes latinas, usando default de español")
                tts?.setLanguage(Locale("es"))
            }

            try {
                // Seleccionar explícitamente la mejor voz latina disponible en el dispositivo
                val spanishVoices = tts?.voices?.filter { v ->
                    v.locale.language.equals("es", ignoreCase = true) ||
                        v.name.startsWith("es-", ignoreCase = true) ||
                        v.name.startsWith("es_", ignoreCase = true)
                } ?: emptyList()

                val bestVoice = spanishVoices.maxByOrNull { calculateVoiceScore(it) }
                if (bestVoice != null && calculateVoiceScore(bestVoice) > 0) {
                    tts?.voice = bestVoice
                    tts?.language = bestVoice.locale
                    Log.i(TAG, "Voz TTS latina asignada: ${bestVoice.name} (${bestVoice.locale}, score=${calculateVoiceScore(bestVoice)})")
                } else {
                    Log.w(TAG, "No se encontró voz latina de alta fidelidad, usando configuración por defecto")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error seleccionando voz específica: ${e.message}")
            }

            tts?.setPitch(1.0f)
            tts?.setSpeechRate(0.97f) // Ritmo fluido, cálido y natural que respeta signos de puntuación
            tts?.setOnUtteranceProgressListener(progressListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error configurando el motor TTS: ${e.message}")
        }
        isReady = true
        mainHandler.post {
            onInitSuccess()

            // Reproducir cualquier anuncio pendiente que se haya solicitado mientras cargaba el motor
            val textToPlay = pendingText
            val cbToRun = pendingCallbacks
            pendingText = null
            pendingCallbacks = null
            if (!textToPlay.isNullOrBlank() && cbToRun != null) {
                speakInternal(textToPlay, TextToSpeech.QUEUE_FLUSH, cbToRun)
            }
        }
    }

    /**
     * Sin motor TTS (dispositivos sin voz instalada) nunca habrá onDone: se liberan los callbacks
     * pendientes para que los flujos que esperan el final de la locución (asistente, login) no se cuelguen.
     */
    private fun markInitFailed() {
        initFailed = true
        isReady = false
        mainHandler.post {
            val cb = pendingCallbacks
            pendingText = null
            pendingCallbacks = null
            cb?.onDone?.invoke()
        }
    }

    /**
     * Da formato al texto para máxima inteligibilidad y entonación natural en español:
     * - Antepone signos de apertura interrogativa (¿) y exclamativa (¡) para que el motor
     *   de síntesis eleve la curva de tono melódico en preguntas.
     * - Garantiza pausas claras en comas (,), puntos (.) y dos puntos (:).
     * - Pronuncia correctamente identificadores @ como 'arroba'.
     */
    fun formatForNaturalSpeech(text: String): String {
        var formatted = text.trim()
        if (formatted.isBlank()) return ""

        // Pronunciar @ como 'arroba'
        formatted = formatted.replace("@", " arroba ")

        // Separar cláusulas para asegurar entonación de preguntas en español
        val sentences = formatted.split(Regex("(?<=[.?!\\n])\\s+"))
        formatted = sentences.joinToString(" ") { sentence ->
            var s = sentence.trim()
            if (s.endsWith("?") && !s.startsWith("¿")) {
                s = "¿$s"
            } else if (s.endsWith("!") && !s.startsWith("¡")) {
                s = "¡$s"
            }
            s
        }

        // Respetar pausas claras en signos de puntuación
        formatted = formatted
            .replace(",", ", ")
            .replace(":", ": ")
            .replace(";", "; ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return formatted
    }

    /**
     * @param onDone se invoca al terminar la locución (también si el motor falla, para no colgar flujos).
     * @param onInterrupted se invoca si la locución es detenida o reemplazada por otra antes de terminar.
     */
    fun speak(
        text: String,
        queueMode: Int = TextToSpeech.QUEUE_FLUSH,
        onDone: () -> Unit = {},
        onInterrupted: () -> Unit = {}
    ) {
        val cb = UtteranceCallbacks(onDone, onInterrupted)
        if (text.isBlank()) {
            mainHandler.post { cb.onDone() }
            return
        }

        val formattedText = formatForNaturalSpeech(text)

        if (initFailed || (isReady && tts == null)) {
            mainHandler.post { cb.onDone() }
            return
        }

        // Si el motor aún se está conectando, guardamos el mensaje para decirlo apenas conecte
        if (!isReady) {
            val replaced = pendingCallbacks
            pendingText = formattedText
            pendingCallbacks = cb
            replaced?.let { mainHandler.post { it.onInterrupted() } }
            return
        }

        speakInternal(formattedText, queueMode, cb)
    }

    private fun speakInternal(formattedText: String, queueMode: Int, cb: UtteranceCallbacks) {
        val engine = tts
        if (engine == null) {
            mainHandler.post { cb.onDone() }
            return
        }
        val utteranceId = "msg_${utteranceCounter.incrementAndGet()}"
        callbacks[utteranceId] = cb
        val result = try {
            engine.speak(formattedText, queueMode, Bundle(), utteranceId)
        } catch (e: Exception) {
            Log.e(TAG, "Excepción en speak(): ${e.message}")
            TextToSpeech.ERROR
        }
        if (result == TextToSpeech.ERROR) {
            callbacks.remove(utteranceId)
            mainHandler.post { cb.onDone() }
        }
    }

    fun stop() {
        val pending = pendingCallbacks
        pendingText = null
        pendingCallbacks = null
        pending?.let { mainHandler.post { it.onInterrupted() } }
        try {
            tts?.stop()
        } catch (_: Exception) {}
    }

    fun shutdown() {
        pendingText = null
        pendingCallbacks = null
        callbacks.clear()
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
        isReady = false
    }

    /**
     * Calcula una puntuación para priorizar acentos latinoamericanos (Perú, 419 neutro, México, EE.UU., etc.)
     * y penalizar fuertemente las variantes peninsulares (España) para que nunca sean elegidas si hay alternativas latinas.
     */
    private fun calculateVoiceScore(voice: android.speech.tts.Voice): Int {
        val country = voice.locale.country.uppercase()
        val tag = voice.locale.toLanguageTag().lowercase()
        val name = voice.name.lowercase()

        // Si es de España (ES / es-es), penalizarlo fuertemente para evitar acento ibérico
        val isSpain = country == "ES" ||
            tag.startsWith("es-es") ||
            tag.contains("-es-") ||
            name.contains("es-es") ||
            name.contains("es_es") ||
            name.contains("spain")
        if (isSpain) {
            return -1000 + voice.quality
        }

        var score = when {
            country == "PE" || tag.contains("es-pe") || name.contains("es-pe") || name.contains("es_pe") -> 600 // Perú
            country == "419" || tag.contains("es-419") || name.contains("es-419") || name.contains("es_419") -> 550 // Español latino neutro
            country == "MX" || tag.contains("es-mx") || name.contains("es-mx") || name.contains("es_mx") -> 500 // México
            country == "US" || tag.contains("es-us") || name.contains("es-us") || name.contains("es_us") -> 450 // EE.UU. (voz latina por defecto de Google TTS)
            country == "CO" || tag.contains("es-co") || name.contains("es-co") || name.contains("es_co") -> 400 // Colombia
            country == "AR" || tag.contains("es-ar") || name.contains("es-ar") || name.contains("es_ar") -> 350 // Argentina
            country == "CL" || tag.contains("es-cl") || name.contains("es-cl") || name.contains("es_cl") -> 300 // Chile
            else -> 150
        }

        // Bonificación por calidad de la voz
        score += when (voice.quality) {
            android.speech.tts.Voice.QUALITY_VERY_HIGH -> 50
            android.speech.tts.Voice.QUALITY_HIGH -> 30
            android.speech.tts.Voice.QUALITY_NORMAL -> 15
            else -> 0
        }

        // Priorizar voces locales/instaladas offline para respuesta inmediata y consistente sin lag de red
        if (!voice.isNetworkConnectionRequired) {
            score += 25
        }

        return score
    }

    private companion object {
        const val TAG = "TextToSpeechService"
    }
}
