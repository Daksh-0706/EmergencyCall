package com.emergencycall.permissions

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.Manifest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class PermissionManager(private val activity: Activity) {

    private val requiredPermissions = mutableListOf<String>()

    init {
        determineRequiredPermissions()
    }

    private fun determineRequiredPermissions() {
        requiredPermissions.clear()

        // Always required for Nearby Connections
        requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        requiredPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)

        // Bluetooth permissions (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            // NEARBY_WIFI_DEVICES only exists on Android 13+ (API 33).
            // Requesting it on 12/12L makes the whole request auto-fail.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requiredPermissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        } else {
            requiredPermissions.add(Manifest.permission.BLUETOOTH)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }

        // Wi-Fi permissions for Nearby
        requiredPermissions.add(Manifest.permission.ACCESS_WIFI_STATE)
        requiredPermissions.add(Manifest.permission.CHANGE_WIFI_STATE)

        // Notifications (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Foreground service (Android 14+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            requiredPermissions.add(Manifest.permission.FOREGROUND_SERVICE)
            requiredPermissions.add(Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK)
        } else {
            requiredPermissions.add(Manifest.permission.FOREGROUND_SERVICE)
        }

        // Vibration
        requiredPermissions.add(Manifest.permission.VIBRATE)

        // Wake lock for alarm
        requiredPermissions.add(Manifest.permission.WAKE_LOCK)

        // Full screen intent for emergency alarm
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            requiredPermissions.add(Manifest.permission.USE_FULL_SCREEN_INTENT)
        }
    }

    fun getRequiredPermissions(): List<String> = requiredPermissions

    fun areAllPermissionsGranted(): Boolean {
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun getMissingPermissions(): List<String> {
        return requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestAllPermissions(
        launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>
    ) {
        val missing = getMissingPermissions()
        if (missing.isNotEmpty()) {
            launcher.launch(missing.toTypedArray())
        }
    }

    fun shouldShowRequestPermissionRationale(permission: String): Boolean {
        return androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }

    companion object {
        fun getPermissionExplanation(permission: String): String {
            return when (permission) {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION -> "Required for Nearby Connections to discover nearby devices via Bluetooth and Wi-Fi"
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE -> "Required for direct phone-to-phone communication via Bluetooth"
                Manifest.permission.NEARBY_WIFI_DEVICES -> "Required for Nearby Connections to discover devices without Internet"
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE -> "Required for Nearby Connections to use Wi-Fi Direct"
                Manifest.permission.POST_NOTIFICATIONS -> "Required to show emergency alarm notifications"
                Manifest.permission.FOREGROUND_SERVICE,
                Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK -> "Required to keep emergency alarm running in background"
                Manifest.permission.VIBRATE -> "Required for emergency vibration alerts"
                Manifest.permission.WAKE_LOCK -> "Required to keep alarm active when screen is off"
                Manifest.permission.USE_FULL_SCREEN_INTENT -> "Required to show full-screen emergency alarm on lock screen"
                else -> "Required for app functionality"
            }
        }
    }
}