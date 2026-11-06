package com.example.mensajeria.domain.service

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Servicio de retroalimentación háptica (vibración) para personas invidentes.
 */
class VibrationService(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun vibrateStartListening() {
        // Vibración corta para indicar inicio de captura de micrófono
        vibrate(60)
    }

    /**
     * Vibración ultra-corta al arrastrar el dedo y pasar sobre un elemento nuevo.
     */
    fun vibrateHover() {
        vibrate(22)
    }

    /**
     * Vibración para toque en el lado izquierdo.
     */
    fun vibrateLeftTap() {
        vibrate(35)
    }

    /**
     * Vibración para toque en el lado derecho (doble pulso corto).
     */
    fun vibrateRightTap() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 25, 30, 35),
                    -1
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 25, 30, 35), -1)
        }
    }

    /**
     * Vibración distintiva al pulsar ambos lados simultáneamente para seleccionar.
     */
    fun vibrateBothSidesSelection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 50, 40, 50, 40, 70),
                    -1
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 50, 40, 50, 40, 70), -1)
        }
    }

    fun vibrateSuccess() {
        // Doble pulso indicando éxito o confirmación
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 40, 50, 60),
                    -1
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 40, 50, 60), -1)
        }
    }

    fun vibrateError() {
        // Pulso largo indicando error o cancelación
        vibrate(180)
    }

    private fun vibrate(durationMs: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(
                VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(durationMs)
        }
    }
}
