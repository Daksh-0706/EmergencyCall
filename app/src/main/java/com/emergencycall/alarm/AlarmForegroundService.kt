package com.emergencycall.alarm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.emergencycall.EmergencyCallApplication
import com.emergencycall.R
import com.emergencycall.alarm.FullScreenAlarmActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmForegroundService : Service() {

    companion object {
        const val ACTION_START_ALARM = "START_ALARM"
        const val ACTION_STOP_ALARM = "STOP_ALARM"
        const val EXTRA_PATIENT_NAME = "patient_name"
        const val EXTRA_EVENT_ID = "event_id"
    }

    private var alarmPlayer: AlarmPlayer? = null
    private var notificationManager: android.app.NotificationManager? = null
    private var currentPatientName = ""
    private var currentEventId = ""

    override fun onCreate() {
        super.onCreate()
        alarmPlayer = AlarmPlayer(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            ACTION_START_ALARM -> {
                currentPatientName = intent.getStringExtra(EXTRA_PATIENT_NAME) ?: "Patient"
                currentEventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: ""
                startForegroundService()
            }
            ACTION_STOP_ALARM -> {
                stopForegroundService()
            }
        }

        return START_STICKY
    }

    private fun startForegroundService() {
        val fullScreenIntent = Intent(this, FullScreenAlarmActivity::class.java).apply {
            putExtra(FullScreenAlarmActivity.EXTRA_PATIENT_NAME, currentPatientName)
            putExtra(FullScreenAlarmActivity.EXTRA_EVENT_ID, currentEventId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            AlarmManager.ALARM_REQUEST_CODE,
            fullScreenIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, EmergencyCallApplication.CHANNEL_EMERGENCY)
            .setSmallIcon(R.drawable.ic_emergency)
            .setContentTitle("🚨 EMERGENCY ACTIVE")
            .setContentText("$currentPatientName needs help - Tap to respond")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(pendingIntent, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setColor(getColor(R.color.emergency_red))
            .setColorized(true)
            .build()

        startForeground(AlarmManager.NOTIFICATION_ID, notification)

        // Start alarm sound and vibration
        alarmPlayer?.start()
    }

    private fun stopForegroundService() {
        alarmPlayer?.stop()
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        alarmPlayer?.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}