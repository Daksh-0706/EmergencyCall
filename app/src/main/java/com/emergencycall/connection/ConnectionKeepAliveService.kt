package com.emergencycall.connection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.emergencycall.EmergencyCallApplication
import com.emergencycall.MainActivity
import com.emergencycall.R
import com.emergencycall.data.ConnectionState
import com.emergencycall.data.EmergencyStateManager
import com.emergencycall.data.UserRole
import com.emergencycall.networking.NearbyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the Nearby message link alive while the app is backgrounded (screen off, or the
 * user swiped it into recents — NOT force-stopped).
 *
 * Without a foreground service, Android can kill this app's process under memory pressure or
 * put it into Doze, both of which pause BLE and drop the pairing. This service holds the
 * process in foreground (`START_STICKY`, keeps running on task remove), shows a persistent
 * low-priority status notification, and periodically nudges [NearbyManager.maybeStartReconnect]
 * so the role channel (patient discovery / caregiver advertising) is re-asserted whenever the
 * link is not fully connected. All the real reconnection logic already lives in NearbyManager.
 */
class ConnectionKeepAliveService : Service() {

    private val stateManager = EmergencyStateManager.getInstance()
    private val keepAliveScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var statusScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var notificationManager: NotificationManager? = null

    @Volatile
    private var started = false

    companion object {
        private const val ACTION_START = "com.emergencycall.connection.START"
        private const val ACTION_STOP = "com.emergencycall.connection.STOP"
        const val NOTIFICATION_ID = 2002

        fun start(context: Context) {
            val intent = Intent(context, ConnectionKeepAliveService::class.java)
                .setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ConnectionKeepAliveService::class.java)
                .setAction(ACTION_STOP)
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!started) {
            started = true
            startForeground(NOTIFICATION_ID, buildConnectionNotification())
            startStatusCollector()
            startKeepAliveLoop()
        }
        return START_STICKY
    }

    /** Re-assert the role channel every ~30 s; NearbyManager decides if it's actually needed. */
    private fun startKeepAliveLoop() {
        keepAliveScope.launch {
            val nearbyManager = NearbyManager.getInstance(this@ConnectionKeepAliveService)
            while (true) {
                delay(30_000)
                if (!started) break
                val state = stateManager.state.value
                val hasKnown = when (state.userRole) {
                    UserRole.PATIENT -> state.pairedDevices.isNotEmpty()
                    UserRole.CAREGIVER -> state.pairedDevice != null
                    else -> false
                }
                if (state.userRole != UserRole.NONE && hasKnown) {
                    // Idempotent: no-ops when everything is connected or a reconnect loop is
                    // already active, otherwise restarts discovery/advertising as needed.
                    nearbyManager.maybeStartReconnect()
                }
            }
        }
    }

    /** Keep the persistent notification in sync with live connection/role state. */
    private fun startStatusCollector() {
        statusScope.launch {
            stateManager.state
                .map { Triple(it.userRole, it.connectionState, it.connectedCount) }
                .distinctUntilChanged()
                .collect { (role, connectionState, connectedCount) ->
                    if (started) {
                        notificationManager?.notify(
                            NOTIFICATION_ID, buildConnectionNotification(role, connectionState, connectedCount)
                        )
                    }
                }
        }
    }

    private fun buildConnectionNotification(
        role: UserRole = stateManager.state.value.userRole,
        connectionState: ConnectionState = stateManager.state.value.connectionState,
        connectedCount: Int = stateManager.state.value.connectedCount
    ): Notification {
        val (title, message) = when {
            role == UserRole.PATIENT -> when {
                connectedCount > 0 -> Pair(
                    "EmergencyCall connected",
                    if (connectedCount == 1) "1 caregiver linked" else "$connectedCount caregivers linked"
                )
                connectionState is ConnectionState.Reconnecting -> Pair("Reconnecting…", "Relinking to caregiver")
                else -> Pair("Searching for caregiver", "Tap to open")
            }
            role == UserRole.CAREGIVER -> when {
                connectionState is ConnectionState.Connected -> Pair("Monitoring patient", "Alert link active")
                connectionState is ConnectionState.Reconnecting -> Pair("Reconnecting…", "Relinking to patient")
                else -> Pair("Waiting for patient", "Tap to open")
            }
            else -> Pair("EmergencyCall", "Open to set up")
        }

        val openIntent = PendingIntent.getActivity(
            this,
            3002,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, EmergencyCallApplication.CHANNEL_CONNECTION)
            .setSmallIcon(R.drawable.ic_connection)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(openIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            EmergencyCallApplication.CHANNEL_CONNECTION,
            "Connection Status",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Connection status updates"
            setShowBadge(false)
        }
        notificationManager?.createNotificationChannel(channel)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Deliberately keep running: this is the point of the service. Do NOT stopSelf().
    }

    override fun onDestroy() {
        started = false
        keepAliveScope.cancel()
        statusScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}