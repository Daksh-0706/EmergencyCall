package com.emergencycall.networking.transport

import com.emergencycall.data.EmergencyEvent

enum class TransportType {
    NEARBY,
    LOCAL_WIFI,
    INTERNET
}

data class CaregiverCandidate(
    val id: String,
    val name: String,
    val transportType: TransportType,
    val signalQuality: Int = 0, // 0-100, for selection heuristic
    val isPaired: Boolean = false,
    val isOptedIn: Boolean = true
)

data class TransportResult(
    val success: Boolean,
    val transportUsed: TransportType,
    val errorMessage: String? = null
)

data class TransportStatus(
    val nearbyAvailable: Boolean = false,
    val localWifiAvailable: Boolean = false,
    val internetAvailable: Boolean = false,
    val eligibleCaregiversNearby: Int = 0,
    val primaryTransport: TransportType? = null
)