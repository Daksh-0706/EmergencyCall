package com.emergencycall.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emergencycall.data.ConnectionState
import com.emergencycall.data.EmergencyStateManager
import com.emergencycall.data.EmergencyStatus
import com.emergencycall.ui.components.ContactCard
import com.emergencycall.ui.components.InitialsAvatar
import com.emergencycall.ui.theme.DesignBorder
import com.emergencycall.ui.theme.DesignRed
import com.emergencycall.ui.theme.DesignTextPrimary
import com.emergencycall.ui.theme.DesignTextSecondary
import kotlin.math.min

/**
 * Patient home, restyled to match the Whispr Figma design (deep dark surfaces +
 * concentric SOS button) while keeping all the real EmergencyCall functionality.
 *
 * The bottom nav has two in-place tabs — HOME and ICE — plus SETTINGS which opens
 * the full settings screen. The emergency trigger remains a press-and-hold on the
 * SOS button.
 */
@Composable
fun PatientHomeScreen(
    onNavigateToConnection: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onEmergency: () -> Unit,
    onHomeClick: () -> Unit,
    onIceClick: () -> Unit,
    selectedTab: NavigationTab = NavigationTab.HOME
) {
    val stateManager = EmergencyStateManager.getInstance()
    val state by stateManager.state.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            BottomNavigationBar(
                selectedTab = selectedTab,
                onHomeClick = onHomeClick,
                onConnectionClick = onIceClick,
                onSettingsClick = onNavigateToSettings,
                middleTab = NavigationTab.ICE
            )
        }
    ) { innerPadding ->
        when (selectedTab) {
            NavigationTab.ICE -> IceTab(
                contacts = state.pairedDevices.map { it.endpointName },
                connected = state.connectedCount > 0,
                onAddContact = onNavigateToConnection,
                modifier = Modifier.padding(innerPadding)
            )
            else -> HomeTab(
                state = state,
                onEmergency = onEmergency,
                onNavigateToConnection = onNavigateToConnection,
                onSettingsClick = onNavigateToSettings,
                onViewAllIce = onIceClick,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@Composable
private fun HomeTab(
    state: com.emergencycall.data.EmergencyState,
    onEmergency: () -> Unit,
    onNavigateToConnection: () -> Unit,
    onSettingsClick: () -> Unit,
    onViewAllIce: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val maxButtonSizeDp = min(maxWidth.value, maxHeight.value) * 0.5f
        val buttonSize = (maxButtonSizeDp.coerceIn(220f, 280f)).dp

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                HomeHeader(
                    connected = state.connectionState is ConnectionState.Connected,
                    onSettingsClick = onSettingsClick
                )
            }

            // Emergency button / sent / acknowledged states
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    when (state.emergencyStatus) {
                        EmergencyStatus.NONE -> EmergencyButton(
                            buttonSize = buttonSize,
                            onLongPress = { onEmergency() },
                            onPressStart = { /* track press start if needed */ },
                            onPressEnd = { /* released */ }
                        )
                        EmergencyStatus.ACTIVE, EmergencyStatus.SENT_WAITING_ACK -> EmergencySentCard(
                            eventId = state.activeEmergencyEvent?.eventId ?: "",
                            status = state.emergencyStatus
                        )
                        EmergencyStatus.ACKNOWLEDGED -> EmergencyAcknowledgedCard(
                            eventId = state.activeEmergencyEvent?.eventId ?: "",
                            responderName = state.responderName
                        )
                    }
                }
            }

            if (state.emergencyStatus == EmergencyStatus.NONE) {
                // Instruction text
                item {
                    Text(
                        text = "Press and hold to alert your caregivers",
                        fontSize = 15.sp,
                        color = DesignTextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Armed status pill
                item { ArmedStatusPill(state) }

                // ICE contacts preview
                if (state.pairedDevices.isNotEmpty()) {
                    item {
                        IceContactsPreview(
                            contacts = state.pairedDevices.map { it.endpointName },
                            connected = state.connectedCount > 0,
                            onViewAll = onViewAllIce
                        )
                    }
                }

                // Pair / reconnect button when not connected
                if (state.connectionState !is ConnectionState.Connected) {
                    item {
                        val label = when (state.connectionState) {
                            is ConnectionState.Disconnected -> "PAIR CONTACT"
                            else -> "CONNECT CONTACT"
                        }
                        Button(
                            onClick = onNavigateToConnection,
                            modifier = Modifier.fillMaxWidth(0.85f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DesignRed,
                                contentColor = Color.White
                            )
                        ) {
                            Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(connected: Boolean, onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "EmergencyCall",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = DesignTextPrimary
            )
            Text(
                text = "PATIENT MODE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = DesignRed
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Live / offline chip
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .background(
                        color = if (connected) DesignRed else DesignTextSecondary,
                        shape = CircleShape
                    )
                    .size(8.dp)
            )
            Text(
                text = if (connected) "Armed" else "Offline",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (connected) DesignRed else DesignTextSecondary
            )
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = DesignTextSecondary
                )
            }
        }
    }
}

/** Small status pill mirroring the design's "SOS Active State" card. */
@Composable
private fun ArmedStatusPill(state: com.emergencycall.data.EmergencyState) {
    val armed = state.connectionState is ConnectionState.Connected
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp)
            .border(1.dp, DesignBorder, RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .padding(end = 12.dp)
                .size(12.dp)
                .background(
                    color = if (armed) DesignRed else DesignTextSecondary,
                    shape = CircleShape
                )
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (armed) "SOS Active: Armed" else "SOS Active: Disarmed",
                color = DesignTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (armed) "${state.connectedCount} caregiver(s) will be pinged instantly"
                       else "Pair a contact to arm emergency alerts",
                color = DesignTextSecondary,
                fontSize = 12.sp
            )
        }
    }
}

