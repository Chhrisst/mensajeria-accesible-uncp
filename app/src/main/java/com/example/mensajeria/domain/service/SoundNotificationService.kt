package com.example.mensajeria.domain.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import kotlin.math.PI
import kotlin.math.sin

/**
 * Servicio para reproducir sonidos accesibles armónicos, agradables y no robóticos.
 * Utiliza ondas sinusoidales con envolvente suave fade-in/fade-out mediante AudioTrack.
 */
class SoundNotificationService(private val context: Context) {

    companion object {
        private const val SAMPLE_RATE = 24000

        @Volatile
        private var instance: SoundNotificationService? = null

        fun getInstance(context: Context): SoundNotificationService {
            return instance ?: synchronized(this) {
                instance ?: SoundNotificationService(context.applicationContext).also { instance = it }
            }
        }
    }

    private var toneGenerator: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 65)
    } catch (_: Exception) {
        null
    }

    /**
     * Genera y reproduce una secuencia melódica de ondas sinusoidales suaves.
     */
    private fun playHarmonicNotes(notes: List<Pair<Double, Int>>, gapMs: Int = 18, volume: Double = 0.28) {
        Thread {
            try {
                val totalSamples = notes.sumOf { (_, ms) -> (SAMPLE_RATE * (ms + gapMs)) / 1000 }
                if (totalSamples <= 0) return@Thread

                val pcm = ShortArray(totalSamples)
                var offset = 0

                for ((freq, durationMs) in notes) {
                    val noteSamples = (SAMPLE_RATE * durationMs) / 1000
                    val fadeSamples = (noteSamples * 0.22).toInt().coerceAtLeast(1)

                    for (i in 0 until noteSamples) {
                        val t = i.toDouble() / SAMPLE_RATE
                        val envelope = when {
                            i < fadeSamples -> sin((i.toDouble() / fadeSamples) * (PI / 2.0))
                            i > noteSamples - fadeSamples ->
                                sin(((noteSamples - i).toDouble() / fadeSamples) * (PI / 2.0))
                            else -> 1.0
                        }
                        // Tono fundamental + armónico suave a la octava (0.18) para timbre cálido tipo marimba/cristal
                        val wave = sin(2.0 * PI * freq * t) + 0.18 * sin(2.0 * PI * (freq * 2.0) * t)
                        val sampleValue = (wave * volume * envelope * Short.MAX_VALUE / 1.18).toInt()
                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        if (offset < totalSamples) {
                            pcm[offset++] = sampleValue.toShort()
                        }
                    }
                    val gapSamples = (SAMPLE_RATE * gapMs) / 1000
                    offset = (offset + gapSamples).coerceAtMost(totalSamples)
                }

                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                val format = AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()

                val track = AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(format)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build()

                track.write(pcm, 0, pcm.size)
                track.play()
                val playMs = notes.sumOf { it.second + gapMs }.toLong() + 120L
                Thread.sleep(playMs)
                track.stop()
                track.release()
            } catch (_: Exception) {
                // Entornos sin hardware de audio (unit tests) ignoran silenciosamente
            }
        }.start()
    }

    /**
     * Tono de confirmación auditiva sutil y agradable para cada botón pulsado.
     */
    fun playButtonClickSound() {
        try {
            playHarmonicNotes(listOf(660.0 to 35), gapMs = 0, volume = 0.20)
        } catch (_: Exception) {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 35)
            } catch (_: Exception) {}
        }
    }

    /**
     * Tono agradable de doble nota ascendente cálida (Re5 -> La5) que indica hablar después del tono.
     */
    fun playListeningPromptSound() {
        try {
            playHarmonicNotes(
                notes = listOf(587.33 to 85, 880.0 to 105),
                gapMs = 15,
                volume = 0.30
            )
        } catch (_: Exception) {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_PROMPT, 120)
            } catch (_: Exception) {}
        }
    }

    /**
     * Tono auditivo suave para el toque en el lado izquierdo.
     */
    fun playLeftTapSound() {
        try {
            playHarmonicNotes(listOf(440.0 to 60), gapMs = 0, volume = 0.22)
        } catch (_: Exception) {}
    }

    /**
     * Tono auditivo más agudo y suave para el toque en el lado derecho.
     */
    fun playRightTapSound() {
        try {
            playHarmonicNotes(listOf(554.37 to 60), gapMs = 0, volume = 0.22)
        } catch (_: Exception) {}
    }

    /**
     * Acorde mayor suave ascendente (Do5 -> Mi5 -> Sol5) para confirmación o acceso exitoso.
     */
    fun playSuccessSound() {
        try {
            playHarmonicNotes(
                notes = listOf(523.25 to 70, 659.25 to 70, 783.99 to 110),
                gapMs = 12,
                volume = 0.28
            )
        } catch (_: Exception) {}
    }

    /**
     * Doble tono grave suave descendente para avisar un error sin estridencia.
     */
    fun playErrorSound() {
        try {
            playHarmonicNotes(
                notes = listOf(392.0 to 90, 311.13 to 130),
                gapMs = 20,
                volume = 0.26
            )
        } catch (_: Exception) {}
    }

    /**
     * Tono agradable de llamada saliente (440 Hz + 480 Hz modulado suave).
     */
    fun playCallRingbackTone() {
        Thread {
            try {
                val durationMs = 450
                val totalSamples = (SAMPLE_RATE * durationMs) / 1000
                val pcm = ShortArray(totalSamples)
                val fadeSamples = (totalSamples * 0.15).toInt().coerceAtLeast(1)

                for (i in 0 until totalSamples) {
                    val t = i.toDouble() / SAMPLE_RATE
                    val env = when {
                        i < fadeSamples -> sin((i.toDouble() / fadeSamples) * (PI / 2.0))
                        i > totalSamples - fadeSamples ->
                            sin(((totalSamples - i).toDouble() / fadeSamples) * (PI / 2.0))
                        else -> 1.0
                    }
                    val wave = 0.5 * sin(2.0 * PI * 440.0 * t) + 0.5 * sin(2.0 * PI * 480.0 * t)
                    pcm[i] = (wave * 0.22 * env * Short.MAX_VALUE).toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build()

                track.write(pcm, 0, pcm.size)
                track.play()
                Thread.sleep(durationMs.toLong() + 100L)
                track.stop()
                track.release()
            } catch (_: Exception) {}
        }.start()
    }

    /**
     * Reproduce un acorde melódico cálido para anunciar una llamada de voz entrante.
     */
    fun playIncomingCallRingtone() {
        try {
            playHarmonicNotes(
                notes = listOf(523.25 to 120, 659.25 to 120, 783.99 to 150, 659.25 to 160),
                gapMs = 25,
                volume = 0.34
            )
        } catch (_: Exception) {
            playCallRingbackTone()
        }
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
            playListeningPromptSound()
        }
    }
}
