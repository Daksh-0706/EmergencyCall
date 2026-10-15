package com.emergencycall

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emergencycall.alarm.AlarmManager
import com.emergencycall.alarm.FullScreenAlarmActivity
import com.emergencycall.connection.ConnectionKeepAliveService
import com.emergencycall.data.EmergencyStateManager
import com.emergencycall.data.EmergencyStatus
import com.emergencycall.data.NetworkMessage
import com.emergencycall.data.PreferencesManager
import com.emergencycall.data.UserRole
import com.emergencycall.data.ConnectionState
import com.emergencycall.networking.NearbyManager
import com.emergencycall.networking.transport.EmergencyRouter
import com.emergencycall.networking.transport.InternetTransport
import com.emergencycall.networking.transport.LocalWifiTransport
import com.emergencycall.networking.transport.NearbyTransport
import com.emergencycall.permissions.PermissionManager
import com.emergencycall.ui.screens.CaregiverHomeScreen
import com.emergencycall.ui.screens.ConnectionScreen
import com.emergencycall.ui.screens.PatientHomeScreen
import com.emergencycall.ui.screens.RoleSelectionScreen
import com.emergencycall.ui.screens.SettingsScreen
import com.emergencycall.ui.screens.BottomNavigationBar
import com.emergencycall.ui.screens.NavigationTab
import com.emergencycall.ui.theme.EmergencyCall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val stateManager = EmergencyStateManager.getInstance()
    private val nearbyManager by lazy { NearbyManager.getInstance(this) }
    private val alarmManager by lazy { AlarmManager(this) }
    private val permissionManager by lazy { PermissionManager(this) }

    // Transport layer
    private val nearbyTransport by lazy { NearbyTransport(nearbyManager) }
    private val localWifiTransport by lazy { LocalWifiTransport(this) }
    private val internetTransport by lazy { InternetTransport() }
    private val emergencyRouter by lazy {
        EmergencyRouter(nearbyTransport, localWifiTransport, internetTransport)
    }

    // These values are read by MainScreen(), so they must be Compose state.
    // Plain properties do not invalidate the composition when navigation callbacks
    // change them (for example, after a successful connection).
    private var showRoleSelection by mutableStateOf(false)
    private var showSettings by mutableStateOf(false)
    private var showConnection by mutableStateOf(false)
    private var testAlarmPending by mutableStateOf(false)
    private var showDiagnostics by mutableStateOf(false)
    private var diagnosticsText by mutableStateOf("")

    // Permission request launcher
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        handlePermissionResult(permissions)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load preferences asynchronously
        lifecycleScope.launch(Dispatchers.IO) {
            val hasSetup = PreferencesManager.getHasCompletedSetup()
            val userRole = PreferencesManager.getUserRole()
            val patientName = PreferencesManager.getPatientName()
            val deviceName = PreferencesManager.getDeviceName()
            val vibrationEnabled = PreferencesManager.getVibrationEnabled()
            val pairedDevice = PreferencesManager.getPairedDevice()
            // Patient's multi-caregiver list. Migrate a legacy single pairing into the
            // list so anyone who paired on an older build keeps their caregiver.
            var pairedCaregivers = PreferencesManager.getPairedCaregivers()
            if (userRole == UserRole.PATIENT && pairedCaregivers.isEmpty() && pairedDevice != null) {
                pairedCaregivers = listOf(pairedDevice)
                PreferencesManager.setPairedCaregivers(pairedCaregivers)
            }

            runOnUiThread {
                showRoleSelection = !hasSetup || userRole == UserRole.NONE

                // Sync state from preferences
                stateManager.setUserRole(userRole)
                stateManager.setPatientName(patientName)
                stateManager.setDeviceName(deviceName)
                stateManager.setVibrationEnabled(vibrationEnabled)
                pairedDevice?.let { device ->
                    stateManager.setPairedDevice(device)
                }
                if (userRole == UserRole.PATIENT) {
                    stateManager.setPairedCaregivers(pairedCaregivers)
                }

                // Load persisted emergency state
                stateManager.loadEmergencyState()

                // Request permissions
                permissionManager.requestAllPermissions(requestPermissionsLauncher)

                // Start listening for incoming messages (legacy NearbyManager channel)
                startMessageListener()

                // Start listening on all transports via EmergencyRouter
                startEmergencyRouterListener()

                // Stop this device's local alarm the moment an emergency clears.
                startAlarmStopWatcher()

                // Check for test alarm intent
                if (intent.hasExtra("TEST_ALARM")) {
                    testAlarmPending = true
                }

                setContent {
                    EmergencyCall {
                        MainScreen()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasExtra("TEST_ALARM")) {
            testAlarmPending = true
            triggerTestAlarm()
        }
    }

    override fun onResume() {
        super.onResume()
        val state = stateManager.state.value
        if (state.userRole == UserRole.NONE) return

        val hasKnown = when (state.userRole) {
            UserRole.PATIENT -> state.pairedDevices.isNotEmpty()
            UserRole.CAREGIVER -> state.pairedDevice != null
            else -> false
        }
        if (!hasKnown) return

        val allConnected = when (state.userRole) {
            UserRole.PATIENT -> state.connectedCount >= state.pairedDevices.size
            UserRole.CAREGIVER -> state.connectionState is ConnectionState.Connected
            else -> true
        }
        // Whenever we're not fully connected (e.g. app was killed and relaunched, or a
        // link dropped in the background), re-arm the role channel. onEndpointFound /
        // auto-accept + the reconnect loop do the actual re-linking by name.
        if (!allConnected) {
            restartConnections()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Deliberately do NOT NearbyManager.destroyInstance() here. ConnectionKeepAliveService
        // owns the Nearby channel while the app is backgrounded; destroying the manager on
        // activity destroy (e.g. a config change) would cancel its scope and drop the very
        // link we're trying to keep alive. The singleton lives for the process, and explicit
        // teardown on role change / disconnect already clears state and endpoints.
    }

    private fun handlePermissionResult(permissions: Map<String, Boolean>) {
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            // Permissions granted, initialize nearby connections
            val state = stateManager.state.value
            if (state.userRole != UserRole.NONE) {
                restartConnections()
            }
        } else {
            // Show permission rationale
            showPermissionRationale()
        }
    }

    private fun showPermissionRationale() {
        // This would show a dialog explaining why permissions are needed
    }

    private fun startMessageListener() {
        CoroutineScope(Dispatchers.IO).launch {
            nearbyManager.incomingPayloadFlow.collect { message ->
                handleIncomingMessage(message)
            }
        }
    }

    private fun startEmergencyRouterListener() {
        lifecycleScope.launch(Dispatchers.IO) {
            emergencyRouter.startListening { event ->
                runOnUiThread {
                    handleEmergencyReceived(event)
                }
            }
        }
    }

    /**
     * Stops this device's local alarm whenever the emergency clears (status → NONE).
     * Covers two paths the FullScreenAlarmActivity cannot: the responding caregiver's
     * own local MediaPlayer (MainActivity started it, and FullScreenAlarmActivity's
     * fresh AlarmManager instance can't stop it), and any other caregiver whose alarm
     * is stopped remotely by the relayed acknowledgement.
     */
    private fun startAlarmStopWatcher() {
        lifecycleScope.launch {
            var prevStatus: EmergencyStatus? = null
            stateManager.state.collect { st ->
                val status = st.emergencyStatus
                if (prevStatus != null && prevStatus != status && status == EmergencyStatus.NONE) {
                    alarmManager.stopEmergencyAlarm()
                }
                prevStatus = status
            }
        }
    }

    private fun handleIncomingMessage(message: NetworkMessage) {
        runOnUiThread {
            when (message) {
                is NetworkMessage.Emergency -> handleEmergencyReceived(message.event)
                is NetworkMessage.Acknowledged -> handleAcknowledgedReceived(message.event)
                is NetworkMessage.Heartbeat -> handleHeartbeatReceived()
                is NetworkMessage.PairingRequest -> handlePairingRequest(message)
                is NetworkMessage.PairingResponse -> handlePairingResponse(message)
            }
        }
    }

    private fun handleEmergencyReceived(event: com.emergencycall.data.EmergencyEvent) {
        val state = stateManager.state.value

        // Ignore duplicate events
        if (state.emergencyStatus == EmergencyStatus.ACTIVE &&
            state.activeEmergencyEvent?.eventId == event.eventId) {
            return
        }

        // Update state
        stateManager.update {
            copy(
                emergencyStatus = EmergencyStatus.ACTIVE,
                activeEmergencyEvent = event
            )
        }

        // Start alarm
        alarmManager.startEmergencyAlarm(event.patientName, event.eventId)

        // Acknowledgement is sent only when the caregiver explicitly presses
        // STOP / ACKNOWLEDGE. Sending it here made the patient appear finished
        // before the caregiver had responded.
    }

    private fun handleAcknowledgedReceived(event: com.emergencycall.data.AcknowledgedEvent) {
        val state = stateManager.state.value
        // Only react if this ACK is for the emergency we currently care about.
        // The first responder's ACK reaches the patient; every other caregiver's
        // link has already been cleared by the time the relay echoes back, so they
        // fall through this guard and don't loop.
        if (state.activeEmergencyEvent?.eventId != event.eventId) return

        if (state.userRole == UserRole.PATIENT) {
            // First-responder-wins: record who responded, then relay the ACK to
            // every connected caregiver so the others' alarms stop. Guard on
            // "not already acknowledged" so a second caregiver's ACK is a no-op.
            if (state.emergencyStatus != EmergencyStatus.ACKNOWLEDGED) {
                stateManager.update {
                    copy(
                        emergencyStatus = EmergencyStatus.ACKNOWLEDGED,
                        responderName = event.responderName
                    )
                }
                // Relay to all caregivers (the original responder ignores its own echo).
                nearbyManager.sendAcknowledged(event.eventId, event.responderName)
                // Dwell on the acknowledged screen, then return to the emergency button.
                lifecycleScope.launch {
                    delay(8000)
                    val s = stateManager.state.value
                    if (s.emergencyStatus == EmergencyStatus.ACKNOWLEDGED &&
                        s.activeEmergencyEvent?.eventId == event.eventId) {
                        stateManager.clearEmergency()
                    }
                }
            }
        } else {
            // Caregiver: a peer responded first (relayed via the patient). Stop our
            // alarm and clear. clearEmergency() flips status to NONE, which also
            // dismisses this device's full-screen alarm (see FullScreenAlarmActivity).
            stateManager.clearEmergency()
            alarmManager.stopEmergencyAlarm()
        }
    }

    private fun handleHeartbeatReceived() {
        stateManager.updateLastHeartbeat()
    }

    private fun handlePairingRequest(message: NetworkMessage.PairingRequest) {
        // Auto-accept pairing for simplicity
        lifecycleScope.launch(Dispatchers.IO) {
            val deviceName = PreferencesManager.getDeviceName()
            runOnUiThread {
                nearbyManager.sendMessage(
                    com.emergencycall.data.NetworkMessage.PairingResponse(
                        accepted = true,
                        deviceName = deviceName
                    )
                )
            }
        }
    }

    private fun handlePairingResponse(message: NetworkMessage.PairingResponse) {
        if (message.accepted) {
            // Pairing accepted
            runOnUiThread {
                showConnection = false
                // Connection will be established via Nearby callbacks
            }
        }
    }

    private fun restartConnections() {
        val state = stateManager.state.value
        nearbyManager.stopAdvertising()
        nearbyManager.stopDiscovery()

        // Foreground service keeps the Nearby link alive while the app is backgrounded.
        ConnectionKeepAliveService.start(this)

        lifecycleScope.launch(Dispatchers.IO) {
            if (state.userRole == UserRole.PATIENT) {
                // Patient only discovers and initiates; it never advertises.
                nearbyManager.startDiscovery(UserRole.PATIENT)
            } else if (state.userRole == UserRole.CAREGIVER) {
                // Caregiver only advertises and accepts; it never discovers.
                nearbyManager.startAdvertising(UserRole.CAREGIVER)
            }
        }
    }

    private fun triggerEmergency() {
        val state = stateManager.state.value
        if (state.userRole != UserRole.PATIENT) return

        val event = com.emergencycall.data.EmergencyEvent(
            patientId = state.deviceName,
            patientName = state.patientName
        )

        stateManager.update {
            copy(
                emergencyStatus = EmergencyStatus.ACTIVE,
                activeEmergencyEvent = event
            )
        }

        // Direct broadcast to every connected caregiver — guaranteed Nearby fan-out.
        // The router still runs below (non-Nearby fallback); double-delivery is harmless
        // because caregivers dedupe on eventId in handleEmergencyReceived.
        nearbyManager.sendEmergency(event)
        lifecycleScope.launch(Dispatchers.IO) {
            val result = emergencyRouter.routeEmergency(event)

            runOnUiThread {
                if (result.success) {
                    // Update to waiting for ack
                    stateManager.update {
                        copy(emergencyStatus = EmergencyStatus.SENT_WAITING_ACK)
                    }
                } else {
                    // Delivery failed - persist emergency locally, will retry on reconnect
                    stateManager.update {
                        copy(emergencyStatus = EmergencyStatus.SENT_WAITING_ACK)
                    }
                    Log.w("MainActivity", "Emergency delivery failed: ${result.errorMessage}, will retry on reconnect")
                }
            }
        }
    }

    private fun triggerTestAlarm() {
        testAlarmPending = false
        alarmManager.startEmergencyAlarm("TEST PATIENT", "TEST_EVENT_${System.currentTimeMillis()}")

        // Auto-stop after 10 seconds for test
        CoroutineScope(Dispatchers.IO).launch {
            kotlinx.coroutines.delay(10000)
            runOnUiThread {
                alarmManager.stopEmergencyAlarm()
            }
        }
    }

    private fun showDiagnostics() {
        val state = stateManager.state.value
        val diagnostics = StringBuilder()
        diagnostics.appendLine("=== Connection Diagnostics ===")
        diagnostics.appendLine("Role: ${state.userRole.name}")
        diagnostics.appendLine("Patient Name: ${state.patientName}")
        diagnostics.appendLine("Device Name: ${state.deviceName}")
        diagnostics.appendLine("Vibration: ${state.vibrationEnabled}")
        diagnostics.appendLine("Connection State: ${state.connectionState}")
        diagnostics.appendLine("Emergency Status: ${state.emergencyStatus}")
        diagnostics.appendLine("Paired Patient (caregiver side): ${state.pairedDevice?.endpointName ?: "None"}")
        diagnostics.appendLine("Paired Caregivers (patient side): ${state.pairedDevices.joinToString { it.endpointName }.ifBlank { "None" }}")
        diagnostics.appendLine("Connected Count: ${state.connectedCount}")
        diagnostics.appendLine("Last Heartbeat: ${if (state.lastHeartbeat > 0) state.lastHeartbeat else "Never"}")
        diagnostics.appendLine("")
        diagnostics.appendLine("Permissions:")
        diagnostics.appendLine("- Bluetooth Connect: ${checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED}")
        diagnostics.appendLine("- Bluetooth Advertise: ${checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED}")
        diagnostics.appendLine("- Bluetooth Scan: ${checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED}")
        diagnostics.appendLine("- Location Fine: ${checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            diagnostics.appendLine("- Nearby Wi-Fi: ${checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED}")
        }
        diagnostics.appendLine("- Notifications: ${checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED}")
        diagnostics.appendLine("")
        diagnostics.appendLine("Nearby Connections:")
        diagnostics.appendLine("- Is Connected: ${nearbyManager.isConnected()}")
        diagnostics.appendLine("- Live endpoints: ${nearbyManager.connectedEndpointsSnapshot().values.joinToString().ifBlank { "None" }}")

        diagnosticsText = diagnostics.toString()
        showDiagnostics = true
    }

    private fun clearEmergencyAndPairing() {
        // Clear emergency state
        stateManager.update {
            copy(
                emergencyStatus = EmergencyStatus.NONE,
                activeEmergencyEvent = null
            )
        }
        alarmManager.stopEmergencyAlarm()
    }

    @Composable
    fun MainScreen() {
        val state by stateManager.state.collectAsStateWithLifecycle()

        // Handle test alarm on resume
        androidx.compose.runtime.LaunchedEffect(key1 = testAlarmPending) {
            if (testAlarmPending) {
                triggerTestAlarm()
            }
        }

        // Diagnostics dialog
        if (showDiagnostics) {
            androidx.compose.runtime.LaunchedEffect(key1 = showDiagnostics) {
                // Auto-dismiss after showing
            }
        }

        // Track selected tab for bottom navigation
        val selectedTab = remember { mutableStateOf<NavigationTab>(NavigationTab.HOME) }

        val navHomeClick = {
            showConnection = false
            showSettings = false
            selectedTab.value = NavigationTab.HOME
        }
        val navConnectionClick = {
            showConnection = true
            showSettings = false
            selectedTab.value = NavigationTab.CONNECTION
        }
        val navIceClick = {
            showConnection = false
            showSettings = false
            selectedTab.value = NavigationTab.ICE
        }
        val navSettingsClick = {
            showSettings = true
            showConnection = false
            selectedTab.value = NavigationTab.SETTINGS
        }

        when {
            showRoleSelection -> RoleSelectionScreen(onRoleSelected = { role ->
                stateManager.setUserRole(role)
                lifecycleScope.launch(Dispatchers.IO) {
                    PreferencesManager.setUserRole(role)
                    PreferencesManager.setHasCompletedSetup(true)
                }
                showRoleSelection = false
                restartConnections()
            })

            showSettings -> SettingsScreen(
                onBackClick = { showSettings = false },
                onNavigateToConnection = {
                    showSettings = false
                    showConnection = true
                },
                onTestAlarmClick = { triggerTestAlarm() },
                onShowDiagnostics = { showDiagnostics() },
                onChangeRole = { changeRole() }
            )

            showConnection -> ConnectionScreen(
                onNavigateHome = navHomeClick,
                onNavigateToSettings = navSettingsClick,
                onStartOver = { showConnection = false }
            )

            showDiagnostics -> DiagnosticsDialog(
                text = diagnosticsText,
                onDismiss = { showDiagnostics = false }
            )

            state.userRole == UserRole.PATIENT -> PatientHomeScreen(
                onNavigateToConnection = navConnectionClick,
                onNavigateToSettings = navSettingsClick,
                onEmergency = { triggerEmergency() },
                onHomeClick = navHomeClick,
                onIceClick = navIceClick,
                selectedTab = selectedTab.value
            )

            state.userRole == UserRole.CAREGIVER -> CaregiverHomeScreen(
                onNavigateToConnection = navConnectionClick,
                onNavigateToSettings = navSettingsClick,
                onTestAlarm = { triggerTestAlarm() },
                selectedTab = selectedTab.value
            )

            else -> RoleSelectionScreen(onRoleSelected = { role ->
                stateManager.setUserRole(role)
                lifecycleScope.launch(Dispatchers.IO) {
                    PreferencesManager.setUserRole(role)
                    PreferencesManager.setHasCompletedSetup(true)
                }
                showRoleSelection = false
                restartConnections()
            })
        }
    }

    private fun changeRole() {
        // Cancel any ongoing reconnect, clear state, and return to role selection
        lifecycleScope.launch(Dispatchers.IO) {
            PreferencesManager.clearRoleAndConnection()
        }
        nearbyManager.cancelReconnectAndClearState()
        // No longer paired / no role — the background keep-alive has nothing to hold onto.
        ConnectionKeepAliveService.stop(this)
        stateManager.update {
            copy(
                userRole = UserRole.NONE,
                pairedDevice = null,
                pairedDevices = emptyList(),
                connectedCount = 0,
                connectionState = ConnectionState.Disconnected,
                emergencyStatus = EmergencyStatus.NONE,
                activeEmergencyEvent = null
            )
        }
        showRoleSelection = true
        showSettings = false
        showConnection = false
        showDiagnostics = false
    }

    @Composable
    fun DiagnosticsDialog(
    text: String,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connection Diagnostics") },
        text = {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .padding(16.dp)
            ) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = text,
                        fontSize = 12.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

}