/** Horizontal row of ICE contact initials, matching the design's home preview. */
@Composable
private fun IceContactsPreview(
    contacts: List<String>,
    connected: Boolean,
    onViewAll: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("ICE Contacts", color = DesignTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "View All",
                color = DesignRed,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.background(
                    color = Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                ).pointerInput(Unit) {
                    detectTapGestures { onViewAll() }
                }.padding(4.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            contacts.take(4).forEach { name ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    InitialsAvatar(name = name, size = 52.dp)
                    Spacer(Modifier.padding(4.dp))
                    Text(
                        text = name.take(8).ifBlank { "?" },
                        color = DesignTextPrimary,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                    Text(
                        text = if (connected) "Online" else "Offline",
                        color = DesignTextSecondary,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

/** Full ICE contacts list tab. */
@Composable
private fun IceTab(
    contacts: List<String>,
    connected: Boolean,
    onAddContact: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                Text("ICE Contacts", color = DesignTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "These trusted contacts will be notified in an emergency.",
                    color = DesignTextSecondary,
                    fontSize = 13.sp
                )
            }
        }

        items(contacts) { name ->
            ContactCard(
                name = name,
                subtitle = "Caregiver",
                pill = if (connected) "Online" else "Offline"
            )
        }

        if (contacts.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No ICE contacts yet",
                        color = DesignTextSecondary,
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.padding(8.dp))
                    Text(
                        text = "Pair another phone to add a caregiver",
                        color = DesignTextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        }

        item {
            Button(
                onClick = onAddContact,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DesignRed,
                    contentColor = Color.White
                )
            ) {
                Text("ADD ICE CONTACT", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }

        item { Spacer(Modifier.padding(16.dp)) }
    }
}

/**
 * The design's concentric SOS button: faint + soft red rings around a solid red
 * center. Press-and-hold (>= the long-press timeout) triggers the emergency.
 */
@Composable
fun EmergencyButton(
    buttonSize: Dp,
    onLongPress: () -> Unit,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        label = "sosScale"
    )
    Box(
        modifier = Modifier.size(buttonSize).aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        // Outermost faint ring
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .background(DesignRed.copy(alpha = 0.04f), CircleShape)
        )
        // Soft intermediate ring
        Box(
            modifier = Modifier
                .fillMaxSize(0.82f)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .background(DesignRed.copy(alpha = 0.12f), CircleShape)
        )
        // Solid red center — the actual press target
        Box(
            modifier = Modifier
                .fillMaxSize(0.62f)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .background(if (isPressed) DesignRed.copy(alpha = 0.85f) else DesignRed, CircleShape)
                .shadow(elevation = 20.dp, spotColor = DesignRed, shape = CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            onPressStart()
                            val released = tryAwaitRelease()
                            isPressed = false
                            if (released) onPressEnd()
                        },
                        onLongPress = { onLongPress() }
                    )
                }
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "SOS",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Hold to trigger",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
fun EmergencySentCard(eventId: String, status: EmergencyStatus) {
    val statusText = when (status) {
        EmergencyStatus.ACTIVE -> "Emergency sent"
        EmergencyStatus.SENT_WAITING_ACK -> "Waiting for caregiver..."
        else -> "Emergency sent"
    }
    val color = if (status == EmergencyStatus.SENT_WAITING_ACK) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.error

    Card(
        modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.15f),
            contentColor = color
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.Send,
                contentDescription = "",
                modifier = Modifier.size(48.dp),
                tint = color
            )
            Spacer(Modifier.padding(16.dp))
            Text(
                text = statusText,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.padding(8.dp))
            Text(
                text = "Event: ${eventId.take(8)}...",
                fontSize = 14.sp,
                color = color.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun EmergencyAcknowledgedCard(eventId: String, responderName: String = "") {
    val color = MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.15f),
            contentColor = color
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "",
                modifier = Modifier.size(48.dp),
                tint = color
            )
            Spacer(Modifier.padding(16.dp))
            Text(
                text = "Help Acknowledged",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.padding(8.dp))
            Text(
                text = if (responderName.isNotBlank()) "$responderName is on the way" else "Caregiver is on the way",
                fontSize = 18.sp,
                color = color.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.padding(8.dp))
            Text(
                text = "Event: ${eventId.take(8)}...",
                fontSize = 14.sp,
                color = color.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}