package com.emergencycall.networking.transport

import com.emergencycall.data.EmergencyEvent
import com.emergencycall.data.NetworkMessage
import com.emergencycall.data.PairedDevice
import com.emergencycall.data.PreferencesManager
import com.emergencycall.data.UserRole
import com.emergencycall.networking.NearbyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NearbyTransport(private val nearbyManager: NearbyManager) : EmergencyTransport {

    override val transportType: TransportType = TransportType.NEARBY

    override val isAvailable: Boolean
        get() = nearbyManager.isConnected()

    private val _incomingEmergencyChannel = Channel<EmergencyEvent>(Channel.UNLIMITED)
    private var isListening = false

    override suspend fun sendEmergency(event: EmergencyEvent, target: CaregiverCandidate): TransportResult {
        // Send via NearbyManager - the target ID should match the connected endpoint
        // sendEmergency returns Unit, so we assume success if no exception thrown
        return try {
            nearbyManager.sendEmergency(event)
            TransportResult(
                success = true,
                transportUsed = TransportType.NEARBY,
                errorMessage = null
            )
        } catch (e: Exception) {
            TransportResult(
                success = false,
                transportUsed = TransportType.NEARBY,
                errorMessage = "Failed to send via Nearby: ${e.message}"
            )
        }
    }

    override suspend fun startListening(onEmergencyReceived: (EmergencyEvent) -> Unit) {
        if (isListening) return
        isListening = true

        // Collect from NearbyManager's incoming payload flow
        CoroutineScope(Dispatchers.IO).launch {
            nearbyManager.incomingPayloadFlow.collect { message ->
                when (message) {
                    is NetworkMessage.Emergency -> onEmergencyReceived(message.event)
                    else -> {} // Ignore other message types
                }
            }
        }
    }

    override suspend fun stopListening() {
        isListening = false
        // Channel will be closed when NearbyManager shuts down
    }

    override suspend fun getAvailableCaregivers(): List<CaregiverCandidate> {
        val candidates = mutableListOf<CaregiverCandidate>()

        val stateManager = com.emergencycall.data.EmergencyStateManager.getInstance()
        val state = stateManager.state.value
        val userRole = state.userRole

        // Get caregiver opt-in preference (suspend function)
        val receiveAlerts = PreferencesManager.getReceiveEmergencyAlerts()

        if (userRole == UserRole.PATIENT) {
            // Every LIVE caregiver connection is a delivery target. These are the ones
            // that actually get the emergency broadcast, so they rank highest.
            val connected = nearbyManager.connectedEndpointsSnapshot()
            val connectedNames = connected.values.toSet()
            connected.forEach { (endpointId, name) ->
                candidates.add(CaregiverCandidate(
                    id = endpointId,
                    name = name,
                    transportType = TransportType.NEARBY,
                    signalQuality = 100,
                    isPaired = true,
                    isOptedIn = receiveAlerts
                ))
            }

            // Known-but-not-currently-connected caregivers (so the router still reports
            // targets while a reconnect is mid-flight).
            state.pairedDevices.filter { it.endpointName !in connectedNames }.forEach { dev ->
                candidates.add(CaregiverCandidate(
                    id = dev.endpointId,
                    name = dev.endpointName,
                    transportType = TransportType.NEARBY,
                    signalQuality = 90,
                    isPaired = true,
                    isOptedIn = receiveAlerts
                ))
            }

            // Discovered but not yet paired/connected (first-time pairing candidates).
            nearbyManager.discoveredEndpointsFlow.value.forEach { (endpointId, endpointInfo) ->
                if (endpointInfo.endpointName !in connectedNames &&
                    state.pairedDevices.none { it.endpointName == endpointInfo.endpointName }) {
                    candidates.add(CaregiverCandidate(
                        id = endpointId,
                        name = endpointInfo.endpointName,
                        transportType = TransportType.NEARBY,
                        signalQuality = 80,
                        isPaired = false,
                        isOptedIn = receiveAlerts
                    ))
                }
            }
        } else if (userRole == UserRole.CAREGIVER) {
            // Caregiver mode - we can accept connections, but we don't "send" emergencies.
            // We just report availability of the linked patient.
            val connectedName = nearbyManager.getConnectedEndpointName()
            connectedName?.let { name ->
                candidates.add(CaregiverCandidate(
                    id = "caregiver_self",
                    name = name,
                    transportType = TransportType.NEARBY,
                    signalQuality = 100,
                    isPaired = state.pairedDevice != null,
                    isOptedIn = receiveAlerts
                ))
            }
        }

        return candidates
    }
}