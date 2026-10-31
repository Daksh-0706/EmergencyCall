package com.emergencycall.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A living status dot: two quick heartbeats per cycle.
 * Use it anywhere the app communicates "live / armed / connected"
 * (header chip, Armed pill, caregiver "Live" badge).
 */
@Composable
fun HeartbeatDot(
    color: Color,
    size: Dp = 8.dp,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "heartbeat")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing)
        ),
        label = "heartbeatPhase"
    )

    // Double-beat: two pulses inside one cycle (lub-dub).
    val beat = (phase * 2f) % 1f
    val scale = 1f + 0.7f * beat * (1f - beat) * 4f   // peaks mid-beat
    val alpha = 0.9f - 0.55f * beat

    Box(
        modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .background(color, CircleShape)
    )
}
