package com.emergencycall.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.navigationBarsPadding
import com.emergencycall.data.ConnectionState
import com.emergencycall.data.EmergencyStateManager
import com.emergencycall.data.UserRole
import com.emergencycall.connection.ConnectionKeepAliveService
import com.emergencycall.networking.NearbyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/**
 * Shared connection-status model used by the Connection screen and the home screens
 * so iconography (Bluetooth icons) and copy stay consistent everywhere.
 */
data class ConnectionStatusInfo(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color,
    val iconBg: Color,
    val showSpinner: Boolean = false
)

@Composable
fun connectionStatusInfo(connectionState: ConnectionState, userRole: UserRole): ConnectionStatusInfo {
    val scheme = MaterialTheme.colorScheme
    return when (connectionState) {
        is ConnectionState.NotConnected -> ConnectionStatusInfo(
            "NOT CONNECTED",
            "Tap the button below to begin.",
            Icons.Filled.BluetoothDisabled, scheme.onSurfaceVariant, scheme.surfaceContainerHighest
        )
        is ConnectionState.Waiting -> ConnectionStatusInfo(
            "WAITING FOR PATIENT",
            "Keep this phone near the patient phone. Waiting for a connection request…",
            Icons.Filled.BluetoothSearching, scheme.tertiary, scheme.tertiaryContainer,
            showSpinner = true
        )
        is ConnectionState.Searching -> ConnectionStatusInfo(
            "SEARCHING…",
            "Scanning for the other phone. Make sure it is nearby and in pairing mode.",
            Icons.Filled.BluetoothSearching, scheme.primary, scheme.primaryContainer,
            showSpinner = true
        )
        is ConnectionState.DeviceFound -> ConnectionStatusInfo(
            "DEVICE FOUND",
            "Tap CONNECT to pair with ${connectionState.endpointName}.",
            Icons.Filled.Bluetooth, scheme.primary, scheme.primaryContainer
        )
        is ConnectionState.ConnectionRequested -> ConnectionStatusInfo(
            "PATIENT FOUND",
            "${connectionState.endpointName} wants to connect.",
            Icons.Filled.BluetoothConnected, scheme.tertiary, scheme.tertiaryContainer,
            showSpinner = true
        )
        is ConnectionState.Connecting -> ConnectionStatusInfo(
            "CONNECTING…",
            "Connecting to ${connectionState.endpointName}…",
            Icons.Filled.BluetoothSearching, scheme.tertiary, scheme.tertiaryContainer,
            showSpinner = true
        )
        is ConnectionState.Connected -> ConnectionStatusInfo(
            "CONNECTED",
            "Linked with ${connectionState.endpointName}.",
            Icons.Filled.BluetoothConnected, scheme.primary, scheme.primaryContainer
        )
        is ConnectionState.Disconnected -> ConnectionStatusInfo(
            "DISCONNECTED",
            "The connection was lost. Tap the button below to reconnect.",
            Icons.Filled.BluetoothDisabled, scheme.error, scheme.errorContainer
        )
        is ConnectionState.Reconnecting -> ConnectionStatusInfo(
            "RECONNECTING…",
            "Attempting to reconnect to ${connectionState.endpointName}…",
            Icons.Filled.BluetoothSearching, scheme.tertiary, scheme.tertiaryContainer,
            showSpinner = true
        )
        is ConnectionState.Failed -> ConnectionStatusInfo(
            "FAILED",
            connectionState.reason,
            Icons.Filled.Close, scheme.error, scheme.errorContainer
        )
    }
}

