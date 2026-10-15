package com.emergencycall.networking.transport

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import com.emergencycall.data.EmergencyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch

/**
 * Local Wi-Fi Transport using Android NSD (Network Service Discovery) / mDNS.
 *
 * This is a skeleton implementation for architecture purposes. Full NSD implementation
 * including service registration, discovery, and socket-based data transfer is deferred
 * to a follow-up pass.
 *
 * Current implementation provides:
 * - Availability check (Wi-Fi connected + NSD supported)
 * - Basic discovery skeleton
 * - Interface compliance for EmergencyRouter
 */
class LocalWifiTransport(private val context: Context) : EmergencyTransport {

    override val transportType: TransportType = TransportType.LOCAL_WIFI

    override val isAvailable: Boolean
        get() = checkWifiAvailable()

    private val nsdManager: NsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager: WifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val _incomingEmergencyChannel = Channel<EmergencyEvent>(Channel.UNLIMITED)
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var isListening = false
    private var isDiscovering = false
    private var registeredServiceName: String? = null

    private fun checkWifiAvailable(): Boolean {
        return try {
            wifiManager.isWifiEnabled && wifiManager.connectionInfo.supplicantState == android.net.wifi.SupplicantState.COMPLETED
        } catch (e: Exception) {
            Log.w("LocalWifiTransport", "Error checking Wi-Fi availability", e)
            false
        }
    }

    override suspend fun sendEmergency(event: EmergencyEvent, target: CaregiverCandidate): TransportResult {
        // TODO: Implement actual socket-based emergency sending
        // For now, return failure with clear message indicating not implemented
        Log.w("LocalWifiTransport", "sendEmergency not fully implemented - returning failure")
        return TransportResult(
            success = false,
            transportUsed = TransportType.LOCAL_WIFI,
            errorMessage = "Local Wi-Fi transport not fully implemented"
        )
    }

    override suspend fun startListening(onEmergencyReceived: (EmergencyEvent) -> Unit) {
        if (isListening) return
        isListening = true

        CoroutineScope(Dispatchers.IO).launch {
            // TODO: Implement NSD service registration for receiving emergencies
            // This would involve:
            // 1. Create NsdServiceInfo with service type "_emergencycall._tcp."
            // 2. Set port for incoming connections
            // 3. Register service with NsdManager
            // 4. Accept incoming socket connections
            // 5. Parse incoming data as EmergencyEvent
            // 6. Call onEmergencyReceived(event)

            Log.d("LocalWifiTransport", "Start listening (skeleton) - NSD registration not implemented")
        }
    }

    override suspend fun stopListening() {
        isListening = false
        // TODO: Unregister NSD service
        Log.d("LocalWifiTransport", "Stop listening (skeleton)")
    }

    override suspend fun getAvailableCaregivers(): List<CaregiverCandidate> {
        val candidates = mutableListOf<CaregiverCandidate>()

        // TODO: Implement NSD discovery to find other EmergencyCall devices on local network
        // This would involve:
        // 1. Start discovery for "_emergencycall._tcp." service type
        // 2. Collect discovered services
        // 3. Convert to CaregiverCandidate list

        Log.d("LocalWifiTransport", "getAvailableCaregivers (skeleton) - NSD discovery not implemented")

        return candidates
    }

    /**
     * Start NSD discovery for emergency services on local network.
     * Skeleton implementation - full discovery logic to be implemented in follow-up.
     */
    private fun startDiscovery() {
        if (isDiscovering) return

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.d("LocalWifiTransport", "NSD discovery started: $serviceType")
                isDiscovering = true
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d("LocalWifiTransport", "NSD service found: ${serviceInfo.serviceName}")
                // TODO: Resolve service and add to available caregivers
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d("LocalWifiTransport", "NSD service lost: ${serviceInfo.serviceName}")
                // TODO: Remove from available caregivers
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d("LocalWifiTransport", "NSD discovery stopped: $serviceType")
                isDiscovering = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e("LocalWifiTransport", "NSD discovery start failed: $errorCode")
                isDiscovering = false
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e("LocalWifiTransport", "NSD discovery stop failed: $errorCode")
            }
        }

        try {
            nsdManager.discoverServices("_emergencycall._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener!!)
        } catch (e: Exception) {
            Log.e("LocalWifiTransport", "Failed to start NSD discovery", e)
        }
    }

    /**
     * Stop NSD discovery.
     */
    private fun stopDiscovery() {
        discoveryListener?.let { listener ->
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (e: Exception) {
                Log.e("LocalWifiTransport", "Failed to stop NSD discovery", e)
            }
        }
        discoveryListener = null
        isDiscovering = false
    }

    /**
     * Register this device as an emergency service on the local network.
     * Skeleton implementation.
     */
    private fun registerService(port: Int, deviceName: String) {
        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                registeredServiceName = serviceInfo.serviceName
                Log.d("LocalWifiTransport", "NSD service registered: ${serviceInfo.serviceName}")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e("LocalWifiTransport", "NSD registration failed: $errorCode")
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                Log.d("LocalWifiTransport", "NSD service unregistered: ${serviceInfo.serviceName}")
                registeredServiceName = null
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e("LocalWifiTransport", "NSD unregistration failed: $errorCode")
            }
        }

        val serviceInfo = NsdServiceInfo().apply {
            serviceType = "_emergencycall._tcp."
            serviceName = deviceName
            setPort(port)
        }

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener!!)
        } catch (e: Exception) {
            Log.e("LocalWifiTransport", "Failed to register NSD service", e)
        }
    }

    /**
     * Unregister the NSD service.
     */
    private fun unregisterService() {
        if (registeredServiceName != null) {
            // We need the original serviceInfo to unregister
            // For now just log
            Log.d("LocalWifiTransport", "Unregister service (skeleton)")
            registeredServiceName = null
        }
    }
}