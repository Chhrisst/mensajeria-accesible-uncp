package com.example.mensajeria.domain.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri

/**
 * Servicio para reproducir sonidos accesibles y tonos agradables de notificación.
 */
class SoundNotificationService(private val context: Context) {

    private var toneGenerator: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
    } catch (_: Exception) {
        null
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
            // Fallback elegante usando ToneGenerator
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_PROMPT, 150)
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
     * Tono para inicio de escucha por voz.
     */
    fun playListeningPromptSound() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_PROMPT, 100)
        } catch (_: Exception) {}
    }
}
