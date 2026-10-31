package com.emergencycall.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emergencycall.data.ConnectionState
import com.emergencycall.data.EmergencyStateManager
import com.emergencycall.data.EmergencyStatus
import com.emergencycall.ui.components.FadeSlideIn
import com.emergencycall.ui.components.HeartbeatDot
import com.emergencycall.ui.components.InitialsAvatar
import com.emergencycall.ui.components.pulseGlow
import com.emergencycall.ui.theme.DesignCardBorder
import com.emergencycall.ui.theme.DesignRed
import com.emergencycall.ui.theme.DesignRedSoft
import com.emergencycall.ui.theme.DesignSurface
import com.emergencycall.ui.theme.DesignTextPrimary
import com.emergencycall.ui.theme.DesignTextSecondary

@Composable
fun CaregiverHomeScreen(
    onNavigateToConnection: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onTestAlarm: () -> Unit,
    selectedTab: NavigationTab = NavigationTab.HOME
) {
    val stateManager = EmergencyStateManager.getInstance()
    val state by stateManager.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            BottomNavigationBar(
                selectedTab = selectedTab,
                onHomeClick = { },
                onConnectionClick = onNavigateToConnection,
                onSettingsClick = onNavigateToSettings
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                FadeSlideIn(delayMs = 0) {
                    CaregiverHeader(
                        connected = state.connectionState is ConnectionState.Connected,
                        onSettingsClick = onNavigateToSettings
                    )
                }
            }

            if (state.pairedDevice != null) {
                item {
                    FadeSlideIn(delayMs = 70) {
                        MonitoringStrip(
                            deviceName = state.pairedDevice?.endpointName ?: "",
                            live = state.connectionState is ConnectionState.Connected
                        )
                    }
                }
            }

            item {
                FadeSlideIn(delayMs = 140) {
                    StatusCard(
                        emergencyStatus = state.emergencyStatus,
                        connectionState = state.connectionState
                    )
                }
            }

            if (state.emergencyStatus == EmergencyStatus.NONE) {
                item {
                    FadeSlideIn(delayMs = 210) {
                        OutlinedButton(
                            onClick = onTestAlarm,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DesignCardBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = DesignTextPrimary
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Science,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.size(8.dp))
                                Text("Test alarm", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            if (state.connectionState !is ConnectionState.Connected) {
                item {
                    FadeSlideIn(delayMs = 280) {
                        Button(
                            onClick = onNavigateToConnection,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DesignRed,
                                contentColor = Color.White
                            )
                        ) {
                            Text(
                                "Connect device",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CaregiverHeader(connected: Boolean, onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "EmergencyCall",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = DesignTextPrimary,
                letterSpacing = 0.2.sp
            )
            Text(
                text = "CAREGIVER MODE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = DesignRed,
                letterSpacing = 1.6.sp
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = DesignSurface,
                shape = RoundedCornerShape(999.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DesignCardBorder)
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HeartbeatDot(
                        color = if (connected) DesignRed else DesignTextSecondary,
                        size = 7.dp
                    )
                    Spacer(Modifier.size(7.dp))
                    Text(
                        text = if (connected) "Armed" else "Offline",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (connected) DesignTextPrimary else DesignTextSecondary
                    )
                }
            }
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = DesignTextSecondary
                )
            }
        }
    }
}

@Composable
private fun MonitoringStrip(deviceName: String, live: Boolean) {
    Surface(
        color = DesignSurface,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DesignCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InitialsAvatar(name = deviceName, size = 46.dp)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Monitoring", fontSize = 11.sp, color = DesignTextSecondary)
                Text(
                    text = deviceName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = DesignTextPrimary
                )
            }
            Surface(
                color = DesignRedSoft,
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HeartbeatDot(color = DesignRed, size = 5.dp)
                    Spacer(Modifier.size(5.dp))
                    Text(
                        text = if (live) "LIVE" else "IDLE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = DesignRed
                    )
                }
            }
        }
    }
}

@Composable
fun StatusCard(emergencyStatus: EmergencyStatus, connectionState: ConnectionState) {
    val statusInfo: Triple<String, Color, ImageVector> = when (emergencyStatus) {
        EmergencyStatus.NONE -> {
            when (connectionState) {
                is ConnectionState.Connected -> Triple("Normal · Monitoring", MaterialTheme.colorScheme.primary, Icons.Filled.BluetoothConnected)
                is ConnectionState.Connecting -> Triple("Connecting...", MaterialTheme.colorScheme.tertiary, Icons.Filled.BluetoothSearching)
                is ConnectionState.Disconnected -> Triple("Disconnected", MaterialTheme.colorScheme.error, Icons.Filled.BluetoothDisabled)
                is ConnectionState.Failed -> Triple("Connection failed", MaterialTheme.colorScheme.error, Icons.Filled.Close)
                is ConnectionState.ConnectionRequested -> Triple("Connection requested", MaterialTheme.colorScheme.tertiary, Icons.Filled.BluetoothConnected)
                is ConnectionState.DeviceFound -> Triple("Device found", MaterialTheme.colorScheme.primary, Icons.Filled.Bluetooth)
                is ConnectionState.NotConnected -> Triple("Not connected", MaterialTheme.colorScheme.error, Icons.Filled.BluetoothDisabled)
                is ConnectionState.Reconnecting -> Triple("Reconnecting...", MaterialTheme.colorScheme.tertiary, Icons.Filled.BluetoothSearching)
                is ConnectionState.Searching -> Triple("Searching...", MaterialTheme.colorScheme.primary, Icons.Filled.BluetoothSearching)
                is ConnectionState.Waiting -> Triple("Waiting...", MaterialTheme.colorScheme.tertiary, Icons.Filled.BluetoothSearching)
            }
        }
        EmergencyStatus.ACTIVE -> Triple("EMERGENCY ACTIVE", MaterialTheme.colorScheme.error, Icons.Filled.Warning)
        EmergencyStatus.SENT_WAITING_ACK -> Triple("Waiting for ack...", MaterialTheme.colorScheme.tertiary, Icons.Filled.HourglassTop)
        EmergencyStatus.ACKNOWLEDGED -> Triple("Acknowledged", MaterialTheme.colorScheme.primary, Icons.Filled.CheckCircle)
    }

    val (statusText, statusColor, icon) = statusInfo
    val alarmActive = emergencyStatus == EmergencyStatus.ACTIVE

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .pulseGlow(enabled = alarmActive, color = statusColor),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (alarmActive) statusColor.copy(alpha = 0.5f) else DesignCardBorder
        ),
        color = Color.Transparent
    ) {
        Column(
            Modifier
                .background(
                    if (alarmActive) statusColor.copy(alpha = 0.14f) else DesignSurface,
                    RoundedCornerShape(24.dp)
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .background(statusColor.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = "Status",
                    modifier = Modifier.size(28.dp),
                    tint = statusColor
                )
            }
            Spacer(Modifier.size(14.dp))
            Text(
                text = statusText,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = statusColor,
                textAlign = TextAlign.Center
            )
            if (alarmActive) {
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "Tap STOP on the full-screen alert to respond",
                    fontSize = 13.sp,
                    color = statusColor.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
