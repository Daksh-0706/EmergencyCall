package com.emergencycall.networking

import android.content.Context
import android.util.Log
import com.emergencycall.R
import com.emergencycall.data.ConnectionState
import com.emergencycall.data.EmergencyEvent
import com.emergencycall.data.EmergencyStateManager
import com.emergencycall.data.EmergencyStatus
import com.emergencycall.data.NetworkMessage
import com.emergencycall.data.PairedDevice
import com.emergencycall.data.PreferencesManager
import com.emergencycall.data.UserRole
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionOptions
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

class NearbyManager private constructor(private val context: Context) {

    private val connectionsClient: ConnectionsClient = Nearby.getConnectionsClient(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val stateManager = EmergencyStateManager.getInstance()

    // Tolerant parser so a newer peer that adds fields (e.g. responderName) does
    // not crash decoding on an older build, and vice-versa.
    private val messageJson = Json { ignoreUnknownKeys = true }

    private val discoveredEndpoints = ConcurrentHashMap<String, DiscoveredEndpointInfo>()
    // endpointId -> endpointName for every LIVE connection. A patient can hold many;
    // a caregiver holds one (its patient).
    private val connectedEndpoints = ConcurrentHashMap<String, String>()
    // Endpoints we've called requestConnection on but haven't resolved yet — dedupes
    // repeated auto-connect attempts from onEndpointFound.
    private val connectingEndpoints: MutableSet<String> = ConcurrentHashMap.newKeySet()
    // Name learned in onConnectionInitiated, keyed by endpointId. The caregiver never
    // "discovers", so this is the only place it learns the patient's name.
    private val initiatedNames = ConcurrentHashMap<String, String>()
    private val pendingConnection = AtomicReference<String?>(null) // caregiver's incoming request id

    private val _discoveredEndpointsFlow = MutableStateFlow<Map<String, DiscoveredEndpointInfo>>(emptyMap())
    val discoveredEndpointsFlow: StateFlow<Map<String, DiscoveredEndpointInfo>> = _discoveredEndpointsFlow.asStateFlow()

    // Broadcast messages to every listener. A Channel would deliver each event
    // to only one collector, which could make emergency events disappear when
    // MainActivity and EmergencyRouter are both listening.
    private val _incomingPayloadFlow = MutableSharedFlow<NetworkMessage>(extraBufferCapacity = 64)
    val incomingPayloadFlow = _incomingPayloadFlow.asSharedFlow()

    private var advertisingJob: Job? = null
    private var discoveryJob: Job? = null
    private var reconnectJob: Job? = null
    // One sender loop broadcasts heartbeats to all connected endpoints; each endpoint
    // gets its own watchdog so one caregiver dropping doesn't tear down the others.
    private var heartbeatSenderJob: Job? = null
    private val watchdogJobs = ConcurrentHashMap<String, Job>()
    private val lastHeartbeatByEndpoint = ConcurrentHashMap<String, Long>()
    @Volatile private var isManualDisconnect = false

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            handlePayloadReceived(endpointId, payload)
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Track transfer progress if needed
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Log.d("NearbyManager", "Connection initiated with $endpointId: ${connectionInfo.endpointName}")
            initiatedNames[endpointId] = connectionInfo.endpointName
            val role = stateManager.state.value.userRole
            if (role == UserRole.CAREGIVER) {
                // If this is the patient we already know, accept silently (reconnect).
                val savedPatient = stateManager.state.value.pairedDevice
                if (savedPatient != null && savedPatient.endpointName == connectionInfo.endpointName) {
                    Log.d("NearbyManager", "Known patient re-connecting, auto-accepting")
                    connectionsClient.acceptConnection(endpointId, payloadCallback)
                        .addOnFailureListener { e -> Log.e("NearbyManager", "Auto-accept failed", e) }
                } else {
                    // First-time: surface the request so the caregiver can ACCEPT/REJECT.
                    pendingConnection.set(endpointId)
                    mainScope.launch {
                        stateManager.update {
                            copy(connectionState = ConnectionState.ConnectionRequested(endpointId, connectionInfo.endpointName))
                        }
                    }
                }
            } else {
                // Patient is the initiator: auto-accept.
                connectionsClient.acceptConnection(endpointId, payloadCallback)
                    .addOnFailureListener { e -> Log.e("NearbyManager", "Failed to accept connection", e) }
            }
        }

