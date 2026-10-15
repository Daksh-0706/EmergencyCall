package com.emergencycall.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.os.Handler
import android.os.Looper

enum class UserRole {
    NONE, PATIENT, CAREGIVER
}

enum class EmergencyStatus {
    NONE,
    ACTIVE,
    SENT_WAITING_ACK,
    ACKNOWLEDGED
}

@Serializable
data class PairedDevice(
    val endpointId: String,
    val endpointName: String,
    val pairedAt: Long = System.currentTimeMillis()
)

data class EmergencyState(
    val userRole: UserRole = UserRole.NONE,
    val patientName: String = "Patient",
    val deviceName: String = "My Device",
    // Caregiver side: the single paired patient. Patient side: kept for
    // backward-compat / migration; the authoritative list is [pairedDevices].
    val pairedDevice: PairedDevice? = null,
    // Patient side: every known caregiver (upserted by name). A patient can be
    // paired to multiple caregivers at once.
    val pairedDevices: List<PairedDevice> = emptyList(),
    // How many of those caregivers are connected right now (drives the aggregate
    // "N caregivers connected" label).
    val connectedCount: Int = 0,
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val emergencyStatus: EmergencyStatus = EmergencyStatus.NONE,
    val activeEmergencyEvent: EmergencyEvent? = null,
    // Name of the caregiver who acknowledged first (shown on the patient's
    // ACKNOWLEDGED screen). Cleared together with the emergency.
    val responderName: String = "",
    val vibrationEnabled: Boolean = true,
    val lastHeartbeat: Long = 0
)

class EmergencyStateManager private constructor() {

    private val _state = MutableStateFlow(EmergencyState())
    val state: StateFlow<EmergencyState> = _state.asStateFlow()

    fun update(block: EmergencyState.() -> EmergencyState) {
        _state.update { it.block() }
    }

    fun setUserRole(role: UserRole) {
        update { copy(userRole = role) }
    }

    fun setPatientName(name: String) {
        update { copy(patientName = name) }
    }

    fun setDeviceName(name: String) {
        update { copy(deviceName = name) }
    }

    fun setPairedDevice(device: PairedDevice?) {
        update { copy(pairedDevice = device) }
    }

    /** Replace the patient's known-caregivers list wholesale (used on load/migration). */
    fun setPairedCaregivers(list: List<PairedDevice>) {
        update { copy(pairedDevices = list) }
    }

    /** Upsert a caregiver into the patient's known list, keyed by endpoint name. */
    fun addPairedCaregiver(device: PairedDevice) {
        update {
            val list = pairedDevices.toMutableList()
            val idx = list.indexOfFirst { it.endpointName == device.endpointName }
            if (idx >= 0) list[idx] = device else list.add(device)
            copy(pairedDevices = list)
        }
    }

    fun removePairedCaregiverByName(name: String) {
        update { copy(pairedDevices = pairedDevices.filterNot { it.endpointName == name }) }
    }

    fun setConnectedCount(count: Int) {
        update { copy(connectedCount = count) }
    }

    fun setConnectionState(state: ConnectionState) {
        update { copy(connectionState = state) }
    }

    fun setEmergencyStatus(status: EmergencyStatus) {
        update { copy(emergencyStatus = status) }
        // Persist emergency status
        CoroutineScope(Dispatchers.IO).launch {
            PreferencesManager.setEmergencyStatus(status)
        }
    }

    fun setActiveEmergencyEvent(event: EmergencyEvent?) {
        update { copy(activeEmergencyEvent = event) }
        // Persist active emergency event
        CoroutineScope(Dispatchers.IO).launch {
            PreferencesManager.setActiveEmergencyEvent(event)
        }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        update { copy(vibrationEnabled = enabled) }
    }

    fun updateLastHeartbeat() {
        update { copy(lastHeartbeat = System.currentTimeMillis()) }
    }

    fun clearEmergency() {
        update {
            copy(
                emergencyStatus = EmergencyStatus.NONE,
                activeEmergencyEvent = null,
                responderName = ""
            )
        }
        // Clear persisted emergency state
        CoroutineScope(Dispatchers.IO).launch {
            PreferencesManager.setEmergencyStatus(EmergencyStatus.NONE)
            PreferencesManager.setActiveEmergencyEvent(null)
        }
    }

    /** Load persisted emergency state from PreferencesManager */
    fun loadEmergencyState() {
        CoroutineScope(Dispatchers.IO).launch {
            val emergencyStatus = PreferencesManager.getEmergencyStatus()
            val activeEmergencyEvent = PreferencesManager.getActiveEmergencyEvent()
            // Use Handler to post to main thread
            Handler(Looper.getMainLooper()).post {
                _state.update { currentState ->
                    currentState.copy(
                        emergencyStatus = emergencyStatus,
                        activeEmergencyEvent = activeEmergencyEvent
                    )
                }
            }
        }
    }

    companion object {
        @Volatile private var INSTANCE: EmergencyStateManager? = null

        fun getInstance(): EmergencyStateManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: EmergencyStateManager().also { INSTANCE = it }
            }
        }
    }
}