package com.emergencycall.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

object PreferencesManager {
    private const val KEY_USER_ROLE = "user_role"
    private const val KEY_PATIENT_NAME = "patient_name"
    private const val KEY_DEVICE_NAME = "device_name"
    private const val KEY_PAIRED_DEVICE = "paired_device"
    private const val KEY_PAIRED_CAREGIVERS = "paired_caregivers"
    private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
    private const val KEY_HAS_COMPLETED_SETUP = "has_completed_setup"
    private const val KEY_EMERGENCY_STATUS = "emergency_status"
    private const val KEY_ACTIVE_EMERGENCY_EVENT = "active_emergency_event"
    private const val KEY_RECEIVE_EMERGENCY_ALERTS = "receive_emergency_alerts"

    private val USER_ROLE_KEY = stringPreferencesKey(KEY_USER_ROLE)
    private val PATIENT_NAME_KEY = stringPreferencesKey(KEY_PATIENT_NAME)
    private val DEVICE_NAME_KEY = stringPreferencesKey(KEY_DEVICE_NAME)
    private val PAIRED_DEVICE_KEY = stringPreferencesKey(KEY_PAIRED_DEVICE)
    private val PAIRED_CAREGIVERS_KEY = stringPreferencesKey(KEY_PAIRED_CAREGIVERS)
    private val VIBRATION_ENABLED_KEY = booleanPreferencesKey(KEY_VIBRATION_ENABLED)
    private val HAS_COMPLETED_SETUP_KEY = booleanPreferencesKey(KEY_HAS_COMPLETED_SETUP)
    private val EMERGENCY_STATUS_KEY = stringPreferencesKey(KEY_EMERGENCY_STATUS)
    private val ACTIVE_EMERGENCY_EVENT_KEY = stringPreferencesKey(KEY_ACTIVE_EMERGENCY_EVENT)
    private val RECEIVE_EMERGENCY_ALERTS_KEY = booleanPreferencesKey(KEY_RECEIVE_EMERGENCY_ALERTS)

    // Tolerant parser: ignore fields written by a newer build so persisted JSON
    // from a future schema doesn't fail to decode and silently reset to null.
    private val jsonParser = Json { ignoreUnknownKeys = true }

    private var dataStore: DataStore<Preferences>? = null

    fun initialize(store: DataStore<Preferences>) {
        dataStore = store
    }

    private val dataStoreInstance: DataStore<Preferences>
        get() = dataStore ?: throw IllegalStateException("PreferencesManager not initialized. Call initialize() first.")

    suspend fun getUserRole(): UserRole = dataStoreInstance.data
        .map { it[USER_ROLE_KEY]?.let { UserRole.valueOf(it) } ?: UserRole.NONE }
        .first()

    suspend fun getPatientName(): String = dataStoreInstance.data
        .map { it[PATIENT_NAME_KEY] ?: "Patient" }
        .first()

    suspend fun getDeviceName(): String = dataStoreInstance.data
        .map { it[DEVICE_NAME_KEY] ?: "My Device" }
        .first()

    suspend fun getPairedDevice(): PairedDevice? = dataStoreInstance.data
        .map { prefs ->
            prefs[PAIRED_DEVICE_KEY]?.let { json ->
                try {
                    jsonParser.decodeFromString<PairedDevice>(json)
                } catch (e: Exception) {
                    null
                }
            }
        }
        .first()

    suspend fun getVibrationEnabled(): Boolean = dataStoreInstance.data
        .map { it[VIBRATION_ENABLED_KEY] ?: true }
        .first()

    suspend fun getReceiveEmergencyAlerts(): Boolean = dataStoreInstance.data
        .map { it[RECEIVE_EMERGENCY_ALERTS_KEY] ?: true } // Default to true for caregivers
        .first()

    suspend fun getHasCompletedSetup(): Boolean = dataStoreInstance.data
        .map { it[HAS_COMPLETED_SETUP_KEY] ?: false }
        .first()

    suspend fun getEmergencyStatus(): EmergencyStatus = dataStoreInstance.data
        .map { it[EMERGENCY_STATUS_KEY]?.let { EmergencyStatus.valueOf(it) } ?: EmergencyStatus.NONE }
        .first()

    suspend fun getActiveEmergencyEvent(): EmergencyEvent? = dataStoreInstance.data
        .map { prefs ->
            prefs[ACTIVE_EMERGENCY_EVENT_KEY]?.let { json ->
                try {
                    jsonParser.decodeFromString<EmergencyEvent>(json)
                } catch (e: Exception) {
                    null
                }
            }
        }
        .first()

    suspend fun setUserRole(value: UserRole) {
        dataStoreInstance.edit { it[USER_ROLE_KEY] = value.name }
    }

    suspend fun setPatientName(value: String) {
        dataStoreInstance.edit { it[PATIENT_NAME_KEY] = value }
    }