        override fun onConnectionResult(endpointId: String, connectionResolution: ConnectionResolution) {
            connectingEndpoints.remove(endpointId)
            if (connectionResolution.status.isSuccess) {
                Log.d("NearbyManager", "Connected to $endpointId")
                val name = connectedEndpoints[endpointId]
                    ?: initiatedNames[endpointId]
                    ?: discoveredEndpoints[endpointId]?.endpointName
                    ?: "Unknown"
                val wasReconnecting = stateManager.state.value.connectionState is ConnectionState.Reconnecting

                finalizeConnection(endpointId, name)

                // If we were reconnecting and there's a pending emergency waiting for ACK, resend it.
                if (wasReconnecting) {
                    val state = stateManager.state.value
                    if (state.emergencyStatus == EmergencyStatus.SENT_WAITING_ACK && state.activeEmergencyEvent != null) {
                        Log.d("NearbyManager", "Reconnection successful, resending pending emergency")
                        sendEmergency(state.activeEmergencyEvent!!)
                    }
                }
            } else {
                Log.e("NearbyManager", "Connection failed: ${connectionResolution.status.statusMessage}")
                handleDisconnection(endpointId, "Connection failed: ${connectionResolution.status.statusMessage}")
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d("NearbyManager", "Disconnected from $endpointId")
            handleDisconnection(endpointId, "Disconnected")
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, discoveredEndpointInfo: DiscoveredEndpointInfo) {
            Log.d("NearbyManager", "Found endpoint: $endpointId - ${discoveredEndpointInfo.endpointName}")
            discoveredEndpoints[endpointId] = discoveredEndpointInfo
            mainScope.launch {
                _discoveredEndpointsFlow.update { it + mapOf(endpointId to discoveredEndpointInfo) }
            }

            // Only the patient initiates. Auto-connect to a KNOWN caregiver (matched by
            // name) that isn't already connected or mid-connection. Unknown caregivers
            // stay in discoveredEndpointsFlow for manual first-time pairing.
            val state = stateManager.state.value
            if (state.userRole != UserRole.PATIENT) return
            val name = discoveredEndpointInfo.endpointName
            val known = state.pairedDevices.any { it.endpointName == name }
            val alreadyConnected = connectedEndpoints.containsKey(endpointId) ||
                connectedEndpoints.values.any { it == name }
            val inFlight = connectingEndpoints.contains(endpointId)
            if (known && !alreadyConnected && !inFlight) {
                Log.d("NearbyManager", "Found known caregiver '$name', auto-connecting")
                connectToEndpoint(endpointId, name)
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d("NearbyManager", "Lost endpoint: $endpointId")
            discoveredEndpoints.remove(endpointId)
            mainScope.launch {
                _discoveredEndpointsFlow.update { it - endpointId }
            }
        }
    }

    // ---- Advertising / discovery (role-split: patient discovers, caregiver advertises) ----

    suspend fun startAdvertising(role: UserRole, updateState: Boolean = true): Boolean {
        stopAdvertising()

        // Advertise under this device's own name so a discovering patient can tell
        // caregivers apart (and reconnect by name). serviceId stays the package name.
        val advertisedName = stateManager.state.value.deviceName.ifBlank { "Device" }
        val options = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        return try {
            val task = connectionsClient.startAdvertising(
                advertisedName,
                context.packageName,
                connectionLifecycleCallback,
                options
            )
            suspendCancellableCoroutine<Boolean> { cont ->
                task.addOnSuccessListener { cont.resume(true) }
                    .addOnFailureListener { e -> cont.resumeWithException(e) }
            }
            Log.d("NearbyManager", "Advertising started as ${role.name} ('$advertisedName')")
            advertisingJob = scope.launch { }
            if (updateState && connectedEndpoints.isEmpty()) {
                mainScope.launch {
                    stateManager.update {
                        if (connectionState is ConnectionState.Connected) this
                        else copy(connectionState = if (role == UserRole.CAREGIVER) ConnectionState.Waiting else ConnectionState.Searching)
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e("NearbyManager", "Advertising failed", e)
            if (updateState && connectedEndpoints.isEmpty()) {
                mainScope.launch {
                    stateManager.update { copy(connectionState = ConnectionState.Failed("Advertising failed: ${e.message}")) }
                }
            }
            false
        }
    }

    fun stopAdvertising() {
        advertisingJob?.cancel()
        advertisingJob = null
        connectionsClient.stopAdvertising()
        Log.d("NearbyManager", "Advertising stopped")
    }

    suspend fun startDiscovery(role: UserRole, updateState: Boolean = true): Boolean {
        stopDiscovery()

        val options = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        return try {
            val task = connectionsClient.startDiscovery(
                context.packageName,
                endpointDiscoveryCallback,
                options
            )
            suspendCancellableCoroutine<Boolean> { cont ->
                task.addOnSuccessListener { cont.resume(true) }
                    .addOnFailureListener { e -> cont.resumeWithException(e) }
            }
            Log.d("NearbyManager", "Discovery started for ${role.name}")
            discoveryJob = scope.launch { }
            if (updateState && role == UserRole.PATIENT && connectedEndpoints.isEmpty()) {
                mainScope.launch {
                    stateManager.update {
                        if (connectionState is ConnectionState.Connected) this
                        else copy(connectionState = ConnectionState.Searching)
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e("NearbyManager", "Discovery failed", e)
            if (updateState && connectedEndpoints.isEmpty()) {
                mainScope.launch {
                    stateManager.update { copy(connectionState = ConnectionState.Failed("Discovery failed: ${e.message}")) }
                }
            }
            false
        }
    }

    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        connectionsClient.stopDiscovery()
        discoveredEndpoints.clear()
        _discoveredEndpointsFlow.update { emptyMap() }
        Log.d("NearbyManager", "Discovery stopped")
    }

    /** Patient initiates a connection to a discovered caregiver. */
    fun connectToEndpoint(endpointId: String, endpointName: String) {
        if (connectedEndpoints.containsKey(endpointId) || !connectingEndpoints.add(endpointId)) {
            return // already connected or a request is already in flight
        }
        // Show the transient "Connecting" state only when nothing else is connected,
        // so adding a 2nd caregiver doesn't flip an existing Connected aggregate back.
        if (connectedEndpoints.isEmpty()) {
            mainScope.launch {
                stateManager.update { copy(connectionState = ConnectionState.Connecting(endpointName)) }
            }
        }

        val myName = stateManager.state.value.deviceName.ifBlank { "Patient" }
        connectionsClient.requestConnection(myName, endpointId, connectionLifecycleCallback)
            .addOnFailureListener { e ->
                Log.e("NearbyManager", "Connection request failed", e)
                connectingEndpoints.remove(endpointId)
                if (connectedEndpoints.isEmpty()) {
                    mainScope.launch {
                        stateManager.update { copy(connectionState = ConnectionState.Failed("Connection failed: ${e.message}")) }
                    }
                }
            }
    }

    /** Called when patient taps a discovered device. */
    fun initiateConnection(endpointId: String, endpointName: String) {
        stateManager.update {
            copy(connectionState = ConnectionState.DeviceFound(endpointId, endpointName))
        }
    }

    /** Manual "disconnect all / re-pair" — tears down every link and clears the pairing. */
    fun disconnect() {
        isManualDisconnect = true
        stopAllHeartbeats()
        stopReconnect()
        stopAdvertising()
        stopDiscovery()
        connectedEndpoints.keys.forEach { endpointId ->
            connectionsClient.disconnectFromEndpoint(endpointId)
        }
        connectedEndpoints.clear()
        connectingEndpoints.clear()
        initiatedNames.clear()
        pendingConnection.set(null)
        mainScope.launch {
            stateManager.update {
                copy(
                    connectionState = ConnectionState.Disconnected,
                    pairedDevice = null,
                    pairedDevices = emptyList(),
                    connectedCount = 0
                )
            }
        }
        // Let the onDisconnected callbacks drain under the manual flag, then re-arm.
        scope.launch {
            delay(1500)
            isManualDisconnect = false
        }
    }

    /** Caregiver accepts an incoming (first-time) connection request. */
    fun acceptConnection(endpointId: String) {
        val id = pendingConnection.getAndSet(null) ?: endpointId
        connectionsClient.acceptConnection(id, payloadCallback)
            .addOnSuccessListener {
                // Optimistically finalize. Nearby normally delivers onConnectionResult(success)
                // to both sides, but over BLE the responder's callback can be missed, which
                // previously left the caregiver stuck on "Connecting".
                val name = initiatedNames[id] ?: discoveredEndpoints[id]?.endpointName ?: "Patient"
                finalizeConnection(id, name)
            }
            .addOnFailureListener { e ->
                Log.e("NearbyManager", "Failed to accept connection", e)
                mainScope.launch {
                    stateManager.update { copy(connectionState = ConnectionState.Failed("Could not accept: ${e.message}")) }
                }
            }
    }

    /** Caregiver rejects an incoming connection request and returns to waiting. */
    fun rejectConnection(endpointId: String) {
        val id = pendingConnection.getAndSet(null) ?: endpointId
        try {
            connectionsClient.rejectConnection(id)
        } catch (e: Exception) {
            Log.e("NearbyManager", "Failed to reject connection", e)
        }
        mainScope.launch {
            stateManager.update { copy(connectionState = ConnectionState.Waiting) }
        }
    }

    /**
     * Records a live connection: updates the map, persists the pairing per role,
     * refreshes the aggregate state, and (re)starts heartbeat + this endpoint's watchdog.
     * Shared by onConnectionResult, acceptConnection and the payload-promotion path so
     * they can't drift apart.
     */
    private fun finalizeConnection(endpointId: String, name: String) {
        connectedEndpoints[endpointId] = name
        connectingEndpoints.remove(endpointId)
        mainScope.launch { _discoveredEndpointsFlow.update { it - endpointId } }

        val role = stateManager.state.value.userRole
        val dev = PairedDevice(endpointId, name)
        if (role == UserRole.PATIENT) {
            stateManager.addPairedCaregiver(dev)
            scope.launch { PreferencesManager.addPairedCaregiver(dev) }
        } else if (role == UserRole.CAREGIVER) {
            mainScope.launch { stateManager.update { copy(pairedDevice = dev) } }
            scope.launch { PreferencesManager.setPairedDevice(dev) }
        }

        recomputeAggregateState()
        startHeartbeatSender()
        startWatchdog(endpointId)
        lastHeartbeatByEndpoint[endpointId] = System.currentTimeMillis()
        maybeStopReconnect()
    }

    /** Push the current connected count + label into the shared state. */
    private fun recomputeAggregateState() {
        val count = connectedEndpoints.size
        if (count == 0) {
            mainScope.launch { stateManager.update { copy(connectedCount = 0) } }
            return
        }
        val firstId = connectedEndpoints.keys.first()
        val label = if (count == 1) connectedEndpoints.values.first() else "$count caregivers"
        mainScope.launch {
            stateManager.update {
                copy(connectionState = ConnectionState.Connected(firstId, label), connectedCount = count)
            }
        }
    }

    fun sendMessage(message: NetworkMessage): Boolean {
        val jsonStr = messageJson.encodeToString(NetworkMessage.serializer(), message)
        val payload = Payload.fromBytes(jsonStr.toByteArray())

        val endpoints = connectedEndpoints.keys.toList()
        Log.d("NearbyManager", "Sending ${message.javaClass.simpleName} to ${endpoints.size} endpoint(s)")
        endpoints.forEach { endpointId ->
            connectionsClient.sendPayload(endpointId, payload)
                .addOnSuccessListener {
                    Log.d("NearbyManager", "Message sent: ${message.javaClass.simpleName}")
                }
                .addOnFailureListener { e ->
                    Log.e("NearbyManager", "Failed to send message", e)
                }
        }
        return endpoints.isNotEmpty()
    }

    fun sendEmergency(event: EmergencyEvent) {
        sendMessage(NetworkMessage.Emergency(event))
    }

    fun sendAcknowledged(eventId: String, responderName: String = "") {
        sendMessage(NetworkMessage.Acknowledged(
            com.emergencycall.data.AcknowledgedEvent(eventId = eventId, responderName = responderName)
        ))
    }

    private fun handlePayloadReceived(endpointId: String, payload: Payload) {
        // A payload proves the link is up. On the responder side onConnectionResult(success)
        // can be missed over BLE, so promote an unknown endpoint to Connected here.
        if (!connectedEndpoints.containsKey(endpointId)) {
            val name = initiatedNames[endpointId] ?: discoveredEndpoints[endpointId]?.endpointName ?: "device"
            finalizeConnection(endpointId, name)
        }
        if (payload.type == Payload.Type.BYTES) {
            val bytes = payload.asBytes() ?: return
            val json = String(bytes)
            try {
                val message = messageJson.decodeFromString(NetworkMessage.serializer(), json)
                if (message is NetworkMessage.Heartbeat) {
                    lastHeartbeatByEndpoint[endpointId] = System.currentTimeMillis()
                    stateManager.updateLastHeartbeat()
                } else {
                    mainScope.launch {
                        _incomingPayloadFlow.emit(message)
                    }
                }
            } catch (e: Exception) {
                Log.e("NearbyManager", "Failed to parse payload", e)
            }
        }
    }

    // ---- Heartbeat: one broadcast sender + a per-endpoint inbound watchdog ----

    private fun startHeartbeatSender() {
        if (heartbeatSenderJob?.isActive == true) return
        heartbeatSenderJob = scope.launch {
            while (connectedEndpoints.isNotEmpty()) {
                delay(10000)
                if (connectedEndpoints.isNotEmpty()) {
                    sendMessage(NetworkMessage.Heartbeat(com.emergencycall.data.HeartbeatEvent()))
                }
            }
        }
    }

    private fun startWatchdog(endpointId: String) {
        watchdogJobs.remove(endpointId)?.cancel()
        lastHeartbeatByEndpoint[endpointId] = System.currentTimeMillis()
        watchdogJobs[endpointId] = scope.launch {
            while (connectedEndpoints.containsKey(endpointId)) {
                delay(5000)
                if (!connectedEndpoints.containsKey(endpointId)) break
                val last = lastHeartbeatByEndpoint[endpointId] ?: System.currentTimeMillis()
                if (System.currentTimeMillis() - last > 25000) {
                    Log.w("NearbyManager", "Heartbeat timeout for $endpointId, dropping it")
                    handleDisconnection(endpointId, "Heartbeat timeout")
                    break
                }
            }
        }
    }

    private fun stopAllHeartbeats() {
        heartbeatSenderJob?.cancel()
        heartbeatSenderJob = null
        watchdogJobs.values.forEach { it.cancel() }
        watchdogJobs.clear()
        lastHeartbeatByEndpoint.clear()
    }

    /** Drops a SINGLE endpoint. Other live connections are left untouched. */
    private fun handleDisconnection(endpointId: String, reason: String) {
        Log.d("NearbyManager", "handleDisconnection($endpointId): $reason")
        connectedEndpoints.remove(endpointId)
        connectingEndpoints.remove(endpointId)
        watchdogJobs.remove(endpointId)?.cancel()
        lastHeartbeatByEndpoint.remove(endpointId)
        initiatedNames.remove(endpointId)
        if (pendingConnection.get() == endpointId) pendingConnection.set(null)

        val manual = isManualDisconnect

        if (connectedEndpoints.isEmpty()) {
            // Nothing connected any more — stop the shared sender.
            heartbeatSenderJob?.cancel()
            heartbeatSenderJob = null
            if (manual) {
                mainScope.launch {
                    stateManager.update { copy(connectionState = ConnectionState.Disconnected, connectedCount = 0) }
                }
            } else {
                mainScope.launch {
                    stateManager.update {
                        copy(
                            connectedCount = 0,
                            connectionState = if (connectionState is ConnectionState.Reconnecting) connectionState
                            else ConnectionState.Disconnected
                        )
                    }
                }
                if (hasKnownDevicesToReconnect()) maybeStartReconnect()
            }
        } else {
            // Others remain; refresh the aggregate label/count and, if this was an
            // unexpected drop of a known device, try to get it back.
            recomputeAggregateState()
            if (!manual && hasKnownDevicesToReconnect()) maybeStartReconnect()
        }
    }

    // ---- Reconnect: lightweight keep-alive that keeps the role channel active ----

    private fun hasKnownDevicesToReconnect(): Boolean {
        val s = stateManager.state.value
        return when (s.userRole) {
            UserRole.PATIENT -> s.pairedDevices.isNotEmpty()
            UserRole.CAREGIVER -> s.pairedDevice != null
            else -> false
        }
    }

    private fun allKnownConnected(): Boolean {
        val s = stateManager.state.value
        return when (s.userRole) {
            UserRole.PATIENT -> s.pairedDevices.isNotEmpty() && connectedEndpoints.size >= s.pairedDevices.size
            UserRole.CAREGIVER -> connectedEndpoints.isNotEmpty()
            else -> true
        }
    }

    private fun firstMissingName(): String? {
        val s = stateManager.state.value
        return if (s.userRole == UserRole.PATIENT) {
            val connectedNames = connectedEndpoints.values.toSet()
            s.pairedDevices.firstOrNull { it.endpointName !in connectedNames }?.endpointName
        } else {
            s.pairedDevice?.endpointName
        }
    }

    fun maybeStartReconnect() {
        if (isManualDisconnect) return
        if (!hasKnownDevicesToReconnect() || allKnownConnected()) return
        if (reconnectJob?.isActive == true) return
        startReconnect()
    }

    private fun maybeStopReconnect() {
        if (allKnownConnected()) stopReconnect()
    }

    private fun startReconnect() {
        stopReconnect()
        val role = stateManager.state.value.userRole
        reconnectJob = scope.launch {
            // Bring the role channel up: patient discovers (onEndpointFound auto-connects
            // known caregivers), caregiver advertises (onConnectionInitiated auto-accepts
            // the known patient).
            if (role == UserRole.PATIENT) startDiscovery(UserRole.PATIENT, updateState = false)
            else startAdvertising(UserRole.CAREGIVER, updateState = false)

            var backoff = 1000L
            val maxBackoff = 30000L
            while (!isManualDisconnect && hasKnownDevicesToReconnect() && !allKnownConnected()) {
                if (connectedEndpoints.isEmpty()) {
                    val name = firstMissingName() ?: "device"
                    mainScope.launch {
                        stateManager.update {
                            if (connectionState is ConnectionState.Connected) this
                            else copy(connectionState = ConnectionState.Reconnecting(name))
                        }
                    }
                }
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(maxBackoff)
                // Re-assert the channel in case Android tore it down while idle.
                if (!isManualDisconnect && !allKnownConnected()) {
                    if (role == UserRole.PATIENT) startDiscovery(UserRole.PATIENT, updateState = false)
                    else startAdvertising(UserRole.CAREGIVER, updateState = false)
                }
            }

            if (connectedEndpoints.isEmpty() && !isManualDisconnect && hasKnownDevicesToReconnect()) {
                mainScope.launch {
                    stateManager.update {
                        if (connectionState is ConnectionState.Connected) this
                        else copy(connectionState = ConnectionState.Disconnected)
                    }
                }
            }
        }
    }

    private fun stopReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
    }

    // ---- Accessors ----

    fun isConnected(): Boolean = connectedEndpoints.isNotEmpty()

    fun getConnectedEndpointName(): String? = connectedEndpoints.values.firstOrNull()

    fun connectedCount(): Int = connectedEndpoints.size

    /** Snapshot of endpointId -> name for every live connection (for the router/UI). */
    fun connectedEndpointsSnapshot(): Map<String, String> = HashMap(connectedEndpoints)

    fun shutdown() {
        scope.cancel()
        mainScope.cancel()
        stopAdvertising()
        stopDiscovery()
        stopAllHeartbeats()
        stopReconnect()
        connectionsClient.stopAllEndpoints()
        connectedEndpoints.clear()
        connectingEndpoints.clear()
        initiatedNames.clear()
        pendingConnection.set(null)
    }

    /** Cancel reconnect loop and clear connection state (used on role change). */
    fun cancelReconnectAndClearState() {
        isManualDisconnect = true
        stopReconnect()
        stopAllHeartbeats()
        stopAdvertising()
        stopDiscovery()
        connectedEndpoints.clear()
        connectingEndpoints.clear()
        initiatedNames.clear()
        pendingConnection.set(null)
        mainScope.launch {
            stateManager.update {
                copy(connectionState = ConnectionState.Disconnected, connectedCount = 0)
            }
        }
        isManualDisconnect = false
    }

    companion object {
        @Volatile private var INSTANCE: NearbyManager? = null

        fun getInstance(context: Context): NearbyManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NearbyManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun destroyInstance() {
            INSTANCE?.shutdown()
            INSTANCE = null
        }
    }
}
