package com.emergencycall.networking.transport

import com.emergencycall.data.EmergencyEvent
import kotlinx.coroutines.channels.ReceiveChannel

interface EmergencyTransport {
    val transportType: TransportType
    val isAvailable: Boolean

    suspend fun sendEmergency(event: EmergencyEvent, target: CaregiverCandidate): TransportResult

    suspend fun startListening(onEmergencyReceived: (EmergencyEvent) -> Unit)

    suspend fun stopListening()

    suspend fun getAvailableCaregivers(): List<CaregiverCandidate>
}