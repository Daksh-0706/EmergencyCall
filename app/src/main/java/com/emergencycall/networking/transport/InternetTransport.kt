package com.emergencycall.networking.transport

import com.emergencycall.data.EmergencyEvent
import android.util.Log

/**
 * Internet Transport - Architecture only, no backend implementation.
 *
 * This transport provides an extensibility point for future cloud-based
 * emergency delivery (e.g., via Firebase Cloud Messaging, WebSocket server,
 * or custom backend).
 *
 * Current implementation:
 * - isAvailable returns false (no backend configured)
 * - All operations return "not implemented" failures
 * - Documented as extension point for future work
 */
class InternetTransport : EmergencyTransport {

    override val transportType: TransportType = TransportType.INTERNET

    override val isAvailable: Boolean
        get() = false // No backend configured in this pass

    override suspend fun sendEmergency(event: EmergencyEvent, target: CaregiverCandidate): TransportResult {
        Log.w("InternetTransport", "sendEmergency called but no backend configured")
        return TransportResult(
            success = false,
            transportUsed = TransportType.INTERNET,
            errorMessage = "Internet transport not configured - no backend available"
        )
    }

    override suspend fun startListening(onEmergencyReceived: (EmergencyEvent) -> Unit) {
        Log.d("InternetTransport", "startListening called but no backend configured")
        // TODO: Implement when backend is available
        // Would typically:
        // 1. Connect to WebSocket/FCM/Webhook endpoint
        // 2. Register this device for incoming emergencies
        // 3. Set up listener for push notifications
    }

    override suspend fun stopListening() {
        Log.d("InternetTransport", "stopListening called")
        // TODO: Disconnect from backend
    }

    override suspend fun getAvailableCaregivers(): List<CaregiverCandidate> {
        // No backend = no caregivers available via internet
        return emptyList()
    }

    /**
     * Extension point: Configure backend connection.
     * To be implemented when backend infrastructure exists.
     */
    fun configureBackend(baseUrl: String, apiKey: String) {
        Log.d("InternetTransport", "Backend configuration requested: $baseUrl")
        // TODO: Store configuration and enable isAvailable when properly connected
    }

    /**
     * Extension point: Register device with backend for push notifications.
     * To be implemented when backend infrastructure exists.
     */
    fun registerForPushNotifications(token: String) {
        Log.d("InternetTransport", "Push registration requested")
        // TODO: Send FCM/device token to backend
    }
}