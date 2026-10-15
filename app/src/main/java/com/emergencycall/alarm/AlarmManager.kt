package com.emergencycall.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.emergencycall.EmergencyCallApplication
import com.emergencycall.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlarmManager(private val context: Context) {

    private val alarmPlayer = AlarmPlayer(context)
    private var isPlaying = false

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ALARM_REQUEST_CODE = 2001
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Emergency alarm channel - high priority, bypasses DND
        val emergencyChannel = NotificationChannel(
            EmergencyCallApplication.CHANNEL_EMERGENCY,
            "Emergency Alarm",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Critical emergency alarm notifications"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 1000, 500, 1000)
            setBypassDnd(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }

        // Connection status channel
        val connectionChannel = NotificationChannel(
            EmergencyCallApplication.CHANNEL_CONNECTION,
            "Connection Status",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Connection status updates"
        }

        notificationManager.createNotificationChannel(emergencyChannel)
        notificationManager.createNotificationChannel(connectionChannel)
    }

    fun startEmergencyAlarm(patientName: String, eventId: String) {
        if (isPlaying) return

        // Start foreground service for persistent alarm
        val serviceIntent = Intent(context, AlarmForegroundService::class.java).apply {
            action = AlarmForegroundService.ACTION_START_ALARM
            putExtra(AlarmForegroundService.EXTRA_PATIENT_NAME, patientName)
            putExtra(AlarmForegroundService.EXTRA_EVENT_ID, eventId)
        }
        ContextCompat.startForegroundService(context, serviceIntent)

        // Also start local playback for immediate feedback
        alarmPlayer.start()
        isPlaying = true
    }

    fun stopEmergencyAlarm() {
        if (!isPlaying) return

        val serviceIntent = Intent(context, AlarmForegroundService::class.java).apply {
            action = AlarmForegroundService.ACTION_STOP_ALARM
        }
        context.startService(serviceIntent)

        alarmPlayer.stop()
        isPlaying = false
    }

    fun createEmergencyNotification(patientName: String, eventId: String, pendingIntent: PendingIntent): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, EmergencyCallApplication.CHANNEL_EMERGENCY)
            .setSmallIcon(R.drawable.ic_emergency)
            .setContentTitle("🚨 EMERGENCY")
            .setContentText("$patientName needs help!")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(pendingIntent, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setColor(context.getColor(R.color.emergency_red))
            .setColorized(true)
            .setVibrate(longArrayOf(0, 1000, 500, 1000))
            .setDefaults(0) // We handle sound/vibration manually
            .setSound(null) // We handle sound manually
    }

    fun createConnectionNotification(title: String, message: String): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, EmergencyCallApplication.CHANNEL_CONNECTION)
            .setSmallIcon(R.drawable.ic_connection)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOngoing(true)
            .setAutoCancel(false)
    }

    fun isAlarmPlaying(): Boolean = isPlaying

    fun cleanup() {
        stopEmergencyAlarm()
    }
}

/**
 * AlarmPlayer - Handles alarm sound and vibration playback.
 * Extracted to avoid code duplication between AlarmManager and AlarmForegroundService.
 */
class AlarmPlayer(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var audioTrack: android.media.AudioTrack? = null
    private val vibrator: Vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    private var isPlaying = false

    fun start() {
        if (isPlaying) return
        playAlarmSound()
        startVibration()
        isPlaying = true
    }

    fun stop() {
        if (!isPlaying) return
        stopAlarmSound()
        stopVibration()
        isPlaying = false
    }

    private fun playAlarmSound() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                mediaPlayer = MediaPlayer.create(context, R.raw.emergency_alarm).also { mp ->
                    mp.isLooping = true
                    mp.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                            .build()
                    )
                    mp.setVolume(1.0f, 1.0f)
                    mp.start()
                }
            } catch (e: Exception) {
                // Fallback to system alarm sound
                try {
                    val alarmUri = android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
                    mediaPlayer = MediaPlayer.create(context, alarmUri).also { mp ->
                        mp.isLooping = true
                        mp.setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                                .build()
                        )
                        mp.start()
                    }
                } catch (e2: Exception) {
                    // Last resort - generate tone
                    generateTone()
                }
            }
        }
    }

    private fun generateTone() {
        // Generate a simple alarm tone programmatically if no sound file
        try {
            val sampleRate = 44100
            val durationMs = 1000
            val numSamples = sampleRate * durationMs / 1000
            val buffer = DoubleArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                // Two-tone alarm: 800Hz and 1000Hz alternating
                val freq = if ((i / (sampleRate / 4)) % 2 == 0) 800.0 else 1000.0
                buffer[i] = Math.sin(2 * Math.PI * freq * t) * 0.5
            }

            val pcmBuffer = ShortArray(numSamples)
            for (i in 0 until numSamples) {
                pcmBuffer[i] = (buffer[i] * 32767).toInt().toShort()
            }

            audioTrack = android.media.AudioTrack(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setFlags(android.media.AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                    .build(),
                android.media.AudioFormat.Builder()
                    .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
                pcmBuffer.size * 2,
                android.media.AudioTrack.MODE_STREAM,
                android.media.AudioManager.AUDIO_SESSION_ID_GENERATE
            )

            audioTrack!!.play()

            // Write in chunks to allow looping
            val chunkSize = 4096
            var offset = 0
            while (offset < pcmBuffer.size && !Thread.currentThread().isInterrupted) {
                val writeSize = minOf(chunkSize, pcmBuffer.size - offset)
                audioTrack!!.write(pcmBuffer, offset, writeSize, android.media.AudioTrack.WRITE_BLOCKING)
                offset += writeSize
                if (offset >= pcmBuffer.size) {
                    offset = 0 // Loop
                }
            }
        } catch (e: Exception) {
            // Silently fail if tone generation fails
        }
    }

    private fun stopAlarmSound() {
        mediaPlayer?.let {
            try {
                it.stop()
                it.release()
            } catch (e: Exception) {
                // Ignore
            }
            mediaPlayer = null
        }
        audioTrack?.let {
            try {
                it.stop()
                it.release()
            } catch (e: Exception) {
                // Ignore
            }
            audioTrack = null
        }
    }

    private fun startVibration() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createWaveform(
                longArrayOf(0, 1000, 500, 1000, 500),
                1 // Repeat index 1 (the 1000ms on)
            )
            vibrator.vibrate(effect, AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build())
        } else {
            vibrator.vibrate(longArrayOf(0, 1000, 500, 1000, 500), 1)
        }
    }

    private fun stopVibration() {
        vibrator.cancel()
    }
}