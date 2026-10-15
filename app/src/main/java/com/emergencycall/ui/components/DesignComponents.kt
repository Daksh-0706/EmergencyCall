package com.emergencycall.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emergencycall.ui.theme.DesignBorder
import com.emergencycall.ui.theme.DesignRed
import com.emergencycall.ui.theme.DesignTextPrimary
import com.emergencycall.ui.theme.DesignTextSecondary

/**
 * A set of warm red/neutral avatar backgrounds, picked deterministically from a
 * name so the same contact always gets the same tint (offline, no images needed).
 */
private val avatarPalette = listOf(
    Color(0xFFFF3B30),
    Color(0xFF8E5CF7),
    Color(0xFF34C7A3),
    Color(0xFF3B7EFF),
    Color(0xFFFF9F3B),
    Color(0xFF5C34A3),
    Color(0xFF8E8E93),
    Color(0xFFE53B5C)
)

private fun paletteColorFor(name: String): Color {
    val hash = name.fold(7) { acc, c -> (acc * 31 + c.code) % 997 }
    val index = ((hash % avatarPalette.size) + avatarPalette.size) % avatarPalette.size
    return avatarPalette[index]
}

/** Rounded "avatar" showing a contact's initials (2 max). */
@Composable
fun InitialsAvatar(
    name: String,
    size: Dp = 56.dp,
    modifier: Modifier = Modifier
) {
    val initials = name.split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .map { it.first().uppercase() }
        .joinToString("")
        .ifBlank { "?" }
    val corner = if (size >= 48.dp) 28.dp else 14.dp
    Box(
        modifier = modifier
            .size(size)
            .background(paletteColorFor(name), RoundedCornerShape(corner)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = Color.White,
            fontSize = (size.value * 0.34f).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * A contact card in the design language: [InitialsAvatar] + name / relationship-like
 * subtitle + an optional trailing pill (e.g. "Connected" / a role tag).
 */
@Composable
fun ContactCard(
    name: String,
    subtitle: String,
    pill: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, DesignBorder, RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        InitialsAvatar(name = name, size = 52.dp, modifier = Modifier.padding(end = 12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = DesignTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = DesignTextSecondary,
                fontSize = 13.sp
            )
        }
        if (pill != null) {
            Box(
                modifier = Modifier
                    .background(DesignRed.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(text = pill, color = DesignRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}