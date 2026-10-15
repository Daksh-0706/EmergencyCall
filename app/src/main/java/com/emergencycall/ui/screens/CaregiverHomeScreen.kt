package com.emergencycall.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.navigationBarsPadding
import com.emergencycall.data.ConnectionState
import com.emergencycall.data.EmergencyStateManager
import com.emergencycall.data.EmergencyStatus
import com.emergencycall.data.UserRole
import com.emergencycall.alarm.FullScreenAlarmActivity
import com.emergencycall.networking.NearbyManager

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
        bottomBar = {
            BottomNavigationBar(
                selectedTab = selectedTab,
                onHomeClick = { /* Already on home */ },
                onConnectionClick = onNavigateToConnection,
                onSettingsClick = onNavigateToSettings
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with status bar padding
            CaregiverHeader(
                connectionState = state.connectionState,
                pairedDeviceName = state.pairedDevice?.endpointName,
                onSettingsClick = onNavigateToSettings
            )

            // Scrollable content area - fills remaining space
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Connection status card
                    item {
                        CompactConnectionStatusCard(
                            connectionState = state.connectionState,
                            userRole = state.userRole
                        )
                    }

                    // Patient info card
                    if (state.pairedDevice != null) {
                        item {
                            PatientInfoCard(
                                patientName = state.patientName,
                                connectionState = state.connectionState
                            )
                        }
                    }

                    // Status card
                    item {
                        StatusCard(
                            emergencyStatus = state.emergencyStatus,
                            connectionState = state.connectionState
                        )
                    }

                    // Test alarm button
                    if (state.emergencyStatus == EmergencyStatus.NONE) {
                        item {
                            Button(
                                onClick = { onTestAlarm() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 480.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Science,
                                        contentDescription = "",
                                        modifier = Modifier.size(24.dp).padding(end = 8.dp)
                                    )
                                    Text("TEST ALARM", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }

                    // Connect / re-pair button if not connected
                    if (state.connectionState !is ConnectionState.Connected) {
                        item {
                            val label = when (state.connectionState) {
                                is ConnectionState.Disconnected -> "RE-PAIR DEVICE"
                                else -> "CONNECT DEVICE"
                            }
                            Button(
                                onClick = onNavigateToConnection,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 480.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    // Bottom padding to ensure content doesn't hide behind bottom nav
                    item {
                        Spacer(modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun CaregiverHeader(
    connectionState: ConnectionState,
    pairedDeviceName: String?,
    onSettingsClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "EmergencyCall",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = com.emergencycall.ui.theme.DesignTextPrimary
                )
                Text(
                    text = "CAREGIVER MODE",
                    fontSize = 12.sp,
                    color = com.emergencycall.ui.theme.DesignRed,
                    fontWeight = FontWeight.Medium
                )
            }

            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = com.emergencycall.ui.theme.DesignTextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.padding(8.dp))

        if (pairedDeviceName != null) {
            // Monitoring strip: patient avatar + name, in the design card language.
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, com.emergencycall.ui.theme.DesignBorder, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                com.emergencycall.ui.components.InitialsAvatar(
                    name = pairedDeviceName,
                    size = 44.dp,
                    modifier = Modifier.padding(end = 12.dp)
                )
                androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Monitoring",
                        fontSize = 12.sp,
                        color = com.emergencycall.ui.theme.DesignTextSecondary
                    )
                    Text(
                        text = pairedDeviceName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = com.emergencycall.ui.theme.DesignTextPrimary
                    )
                }
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .background(com.emergencycall.ui.theme.DesignRed.copy(alpha = 0.12f), androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Live",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = com.emergencycall.ui.theme.DesignRed
                    )
                }
            }
        } else {
            Text(
                text = "No patient paired yet",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = com.emergencycall.ui.theme.DesignTextSecondary
            )
        }
    }
}

@Composable
fun PatientInfoCard(patientName: String, connectionState: ConnectionState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp)
            .border(1.dp, com.emergencycall.ui.theme.DesignBorder, androidx.compose.foundation.shape.RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Monitoring",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.padding(8.dp))

            Text(
                text = patientName,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.padding(8.dp))

            Text(
                text = "Status: ${when (connectionState) {
                    is ConnectionState.Connected -> "Connected"
                    is ConnectionState.Connecting -> "Connecting..."
                    is ConnectionState.Disconnected -> "Disconnected"
                    is ConnectionState.Failed -> "Failed"
                    is ConnectionState.ConnectionRequested -> "Connection Requested"
                    is ConnectionState.DeviceFound -> "Device Found"
                    is ConnectionState.NotConnected -> "Not Connected"
                    is ConnectionState.Reconnecting -> "Reconnecting..."
                    is ConnectionState.Searching -> "Searching..."
                    is ConnectionState.Waiting -> "Waiting..."
                }}",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun StatusCard(emergencyStatus: EmergencyStatus, connectionState: ConnectionState) {
    val statusInfo: Triple<String, androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.vector.ImageVector> = when (emergencyStatus) {
        EmergencyStatus.NONE -> {
            when (connectionState) {
                is ConnectionState.Connected -> Triple("NORMAL - Monitoring", MaterialTheme.colorScheme.primary, Icons.Filled.BluetoothConnected)
                is ConnectionState.Connecting -> Triple("CONNECTING...", MaterialTheme.colorScheme.tertiary, Icons.Filled.BluetoothSearching)
                is ConnectionState.Disconnected -> Triple("DISCONNECTED", MaterialTheme.colorScheme.error, Icons.Filled.BluetoothDisabled)
                is ConnectionState.Failed -> Triple("CONNECTION FAILED", MaterialTheme.colorScheme.error, Icons.Filled.Close)
                is ConnectionState.ConnectionRequested -> Triple("CONNECTION REQUESTED", MaterialTheme.colorScheme.tertiary, Icons.Filled.BluetoothConnected)
                is ConnectionState.DeviceFound -> Triple("DEVICE FOUND", MaterialTheme.colorScheme.primary, Icons.Filled.Bluetooth)
                is ConnectionState.NotConnected -> Triple("NOT CONNECTED", MaterialTheme.colorScheme.error, Icons.Filled.BluetoothDisabled)
                is ConnectionState.Reconnecting -> Triple("RECONNECTING...", MaterialTheme.colorScheme.tertiary, Icons.Filled.BluetoothSearching)
                is ConnectionState.Searching -> Triple("SEARCHING...", MaterialTheme.colorScheme.primary, Icons.Filled.BluetoothSearching)
                is ConnectionState.Waiting -> Triple("WAITING...", MaterialTheme.colorScheme.tertiary, Icons.Filled.BluetoothSearching)
            }
        }
        EmergencyStatus.ACTIVE -> Triple("EMERGENCY ACTIVE", MaterialTheme.colorScheme.error, Icons.Filled.Warning)
        EmergencyStatus.SENT_WAITING_ACK -> Triple("WAITING FOR ACK...", MaterialTheme.colorScheme.tertiary, Icons.Filled.HourglassTop)
        EmergencyStatus.ACKNOWLEDGED -> Triple("ACKNOWLEDGED", MaterialTheme.colorScheme.primary, Icons.Filled.CheckCircle)
    }

    val (statusText, statusColor, icon) = statusInfo

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp)
            .border(1.dp, com.emergencycall.ui.theme.DesignBorder, androidx.compose.foundation.shape.RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = statusColor.copy(alpha = 0.15f),
            contentColor = statusColor
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (emergencyStatus == EmergencyStatus.ACTIVE) 12.dp else 4.dp)
    ) {
        Box(
            modifier = Modifier.padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = "Status",
                    modifier = Modifier.size(48.dp),
                    tint = statusColor
                )

                Spacer(modifier = Modifier.padding(16.dp))

                Text(
                    text = "Status: $statusText",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                    textAlign = TextAlign.Center
                )

                if (emergencyStatus == EmergencyStatus.ACTIVE) {
                    Spacer(modifier = Modifier.padding(8.dp))
                    Text(
                        text = "Tap notification or open app to respond",
                        fontSize = 16.sp,
                        color = statusColor.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}