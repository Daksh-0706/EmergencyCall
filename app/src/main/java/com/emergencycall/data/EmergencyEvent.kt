package com.emergencycall.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class EmergencyEvent(
    val type: String = "EMERGENCY",
    val eventId: String = UUID.randomUUID().toString(),
    val patientId: String,
    val patientName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val version: Int = 1
)

@Serializable
data class AcknowledgedEvent(
    val type: String = "ACKNOWLEDGED",
    val eventId: String,
    // Name of the caregiver who responded first. Defaulted so payloads from
    // older builds (which don't send this field) still decode.
    val responderName: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val version: Int = 1
)

@Serializable
data class HeartbeatEvent(
    val type: String = "HEARTBEAT",
    val timestamp: Long = System.currentTimeMillis(),
    val version: Int = 1
)

@Serializable
sealed class NetworkMessage {
    @Serializable
    data class Emergency(val event: EmergencyEvent) : NetworkMessage()
    @Serializable
    data class Acknowledged(val event: AcknowledgedEvent) : NetworkMessage()
    @Serializable
    data class Heartbeat(val event: HeartbeatEvent) : NetworkMessage()
    @Serializable
    data class PairingRequest(val deviceName: String) : NetworkMessage()
    @Serializable
    data class PairingResponse(val accepted: Boolean, val deviceName: String) : NetworkMessage()
}

sealed class ConnectionState {
    object NotConnected : ConnectionState()                    // Initial state, no pairing
    object Waiting : ConnectionState()                          // Caregiver: waiting for patient
    object Searching : ConnectionState()                        // Patient: actively searching
    data class DeviceFound(val endpointId: String, val endpointName: String) : ConnectionState() // Patient found a device
    data class ConnectionRequested(val endpointId: String, val endpointName: String) : ConnectionState() // Caregiver: incoming request
    data class Connecting(val endpointName: String) : ConnectionState()   // Connection in progress
    data class Connected(val endpointId: String, val endpointName: String) : ConnectionState()
    object Disconnected : ConnectionState()                     // Was paired, now disconnected
    data class Reconnecting(val endpointName: String) : ConnectionState() // Auto-reconnect attempt
    data class Failed(val reason: String) : ConnectionState()   // Explicit failure
}