    suspend fun setDeviceName(value: String) {
        dataStoreInstance.edit { it[DEVICE_NAME_KEY] = value }
    }

    suspend fun setPairedDevice(value: PairedDevice?) {
        dataStoreInstance.edit { prefs ->
            prefs[PAIRED_DEVICE_KEY] = value?.let { device ->
                kotlinx.serialization.json.Json.encodeToString<PairedDevice>(device)
            } ?: ""
        }
    }

    /** Patient's known caregivers (multi-caregiver). Empty list if none/unparseable. */
    suspend fun getPairedCaregivers(): List<PairedDevice> = dataStoreInstance.data
        .map { prefs ->
            prefs[PAIRED_CAREGIVERS_KEY]?.let { json ->
                try {
                    jsonParser.decodeFromString(ListSerializer(PairedDevice.serializer()), json)
                } catch (e: Exception) {
                    emptyList()
                }
            } ?: emptyList()
        }
        .first()

    suspend fun setPairedCaregivers(value: List<PairedDevice>) {
        dataStoreInstance.edit { prefs ->
            prefs[PAIRED_CAREGIVERS_KEY] =
                Json.encodeToString(ListSerializer(PairedDevice.serializer()), value)
        }
    }

    /** Upsert one caregiver into the persisted list, keyed by endpoint name. */
    suspend fun addPairedCaregiver(device: PairedDevice) {
        val current = getPairedCaregivers().toMutableList()
        val idx = current.indexOfFirst { it.endpointName == device.endpointName }
        if (idx >= 0) current[idx] = device else current.add(device)
        setPairedCaregivers(current)
    }

    suspend fun removePairedCaregiverByName(name: String) {
        setPairedCaregivers(getPairedCaregivers().filterNot { it.endpointName == name })
    }

    suspend fun setVibrationEnabled(value: Boolean) {
        dataStoreInstance.edit { it[VIBRATION_ENABLED_KEY] = value }
    }

    suspend fun setReceiveEmergencyAlerts(value: Boolean) {
        dataStoreInstance.edit { it[RECEIVE_EMERGENCY_ALERTS_KEY] = value }
    }

    suspend fun setEmergencyStatus(value: EmergencyStatus) {
        dataStoreInstance.edit { it[EMERGENCY_STATUS_KEY] = value.name }
    }

    suspend fun setActiveEmergencyEvent(value: EmergencyEvent?) {
        dataStoreInstance.edit { prefs ->
            prefs[ACTIVE_EMERGENCY_EVENT_KEY] = value?.let { event ->
                Json.encodeToString<EmergencyEvent>(event)
            } ?: ""
        }
    }

    suspend fun setHasCompletedSetup(value: Boolean) {
        dataStoreInstance.edit { it[HAS_COMPLETED_SETUP_KEY] = value }
    }

    suspend fun clearAll() {
        dataStoreInstance.edit {
            it.clear()
        }
    }

    /**
     * Clears only the role, setup flag, and paired device so the user can re-pick a role.
     * Preserves patient name, device name, and vibration preference.
     */
    suspend fun clearRoleAndConnection() {
        dataStoreInstance.edit {
            it.remove(USER_ROLE_KEY)
            it.remove(HAS_COMPLETED_SETUP_KEY)
            it.remove(PAIRED_DEVICE_KEY)
            it.remove(PAIRED_CAREGIVERS_KEY)
        }
    }

    val userRoleFlow: Flow<UserRole> by lazy { dataStoreInstance.data
        .map { it[USER_ROLE_KEY]?.let { UserRole.valueOf(it) } ?: UserRole.NONE } }
    val patientNameFlow: Flow<String> by lazy { dataStoreInstance.data
        .map { it[PATIENT_NAME_KEY] ?: "Patient" } }
    val deviceNameFlow: Flow<String> by lazy { dataStoreInstance.data
        .map { it[DEVICE_NAME_KEY] ?: "My Device" } }
    val pairedDeviceFlow: Flow<PairedDevice?> by lazy { dataStoreInstance.data
        .map { prefs ->
            prefs[PAIRED_DEVICE_KEY]?.let { json ->
                try { jsonParser.decodeFromString<PairedDevice>(json) } catch (e: Exception) { null }
            }
        } }
    val vibrationEnabledFlow: Flow<Boolean> by lazy { dataStoreInstance.data
        .map { it[VIBRATION_ENABLED_KEY] ?: true } }
    val receiveEmergencyAlertsFlow: Flow<Boolean> by lazy { dataStoreInstance.data
        .map { it[RECEIVE_EMERGENCY_ALERTS_KEY] ?: true } }
    val hasCompletedSetupFlow: Flow<Boolean> by lazy { dataStoreInstance.data
        .map { it[HAS_COMPLETED_SETUP_KEY] ?: false } }
}
