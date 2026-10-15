package com.emergencycall.alarm

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.emergencycall.data.EmergencyStateManager
import com.emergencycall.data.EmergencyStatus
import com.emergencycall.data.PreferencesManager
import com.emergencycall.alarm.AlarmManager
import com.emergencycall.networking.NearbyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FullScreenAlarmActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PATIENT_NAME = "patient_name"
        const val EXTRA_EVENT_ID = "event_id"
    }

    private var patientName = ""
    private var eventId = ""
    private var acknowledged = false
    // Latches true once we've observed our own event active. Prevents a TEST alarm
    // (which never sets a matching active event) from being auto-dismissed, and
    // avoids dismissing before the emergency state has propagated.
    private var sawMatchingEvent = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure screen stays on and shows on lock screen
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setTurnScreenOn(true)
            setShowWhenLocked(true)
        }

        patientName = intent.getStringExtra(EXTRA_PATIENT_NAME) ?: "Patient"
        eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: ""

        setContent {
            EmergencyAlarmScreen(
                patientName = patientName,
                eventId = eventId,
                onAcknowledge = { acknowledge() }
            )
        }

        // If this emergency is cleared or replaced while the alarm is showing
        // (e.g. another caregiver responded first and the patient relayed the ACK),
        // close this screen WITHOUT the onDestroy self-restart.
        lifecycleScope.launch {
            EmergencyStateManager.getInstance().state.collect { st ->
                if (eventId.isEmpty()) return@collect
                if (st.activeEmergencyEvent?.eventId == eventId &&
                    st.emergencyStatus == EmergencyStatus.ACTIVE) {
                    sawMatchingEvent = true
                } else if (sawMatchingEvent) {
                    dismissWithoutAck()
                }
            }
        }
    }

    override fun onBackPressed() {
        // Prevent back button from dismissing the alarm
        // Only allow if acknowledged
        if (!acknowledged) {
            // Do nothing - alarm cannot be dismissed
        } else {
            super.onBackPressed()
        }
    }

    private fun acknowledge() {
        acknowledged = true

        // Stop the alarm service
        val intent = Intent(this, AlarmForegroundService::class.java).apply {
            action = AlarmForegroundService.ACTION_STOP_ALARM
        }
        startService(intent)
        // MainActivity may also have started its local AlarmPlayer. Stop that
        // instance as well; stopping only the foreground service leaves sound on.
        AlarmManager(this).stopEmergencyAlarm()

        // Send acknowledgement to patient, tagged with this caregiver's name so the
        // patient can show "<name> is on the way" and relay it to other caregivers.
        CoroutineScope(Dispatchers.IO).launch {
            val deviceName = PreferencesManager.getDeviceName()
            NearbyManager.getInstance(this@FullScreenAlarmActivity).sendAcknowledged(eventId, deviceName)
        }

        // Update state
        EmergencyStateManager.getInstance().clearEmergency()

        finish()
    }

    /**
     * Closes the alarm because it was resolved elsewhere (another caregiver responded,
     * relayed via the patient) rather than by this user pressing acknowledge. Marks
     * [acknowledged] so onDestroy does not relaunch, and stops the foreground service's
     * sound. This device's local MediaPlayer (if any) is stopped by MainActivity's
     * status→NONE watcher.
     */
    private fun dismissWithoutAck() {
        if (acknowledged || isFinishing) return
        acknowledged = true
        val intent = Intent(this, AlarmForegroundService::class.java).apply {
            action = AlarmForegroundService.ACTION_STOP_ALARM
        }
        startService(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        // If not acknowledged, restart the alarm activity
        if (!acknowledged && !isFinishing) {
            val restartIntent = Intent(this, FullScreenAlarmActivity::class.java).apply {
                putExtra(EXTRA_PATIENT_NAME, patientName)
                putExtra(EXTRA_EVENT_ID, eventId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(restartIntent)
        }
    }
}

@Composable
fun EmergencyAlarmScreen(
    patientName: String,
    eventId: String,
    onAcknowledge: () -> Unit
) {
    var flash by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize()
            .background(if (flash) Color.Red else Color(0xFF8B0000)) // Dark red background
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Emergency icon
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = "Emergency",
                modifier = Modifier.size(120.dp),
                tint = Color.White
            )

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(24.dp))

            // EMERGENCY text
            Text(
                text = "🚨 EMERGENCY",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(16.dp))

            // Patient name
            Text(
                text = "Patient: $patientName",
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))

            // Time
            Text(
                text = "Time: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}",
                fontSize = 22.sp,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(16.dp))

            // Status
            Text(
                text = "HELP REQUIRED",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Yellow,
                textAlign = TextAlign.Center
            )

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(48.dp))

            // Acknowledge button - HUGE
            Button(
                onClick = onAcknowledge,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .padding(24.dp)
                    .size(300.dp, 120.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Red
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "STOP / ACKNOWLEDGE",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Red,
                    textAlign = TextAlign.Center
                )
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(24.dp))

            Text(
                text = "Press to stop alarm and confirm help is on the way",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }
    }
}
