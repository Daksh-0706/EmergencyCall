package com.emergencycall.networking.transport

import android.util.Log
import com.emergencycall.data.EmergencyEvent
import com.emergencycall.data.EmergencyStateManager
import com.emergencycall.data.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * EmergencyRouter - Routes emergency events through the best available transport.
 *
 * Selection priority:
 * 1. Nearby Connections (primary - P2P, offline-capable)
 * 2. Local Wi-Fi (NSD/mDNS - same network fallback)
 * 3. Internet (cloud - requires backend, optional)
 *
 * Caregiver opt-in: Only delivers to caregivers who have `receiveEmergencyAlerts = true`
 * in their preferences.
 */
class EmergencyRouter(
    private val nearbyTransport: NearbyTransport,
    private val localWifiTransport: LocalWifiTransport,
    private val internetTransport: InternetTransport
) {

    private val transports = listOf(nearbyTransport, localWifiTransport, internetTransport)

    /**
     * Route an emergency event to the best available caregiver.
     * Tries transports in priority order, falls back on failure.
     */
    suspend fun routeEmergency(event: EmergencyEvent): TransportResult {
        // Get eligible caregivers across all transports
        val caregivers = getEligibleCaregivers()

        if (caregivers.isEmpty()) {
            return TransportResult(
                success = false,
                transportUsed = TransportType.NEARBY,
                errorMessage = "No eligible caregivers found (check opt-in settings)"
            )
        }

        // Select best caregiver: paired > signal quality > deterministic
        val bestCaregiver = selectBestCaregiver(caregivers)

        // Try each transport in priority order
        for (transport in transports) {
            if (!transport.isAvailable) continue

            // Check if this transport can reach the selected caregiver
            val transportCaregivers = transport.getAvailableCaregivers()
                .filter { it.id == bestCaregiver.id }

            if (transportCaregivers.isNotEmpty()) {
                val target = transportCaregivers.first()
                val result = transport.sendEmergency(event, target)

                if (result.success) {
                    Log.d("EmergencyRouter", "Emergency sent via ${transport.transportType} to ${target.name}")
                    return result
                } else {
                    Log.w("EmergencyRouter", "Transport ${transport.transportType} failed: ${result.errorMessage}, trying next...")
                }
            }
        }

        // All transports failed
        return TransportResult(
            success = false,
            transportUsed = transports.first().transportType,
            errorMessage = "All transports failed to deliver emergency"
        )
    }

    /**
     * Get transport status for UI display.
     */
    suspend fun getTransportStatus(): TransportStatus {
        val nearbyCaregivers = nearbyTransport.getAvailableCaregivers().count { it.isOptedIn }
        val localWifiCaregivers = localWifiTransport.getAvailableCaregivers().count { it.isOptedIn }
        val internetCaregivers = internetTransport.getAvailableCaregivers().count { it.isOptedIn }
        val totalEligible = nearbyCaregivers + localWifiCaregivers + internetCaregivers

        // Determine primary transport (first available with eligible caregivers)
        val primaryTransport = transports
            .firstOrNull { it.isAvailable && it.getAvailableCaregivers().any { it.isOptedIn } }
            ?.transportType

        return TransportStatus(
            nearbyAvailable = nearbyTransport.isAvailable,
            localWifiAvailable = localWifiTransport.isAvailable,
            internetAvailable = internetTransport.isAvailable,
            eligibleCaregiversNearby = totalEligible,
            primaryTransport = primaryTransport
        )
    }

    /**
     * Get all eligible caregivers across all transports.
     * Filters by opt-in preference (caregiver must have receiveEmergencyAlerts = true).
     */
    private suspend fun getEligibleCaregivers(): List<CaregiverCandidate> {
        val allCaregivers = mutableListOf<CaregiverCandidate>()

        for (transport in transports) {
            if (transport.isAvailable) {
                allCaregivers.addAll(transport.getAvailableCaregivers())
            }
        }

        // Filter by opt-in - check PreferencesManager for each caregiver
        // For now, we use the isOptedIn flag from the transport (which should check preferences)
        return allCaregivers.filter { it.isOptedIn }
            .distinctBy { it.id }
    }

    /**
     * Select the best caregiver from the eligible list.
     * Priority: paired device > signal quality > first (deterministic)
     */
    private fun selectBestCaregiver(caregivers: List<CaregiverCandidate>): CaregiverCandidate {
        return caregivers
            .maxByOrNull { it.isPaired }
            ?: caregivers.maxByOrNull { it.signalQuality }
            ?: caregivers.first()
    }

    /**
     * Start listening on all available transports.
     */
    suspend fun startListening(onEmergencyReceived: (EmergencyEvent) -> Unit) {
        for (transport in transports) {
            if (transport.isAvailable) {
                transport.startListening(onEmergencyReceived)
            }
        }
    }

    /**
     * Stop listening on all transports.
     */
    suspend fun stopListening() {
        for (transport in transports) {
            transport.stopListening()
        }
    }

    companion object {
        private const val TAG = "EmergencyRouter"
    }
}