package com.example.mensajeria.domain.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri

/**
 * Servicio para reproducir sonidos accesibles, tonos agradables de confirmación y prompts de voz.
 */
class SoundNotificationService(private val context: Context) {

    companion object {
        @Volatile
        private var instance: SoundNotificationService? = null

        fun getInstance(context: Context): SoundNotificationService {
            return instance ?: synchronized(this) {
                instance ?: SoundNotificationService(context.applicationContext).also { instance = it }
            }
        }
    }

    private var toneGenerator: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 85)
    } catch (_: Exception) {
        try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Tono de confirmación auditiva instantáneo para cada botón o elemento interactivo pulsado.
     */
    fun playButtonClickSound() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 55)
        } catch (_: Exception) {
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                audioManager?.playSoundEffect(AudioManager.FX_KEY_CLICK)
            } catch (_: Exception) {}
        }
    }

    /**
     * Tono prompt de inicio de escucha por voz (para que el usuario sepa que el micrófono está activo).
     */
    fun playListeningPromptSound() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_PROMPT, 130)
        } catch (_: Exception) {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
            } catch (_: Exception) {}
        }
    }

    /**
     * Tono auditivo suave para el toque en el lado izquierdo.
     */
    fun playLeftTapSound() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 70)
        } catch (_: Exception) {}
    }

    /**
     * Tono auditivo más agudo para el toque en el lado derecho.
     */
    fun playRightTapSound() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 70)
        } catch (_: Exception) {}
    }

    /**
     * Tono armónico para confirmación o acceso exitoso.
     */
    fun playSuccessSound() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 200)
        } catch (_: Exception) {}
    }

    /**
     * Reproduce un sonido agradable cuando se recibe un mensaje nuevo.
     */
    fun playMessageReceivedSound() {
        try {
            val notificationUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone: Ringtone? = RingtoneManager.getRingtone(context, notificationUri)
            ringtone?.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ringtone?.play()
        } catch (_: Exception) {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_PROMPT, 150)
        }
    }
}
