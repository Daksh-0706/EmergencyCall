package com.emergencycall.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emergencycall.data.UserRole
import com.emergencycall.ui.components.FadeSlideIn
import com.emergencycall.ui.theme.DesignCardBorder
import com.emergencycall.ui.theme.DesignCareBlue
import com.emergencycall.ui.theme.DesignCareBlueSoft
import com.emergencycall.ui.theme.DesignRed
import com.emergencycall.ui.theme.DesignRedSoft
import com.emergencycall.ui.theme.DesignSurface
import com.emergencycall.ui.theme.DesignTextPrimary
import com.emergencycall.ui.theme.DesignTextSecondary

@Composable
fun RoleSelectionScreen(onRoleSelected: (UserRole) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.size(44.dp))

        FadeSlideIn(delayMs = 0) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "EmergencyCall",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = DesignTextPrimary,
                    letterSpacing = 0.3.sp
                )
                Text(
                    text = "OFFLINE SOS NETWORK",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = DesignRed,
                    letterSpacing = 2.5.sp
                )
            }
        }

        Spacer(Modifier.size(48.dp))

        FadeSlideIn(delayMs = 100) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "WHO IS THIS PHONE FOR?",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = DesignTextSecondary,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    text = "Choose your role",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = DesignTextPrimary
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "One phone sends the alert,\nthe other responds to it.",
                    fontSize = 13.5.sp,
                    color = DesignTextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }
        }

        Spacer(Modifier.size(32.dp))

        FadeSlideIn(delayMs = 200) {
            RoleCardV2(
                title = "I may need help",
                description = "Press & hold SOS to alert my caregivers instantly — no internet needed.",
                tag = "Patient",
                icon = Icons.Filled.MedicalServices,
                accent = DesignRed,
                accentSoft = DesignRedSoft,
                hero = true,
                onClick = { onRoleSelected(UserRole.PATIENT) }
            )
        }

        Spacer(Modifier.size(16.dp))

        FadeSlideIn(delayMs = 320) {
            RoleCardV2(
                title = "I respond to alerts",
                description = "Get loud full-screen alarms and acknowledge with one tap.",
                tag = "Caregiver",
                icon = Icons.Filled.Visibility,
                accent = DesignCareBlue,
                accentSoft = DesignCareBlueSoft,
                hero = false,
                onClick = { onRoleSelected(UserRole.CAREGIVER) }
            )
        }

        Spacer(Modifier.size(28.dp))

        FadeSlideIn(delayMs = 440) {
            Text(
                text = "You can change this anytime in Settings",
                fontSize = 12.sp,
                color = DesignTextSecondary,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.size(24.dp))
    }
}

@Composable
private fun RoleCardV2(
    title: String,
    description: String,
    tag: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: androidx.compose.ui.graphics.Color,
    accentSoft: androidx.compose.ui.graphics.Color,
    hero: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = DesignSurface,
        shape = RoundedCornerShape(26.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.5.dp,
            color = if (hero) accent.copy(alpha = 0.5f) else DesignCardBorder
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            Modifier
                .background(
                    if (hero) accent.copy(alpha = 0.08f) else androidx.compose.ui.graphics.Color.Transparent,
                    RoundedCornerShape(26.dp)
                )
                .clickable(onClick = onClick)
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(56.dp)
                        .background(accentSoft, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(Modifier.size(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = DesignTextPrimary
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = description,
                        fontSize = 12.5.sp,
                        color = DesignTextSecondary,
                        lineHeight = 18.sp
                    )
                }
                Spacer(Modifier.size(12.dp))
                Surface(
                    color = accentSoft,
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = tag,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
