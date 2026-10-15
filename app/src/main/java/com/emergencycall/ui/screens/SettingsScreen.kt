package com.emergencycall.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emergencycall.data.EmergencyStateManager
import com.emergencycall.data.PreferencesManager
import com.emergencycall.data.UserRole
import com.emergencycall.ui.theme.DesignBorder
import com.emergencycall.ui.theme.DesignRed
import com.emergencycall.ui.theme.DesignTextPrimary
import com.emergencycall.ui.theme.DesignTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onNavigateToConnection: () -> Unit,
    onTestAlarmClick: () -> Unit,
    onShowDiagnostics: () -> Unit,
    onChangeRole: () -> Unit
) {
    val stateManager = EmergencyStateManager.getInstance()
    val state by stateManager.state.collectAsStateWithLifecycle()

    var patientName by remember { mutableStateOf(state.patientName) }
    var deviceName by remember { mutableStateOf(state.deviceName) }
    var vibrationEnabled by remember { mutableStateOf(state.vibrationEnabled) }
    var receiveEmergencyAlerts by remember { mutableStateOf(false) }
    var showChangeRoleDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Load receiveEmergencyAlerts preference when screen appears
    androidx.compose.runtime.LaunchedEffect(key1 = state.userRole) {
        if (state.userRole == UserRole.CAREGIVER) {
            val alerts = PreferencesManager.getReceiveEmergencyAlerts()
            receiveEmergencyAlerts = alerts
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .statusBarsPadding(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = DesignTextPrimary
                    )
                }
                Text(
                    text = "Settings",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = DesignTextPrimary
                )
                Spacer(modifier = Modifier.width(48.dp))
            }
        },
        bottomBar = {
            BottomNavigationBar(
                selectedTab = NavigationTab.SETTINGS,
                onHomeClick = onBackClick,
                onConnectionClick = onNavigateToConnection,
                onSettingsClick = { /* Already on settings */ }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Role
            item {
                SettingsCard(
                    title = "Current Role",
                    subtitle = state.userRole.name,
                    icon = Icons.Filled.Person
                ) {
                    Button(
                        onClick = { showChangeRoleDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DesignRed,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp)
                    ) {
                        Text("CHANGE ROLE", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Patient Name
            item {
                SettingsCard(
                    title = "Patient Name",
                    subtitle = "Name shown on caregiver's phone",
                    icon = Icons.Filled.MedicalServices
                ) {
                    StyledTextField(
                        value = patientName,
                        onValueChange = { newName ->
                            patientName = newName
                            stateManager.setPatientName(newName)
                            coroutineScope.launch(Dispatchers.IO) {
                                PreferencesManager.setPatientName(newName)
                            }
                        }
                    )
                }
            }

            // Device Name
            item {
                SettingsCard(
                    title = "Device Name",
                    subtitle = "Name shown during pairing",
                    icon = Icons.Filled.Phone
                ) {
                    StyledTextField(
                        value = deviceName,
                        onValueChange = { newName ->
                            deviceName = newName
                            stateManager.setDeviceName(newName)
                            coroutineScope.launch(Dispatchers.IO) {
                                PreferencesManager.setDeviceName(newName)
                            }
                        }
                    )
                }
            }

            // Vibration
            item {
                SettingsToggleRow(
                    title = "Vibration",
                    subtitle = "Enable vibration for emergency alerts",
                    icon = Icons.Filled.Vibration,
                    checked = vibrationEnabled,
                    onCheckedChange = { checked ->
                        vibrationEnabled = checked
                        stateManager.setVibrationEnabled(checked)
                        coroutineScope.launch(Dispatchers.IO) {
                            PreferencesManager.setVibrationEnabled(checked)
                        }
                    }
                )
            }

            // Receive Emergency Alerts (Caregiver only)
            if (state.userRole == UserRole.CAREGIVER) {
                item {
                    SettingsToggleRow(
                        title = "Receive Emergency Alerts",
                        subtitle = "Accept calls from paired patients",
                        icon = Icons.Filled.NotificationsActive,
                        checked = receiveEmergencyAlerts,
                        onCheckedChange = { checked ->
                            receiveEmergencyAlerts = checked
                            coroutineScope.launch(Dispatchers.IO) {
                                PreferencesManager.setReceiveEmergencyAlerts(checked)
                            }
                        }
                    )
                }
            }

            // Test Alarm
            item {
                SettingsCard(
                    title = "Test Alarm",
                    subtitle = "Test the emergency alarm sound and vibration",
                    icon = Icons.Filled.NotificationsActive
                ) {
                    RedButton("TEST ALARM", onClick = { onTestAlarmClick() })
                }
            }

            // Re-pair Device
            item {
                SettingsCard(
                    title = "Re-pair Device",
                    subtitle = "Connect to a different phone",
                    icon = Icons.Filled.BluetoothSearching
                ) {
                    RedButton("RE-PAIR DEVICE", onClick = { onNavigateToConnection() }, filled = false)
                }
            }

            // Connection Diagnostics
            item {
                SettingsCard(
                    title = "Connection Diagnostics",
                    subtitle = "View connection status and troubleshoot",
                    icon = Icons.Filled.BugReport
                ) {
                    RedButton("VIEW DIAGNOSTICS", onClick = { onShowDiagnostics() }, filled = false)
                }
            }

            // About
            item {
                SettingsCard(
                    title = "About",
                    subtitle = "EmergencyCall v1.0.0",
                    icon = Icons.Filled.Info
                ) {
                    Text(
                        text = "Offline emergency call system using Nearby Connections",
                        fontSize = 13.sp,
                        color = DesignTextSecondary,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.padding(4.dp))
                    Text(
                        text = "No Internet required • Direct phone-to-phone",
                        fontSize = 13.sp,
                        color = DesignTextSecondary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item { Spacer(Modifier.padding(8.dp)) }
        }
    }

    if (showChangeRoleDialog) {
        AlertDialog(
            onDismissRequest = { showChangeRoleDialog = false },
            title = { Text("Change device role?") },
            text = { Text("You will be disconnected from the current device.") },
            confirmButton = {
                Button(
                    onClick = {
                        showChangeRoleDialog = false
                        onChangeRole()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DesignRed, contentColor = Color.White)
                ) { Text("Change Role") }
            },
            dismissButton = {
                Button(
                    onClick = { showChangeRoleDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) { Text("Cancel") }
            }
        )
    }
}

/** Design-style settings card: icon chip + title + subtitle header, then content. */
@Composable
private fun SettingsCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp)
            .border(1.dp, DesignBorder, RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconChip(icon = icon, modifier = Modifier.padding(end = 12.dp))
            Column {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DesignTextPrimary)
                Text(subtitle, fontSize = 12.sp, color = DesignTextSecondary)
            }
        }
        Spacer(Modifier.padding(12.dp))
        content()
    }
}

/** Design-style toggle row: icon chip + title/subtitle + trailing switch. */
@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp)
            .border(1.dp, DesignBorder, RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconChip(icon = icon, modifier = Modifier.padding(end = 12.dp))
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DesignTextPrimary)
            Text(subtitle, fontSize = 12.sp, color = DesignTextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = DesignRed
            )
        )
    }
}

@Composable
private fun IconChip(icon: ImageVector, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(40.dp)
            .background(DesignRed.copy(alpha = 0.10f), RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = DesignRed)
    }
}

@Composable
private fun StyledTextField(value: String, onValueChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        singleLine = true,
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun RedButton(text: String, onClick: () -> Unit, filled: Boolean = true) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (filled) DesignRed else MaterialTheme.colorScheme.surface,
            contentColor = if (filled) Color.White else DesignRed
        )
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}