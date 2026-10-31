package com.emergencycall.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import com.emergencycall.ui.theme.DesignBorder
import com.emergencycall.ui.theme.DesignRed
import com.emergencycall.ui.theme.DesignRedSoft
import com.emergencycall.ui.theme.DesignSurfaceDeep
import com.emergencycall.ui.theme.DesignTextSecondary

/**
 * v2 floating bottom nav — glass pill, red pill-highlight on the active tab.
 */
@Composable
fun BottomNavigationBar(
    selectedTab: NavigationTab,
    onHomeClick: () -> Unit,
    onConnectionClick: () -> Unit,
    onSettingsClick: () -> Unit,
    middleTab: NavigationTab = NavigationTab.CONNECTION
) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Surface(
            color = DesignSurfaceDeep.copy(alpha = 0.92f),
            shape = RoundedCornerShape(26.dp),
            border = BorderStroke(1.dp, DesignBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                // Insets first so the pill keeps its full 66dp content height on
                // both gesture and 3-button navigation.
                Modifier.fillMaxWidth().navigationBarsPadding().height(66.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavItem(
                    icon = Icons.Filled.Home,
                    label = "Home",
                    selected = selectedTab == NavigationTab.HOME,
                    onClick = onHomeClick,
                    modifier = Modifier.weight(1f)
                )
                NavItem(
                    icon = if (middleTab == NavigationTab.ICE) Icons.Filled.MedicalServices else Icons.Filled.Bluetooth,
                    label = if (middleTab == NavigationTab.ICE) "ICE" else "Connect",
                    selected = selectedTab == middleTab,
                    onClick = onConnectionClick,
                    modifier = Modifier.weight(1f)
                )
                NavItem(
                    icon = Icons.Filled.Settings,
                    label = "Settings",
                    selected = selectedTab == NavigationTab.SETTINGS,
                    onClick = onSettingsClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(38.dp)
                .background(
                    color = if (selected) DesignRedSoft else androidx.compose.ui.graphics.Color.Transparent,
                    shape = RoundedCornerShape(13.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) DesignRed else DesignTextSecondary,
                modifier = Modifier.size(21.dp)
            )
        }
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) DesignRed else DesignTextSecondary
        )
    }
}
