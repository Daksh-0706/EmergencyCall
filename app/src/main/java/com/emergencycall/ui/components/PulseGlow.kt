package com.emergencycall.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.emergencycall.ui.animations.EaseInOut

/**
 * Pulsing red glow behind a card — used when an emergency is ACTIVE so the
 * caregiver's status card literally radiates alarm.
 *
 * Usage:
 *   Card(
 *       modifier = Modifier
 *           .fillMaxWidth()
 *           .pulseGlow(enabled = emergencyStatus == EmergencyStatus.ACTIVE, color = statusColor)
 *   ) { ... }
 */
fun Modifier.pulseGlow(
    enabled: Boolean,
    color: Color,
    maxElevation: Dp = 24.dp,
    cornerRadius: Dp = 16.dp
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "pulseGlow")
    val glow by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseGlowValue"
    )
    val elevation = if (enabled) (4 + (maxElevation.value - 4) * glow).dp else 4.dp
    this.shadow(
        elevation = elevation,
        shape = RoundedCornerShape(cornerRadius),
        spotColor = color
    )
}
