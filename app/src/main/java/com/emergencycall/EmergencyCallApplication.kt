package com.emergencycall

import android.app.Application
import androidx.datastore.preferences.preferencesDataStore
import com.emergencycall.data.PreferencesManager

class EmergencyCallApplication : Application() {

    val dataStore by preferencesDataStore("emergency_call_prefs")

    override fun onCreate() {
        super.onCreate()
        PreferencesManager.initialize(dataStore)
    }

    companion object {
        const val SERVICE_ID = "com.emergencycall.nearby"
        const val CHANNEL_EMERGENCY = "emergency_alarm_channel"
        const val CHANNEL_CONNECTION = "connection_status_channel"
    }
}