@Composable
fun ConnectionScreen(
    onNavigateHome: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onStartOver: () -> Unit
) {
    val stateManager = EmergencyStateManager.getInstance()
    val state by stateManager.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val nearbyManager = NearbyManager.getInstance(context)
    val role = state.userRole
    val coroutineScope = rememberCoroutineScope()

    var discoveredDevices by remember { mutableStateOf<Map<String, com.google.android.gms.nearby.connection.DiscoveredEndpointInfo>>(emptyMap()) }

    // Reset a "lost" connection to a clean starting point so the right action button
    // (SEARCH FOR CAREGIVER / WAIT FOR PATIENT) is shown. Active connections are untouched.
    LaunchedEffect(key1 = role) {
        if (role != UserRole.NONE &&
            stateManager.state.value.connectionState is ConnectionState.Disconnected
        ) {
            stateManager.update { copy(connectionState = ConnectionState.NotConnected) }
        }
    }

    LaunchedEffect(key1 = nearbyManager) {
        nearbyManager.discoveredEndpointsFlow.collect { endpoints ->
            discoveredDevices = endpoints
        }
    }

    // Auto-return to home as soon as a pairing completes so the CAREGIVER lands back
    // on the monitoring screen instead of being stuck on this tab. The PATIENT stays
    // here after connecting so they can add multiple caregivers in one sitting.
    var wasConnectedBefore by remember { mutableStateOf(state.connectionState is ConnectionState.Connected) }
    LaunchedEffect(state.connectionState) {
        val nowConnected = state.connectionState is ConnectionState.Connected
        if (nowConnected && !wasConnectedBefore && role == UserRole.CAREGIVER) {
            onNavigateHome()
        }
        wasConnectedBefore = nowConnected
    }

    Scaffold(
        bottomBar = {
            BottomNavigationBar(
                selectedTab = NavigationTab.CONNECTION,
                onHomeClick = onNavigateHome,
                onConnectionClick = { /* already on connection */ },
                onSettingsClick = onNavigateToSettings
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
        ) {
            // Scrollable content
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp).statusBarsPadding(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "EmergencyCall",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (role == UserRole.PATIENT) "CONNECT TO CAREGIVER" else "CAREGIVER CONNECTION",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Status card
                item {
                    ConnectionStatusCard(connectionState = state.connectionState, userRole = role)
                }

                // Instructions card (role specific)
                item {
                    ConnectionInstructionsCard(userRole = role)
                }

                // Action area
                item {
                    when (role) {
                        UserRole.PATIENT -> PatientConnectionControls(
                            connectionState = state.connectionState,
                            discoveredDevices = discoveredDevices,
                            pairedDevices = state.pairedDevices,
                            connectedCount = state.connectedCount,
                            onSearch = {
                                // Patient only discovers; it never advertises.
                                coroutineScope.launch {
                                    nearbyManager.startDiscovery(UserRole.PATIENT)
                                }
                            },
                            onDeviceClick = { endpointId, endpointName ->
                                nearbyManager.initiateConnection(endpointId, endpointName)
                            },
                            onConnect = { endpointId, endpointName ->
                                nearbyManager.connectToEndpoint(endpointId, endpointName)
                            },
                            onDisconnect = {
                                // No pairing left — stop the background keep-alive so it isn't
                                // advertising a cleared connection.
                                ConnectionKeepAliveService.stop(context)
                                nearbyManager.disconnect()
                                coroutineScope.launch(Dispatchers.IO) {
                                    com.emergencycall.data.PreferencesManager.setPairedCaregivers(emptyList())
                                    com.emergencycall.data.PreferencesManager.setPairedDevice(null)
                                }
                                onStartOver()
                            }
                        )
                        UserRole.CAREGIVER -> CaregiverConnectionControls(
                            connectionState = state.connectionState,
                            discoveredDevices = discoveredDevices,
                            onWait = {
                                // Caregiver only advertises; it never discovers.
                                coroutineScope.launch {
                                    nearbyManager.startAdvertising(UserRole.CAREGIVER)
                                }
                            },
                            onAccept = { endpointId -> nearbyManager.acceptConnection(endpointId) },
                            onReject = { endpointId -> nearbyManager.rejectConnection(endpointId) },
                            onDisconnect = {
                                ConnectionKeepAliveService.stop(context)
                                nearbyManager.disconnect()
                                coroutineScope.launch(Dispatchers.IO) {
                                    com.emergencycall.data.PreferencesManager.setPairedDevice(null)
                                    com.emergencycall.data.PreferencesManager.setPairedCaregivers(emptyList())
                                }
                                onStartOver()
                            }
                        )
                        else -> Text("Select a role first", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Bottom padding
                item {
                    Spacer(modifier = Modifier.padding(16.dp))
                }
            }

            // Bottom navigation (always visible) - handled by Scaffold bottomBar
        }
    }
}

@Composable
private fun ConnectionStatusCard(
    connectionState: ConnectionState,
    userRole: UserRole
) {
    val info = connectionStatusInfo(connectionState, userRole)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp),
        colors = CardDefaults.cardColors(
            containerColor = info.color.copy(alpha = 0.12f),
            contentColor = info.color
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(info.iconBg, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (info.showSpinner) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = info.color,
                        strokeWidth = 3.dp
                    )
                } else {
                    Icon(
                        imageVector = info.icon,
                        contentDescription = "Connection",
                        modifier = Modifier.size(32.dp),
                        tint = info.color
                    )
                }
            }
            Spacer(modifier = Modifier.padding(8.dp))
            Text(
                text = info.title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = info.color,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.padding(4.dp))
            Text(
                text = info.subtitle,
                fontSize = 14.sp,
                color = info.color.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Compact connection-status card reused by the home screens so iconography and copy
 * match the dedicated Connection screen.
 */
@Composable
fun CompactConnectionStatusCard(
    connectionState: ConnectionState,
    userRole: UserRole
) {
    val info = connectionStatusInfo(connectionState, userRole)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp),
        colors = CardDefaults.cardColors(
            containerColor = info.color.copy(alpha = 0.12f),
            contentColor = info.color
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(info.iconBg, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (info.showSpinner) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = info.color,
                        strokeWidth = 3.dp
                    )
                } else {
                    Icon(
                        imageVector = info.icon,
                        contentDescription = "Connection",
                        modifier = Modifier.size(24.dp),
                        tint = info.color
                    )
                }
            }
            Spacer(modifier = Modifier.padding(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = info.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = info.color
                )
                Spacer(modifier = Modifier.padding(2.dp))
                Text(
                    text = info.subtitle,
                    fontSize = 14.sp,
                    color = info.color.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun ConnectionInstructionsCard(userRole: UserRole) {
    val (title, description) = when (userRole) {
        UserRole.PATIENT -> Pair(
            "How to connect (Patient)",
            "1. Open Caregiver Mode on the caregiver phone.\n" +
                "2. On the caregiver phone, open Connection and tap WAIT FOR PATIENT.\n" +
                "3. On this phone, tap SEARCH FOR CAREGIVER, then tap the device and CONNECT."
        )
        UserRole.CAREGIVER -> Pair(
            "How to connect (Caregiver)",
            "1. Keep this phone near the patient phone.\n" +
                "2. Tap WAIT FOR PATIENT below.\n" +
                "3. When the patient searches from the other phone, tap ACCEPT."
        )
        else -> Pair("Select role first", "Go to Settings to choose Patient or Caregiver.")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.padding(4.dp))
            Text(
                text = description,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PatientConnectionControls(
    connectionState: ConnectionState,
    discoveredDevices: Map<String, com.google.android.gms.nearby.connection.DiscoveredEndpointInfo>,
    pairedDevices: List<com.emergencycall.data.PairedDevice>,
    connectedCount: Int,
    onSearch: () -> Unit,
    onDeviceClick: (String, String) -> Unit,
    onConnect: (String, String) -> Unit,
    onDisconnect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (connectionState) {
            is ConnectionState.Connected -> {
                // List the known caregivers and let the patient add more before leaving.
                if (pairedDevices.isNotEmpty()) {
                    Text(
                        text = if (connectedCount == 1) "1 caregiver connected" else "$connectedCount caregivers connected",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.padding(6.dp))
                    pairedDevices.forEach { dev ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.BluetoothConnected,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.padding(6.dp))
                                Text(dev.endpointName, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        Spacer(modifier = Modifier.padding(4.dp))
                    }
                    Spacer(modifier = Modifier.padding(6.dp))
                }
                PrimaryButton("SEARCH FOR MORE CAREGIVERS", onSearch)
                Spacer(modifier = Modifier.padding(8.dp))
                Button(
                    onClick = onDisconnect,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text("DISCONNECT ALL & RE-PAIR", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
            is ConnectionState.Connecting,
            is ConnectionState.ConnectionRequested,
            is ConnectionState.Reconnecting -> {
                CircularProgressIndicator(modifier = Modifier.size(40.dp), color = MaterialTheme.colorScheme.tertiary)
            }
            is ConnectionState.DeviceFound -> {
                // Show the chosen device with a CONNECT button.
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = connectionState.endpointName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.padding(8.dp))
                        Button(
                            onClick = { onConnect(connectionState.endpointId, connectionState.endpointName) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("CONNECT", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            is ConnectionState.Searching -> {
                Text("Searching for caregiver phone…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is ConnectionState.Failed -> {
                PrimaryButton("SEARCH AGAIN", onSearch)
            }
            else -> {
                // NotConnected (or Waiting handled below for caregiver)
                PrimaryButton("SEARCH FOR CAREGIVER", onSearch)
            }
        }

        // Show discovered devices during NotConnected/Searching (first-time pairing)
        // and also while Connected, so the patient can add another caregiver.
        if ((connectionState is ConnectionState.NotConnected ||
                connectionState is ConnectionState.Searching ||
                connectionState is ConnectionState.Connected)
            && discoveredDevices.isNotEmpty()) {
            Spacer(modifier = Modifier.padding(16.dp))
            Text(
                text = if (connectionState is ConnectionState.Connected) "Add another caregiver" else "Nearby devices",
                fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.padding(8.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)
            ) {
                items(discoveredDevices.entries.toList()) { entry ->
                    val (endpointId, info) = entry
                    DeviceCard(endpointId = endpointId, endpointName = info.endpointName) {
                        // While already connected, tapping connects directly (no
                        // DeviceFound intermediate, which would hide the connected list).
                        if (connectionState is ConnectionState.Connected) {
                            onConnect(endpointId, info.endpointName)
                        } else {
                            onDeviceClick(endpointId, info.endpointName)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CaregiverConnectionControls(
    connectionState: ConnectionState,
    discoveredDevices: Map<String, com.google.android.gms.nearby.connection.DiscoveredEndpointInfo>,
    onWait: () -> Unit,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit,
    onDisconnect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (connectionState) {
            is ConnectionState.Connected -> {
                Button(
                    onClick = onDisconnect,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text("DISCONNECT & RE-PAIR", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
            is ConnectionState.ConnectionRequested -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("PATIENT FOUND", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.padding(4.dp))
                        Text(connectionState.endpointName, fontSize = 16.sp)
                        Text("Wants to connect", fontSize = 14.sp, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f))
                        Spacer(modifier = Modifier.padding(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { onAccept(connectionState.endpointId) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text("ACCEPT", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            }
                            Button(
                                onClick = { onReject(connectionState.endpointId) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Text("REJECT", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
            is ConnectionState.Connecting,
            is ConnectionState.Reconnecting -> {
                CircularProgressIndicator(modifier = Modifier.size(40.dp), color = MaterialTheme.colorScheme.tertiary)
            }
            is ConnectionState.Waiting -> {
                Text("Waiting for the patient to connect…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is ConnectionState.Failed -> {
                PrimaryButton("WAIT FOR PATIENT", onWait)
            }
            else -> {
                PrimaryButton("WAIT FOR PATIENT", onWait)
            }
        }
    }
}

@Composable
private fun DeviceCard(
    endpointId: String,
    endpointName: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = endpointName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Tap to connect",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Filled.ArrowForwardIos,
                contentDescription = "",
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}