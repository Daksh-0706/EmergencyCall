package com.emergencycall.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emergencycall.data.ConnectionState
import com.emergencycall.data.EmergencyState
import com.emergencycall.data.EmergencyStateManager
import com.emergencycall.data.EmergencyStatus
import com.emergencycall.ui.components.ContactCard
import com.emergencycall.ui.components.FadeSlideIn
import com.emergencycall.ui.components.HeartbeatDot
import com.emergencycall.ui.components.InitialsAvatar
import com.emergencycall.ui.components.PremiumSosButton
import com.emergencycall.ui.components.pulseGlow
import com.emergencycall.ui.theme.DesignCardBorder
import com.emergencycall.ui.theme.DesignRed
import com.emergencycall.ui.theme.DesignRedSoft
import com.emergencycall.ui.theme.DesignSurface
import com.emergencycall.ui.theme.DesignTextPrimary
import com.emergencycall.ui.theme.DesignTextSecondary
import kotlin.math.min

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
        containerColor = Color.Transparent,
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
    state: EmergencyState,
    onEmergency: () -> Unit,
    onNavigateToConnection: () -> Unit,
    onSettingsClick: () -> Unit,
    onViewAllIce: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val maxButtonSizeDp = min(maxWidth.value, maxHeight.value) * 0.52f
        val buttonSize = (maxButtonSizeDp.coerceIn(230f, 290f)).dp

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                FadeSlideIn(delayMs = 0) {
                    HomeHeader(
                        connected = state.connectionState is ConnectionState.Connected,
                        onSettingsClick = onSettingsClick
                    )
                }
            }

            item {
                FadeSlideIn(delayMs = 70) {
                    ArmedStatusPill(state)
                }
            }

            item {
                FadeSlideIn(delayMs = 140) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = state.emergencyStatus,
                            transitionSpec = {
                                (fadeIn(androidx.compose.animation.core.tween(300)) +
                                        slideInVertically { h -> h / 4 }) togetherWith
                                        fadeOut(androidx.compose.animation.core.tween(220))
                            },
                            label = "emergencyStatus"
                        ) { status ->
                            when (status) {
                                EmergencyStatus.NONE -> PremiumSosButton(
                                    buttonSize = buttonSize,
                                    onLongPress = { onEmergency() }
                                )
                                EmergencyStatus.ACTIVE, EmergencyStatus.SENT_WAITING_ACK ->
                                    EmergencySentCard(
                                        eventId = state.activeEmergencyEvent?.eventId ?: "",
                                        status = status
                                    )
                                EmergencyStatus.ACKNOWLEDGED ->
                                    EmergencyAcknowledgedCard(
                                        eventId = state.activeEmergencyEvent?.eventId ?: "",
                                        responderName = state.responderName
                                    )
                            }
                        }
                    }
                }
            }

            if (state.emergencyStatus == EmergencyStatus.NONE) {
                item {
                    FadeSlideIn(delayMs = 210) {
                        Text(
                            text = "Press and hold to alert your caregivers",
                            fontSize = 14.sp,
                            color = DesignTextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (state.pairedDevices.isNotEmpty()) {
                    item {
                        FadeSlideIn(delayMs = 280) {
                            IceContactsPreview(
                                contacts = state.pairedDevices.map { it.endpointName },
                                connected = state.connectedCount > 0,
                                onViewAll = onViewAllIce
                            )
                        }
                    }
                }

                if (state.connectionState !is ConnectionState.Connected) {
                    item {
                        FadeSlideIn(delayMs = 350) {
                            Button(
                                onClick = onNavigateToConnection,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DesignRed,
                                    contentColor = Color.White
                                )
                            ) {
                                Text(
                                    "Pair a contact to arm SOS",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
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
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = DesignTextPrimary,
                letterSpacing = 0.2.sp
            )
            Text(
                text = "PATIENT MODE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = DesignRed,
                letterSpacing = 1.6.sp
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = DesignSurface,
                shape = RoundedCornerShape(999.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DesignCardBorder)
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HeartbeatDot(
                        color = if (connected) DesignRed else DesignTextSecondary,
                        size = 7.dp
                    )
                    Spacer(Modifier.size(7.dp))
                    Text(
                        text = if (connected) "Armed" else "Offline",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (connected) DesignTextPrimary else DesignTextSecondary
                    )
                }
            }
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

@Composable
private fun ArmedStatusPill(state: EmergencyState) {
    val armed = state.connectionState is ConnectionState.Connected
    Surface(
        color = DesignSurface,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DesignCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeartbeatDot(
                color = if (armed) DesignRed else DesignTextSecondary,
                size = 10.dp
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (armed) "SOS Active · Armed" else "SOS Active · Disarmed",
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
}

@Composable
private fun IceContactsPreview(
    contacts: List<String>,
    connected: Boolean,
    onViewAll: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "ICE Contacts",
                color = DesignTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "View All",
                color = DesignRed,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onViewAll).padding(4.dp)
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
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = name.take(8).ifBlank { "?" },
                        color = DesignTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        text = if (connected) "Online" else "Offline",
                        color = if (connected) DesignRed else DesignTextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun IceTab(
    contacts: List<String>,
    connected: Boolean,
    onAddContact: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FadeSlideIn(delayMs = 0) {
                Column(Modifier.padding(bottom = 8.dp)) {
                    Text(
                        "ICE Contacts",
                        color = DesignTextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "These trusted contacts will be notified in an emergency.",
                        color = DesignTextSecondary,
                        fontSize = 13.sp
                    )
                }
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
                    Text("No ICE contacts yet", color = DesignTextSecondary, fontSize = 15.sp)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "Pair another phone to add a caregiver",
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
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DesignRed,
                    contentColor = Color.White
                )
            ) {
                Text("ADD ICE CONTACT", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp))
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
    val color = if (status == EmergencyStatus.SENT_WAITING_ACK) DesignRed
                else MaterialTheme.colorScheme.error

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .pulseGlow(enabled = true, color = color),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        color = Color.Transparent
    ) {
        Column(
            Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(color.copy(alpha = 0.18f), DesignSurface)
                    ),
                    RoundedCornerShape(24.dp)
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .background(color.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = color
                )
            }
            Spacer(Modifier.size(14.dp))
            Text(
                text = statusText,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = if (status == EmergencyStatus.SENT_WAITING_ACK)
                    "Alert delivered. Keep your phone nearby."
                else "Broadcasting to your caregivers...",
                fontSize = 13.sp,
                color = DesignTextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = "Event: ${eventId.take(8)}",
                fontSize = 12.sp,
                color = DesignTextSecondary.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun EmergencyAcknowledgedCard(eventId: String, responderName: String = "") {
    val color = MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        color = Color.Transparent
    ) {
        Column(
            Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(color.copy(alpha = 0.18f), DesignSurface)
                    ),
                    RoundedCornerShape(24.dp)
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .background(color.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                    tint = color
                )
            }
            Spacer(Modifier.size(14.dp))
            Text(
                text = "Help Acknowledged",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = if (responderName.isNotBlank()) "$responderName is on the way" else "Caregiver is on the way",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = DesignTextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = "Event: ${eventId.take(8)}",
                fontSize = 12.sp,
                color = DesignTextSecondary.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}
