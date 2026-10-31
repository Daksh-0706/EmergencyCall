package com.emergencycall.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import com.emergencycall.ui.theme.DesignCardBorder
import com.emergencycall.ui.theme.DesignRed
import com.emergencycall.ui.theme.DesignRedDeep
import com.emergencycall.ui.theme.DesignRedLight
import com.emergencycall.ui.theme.DesignRedSoft
import com.emergencycall.ui.theme.DesignSurface
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val HOLD_DURATION_MS = 1000
private const val PROGRESS_STROKE_DP = 6

private val SosGradient = Brush.linearGradient(
    listOf(DesignRedLight, DesignRed, DesignRedDeep)
)

/**
 * The v2 SOS button — gradient core, breathing halo, two sonar rings and a
 * 1-second hold-progress ring. Replaces the flat v1 button.
 */
@Composable
fun PremiumSosButton(
    buttonSize: Dp,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var isPressed by remember { mutableStateOf(false) }

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "sosPressScale"
    )

    val pulse = rememberInfiniteTransition(label = "sosPulse")
    val phase by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "sosPhase"
    )
    val breath = 1f - abs(2f * phase - 1f)

    val holdProgress = remember { Animatable(0f) }

    Box(
        modifier = modifier.size(buttonSize),
        contentAlignment = Alignment.Center
    ) {
        // Breathing halo
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val s = 0.82f + 0.18f * breath
                    scaleX = s; scaleY = s
                }
                .background(
                    Brush.radialGradient(
                        listOf(DesignRed.copy(alpha = 0.20f), Color.Transparent)
                    ),
                    CircleShape
                )
        )
        // Sonar rings
        SonarRing(phase, 0f)
        SonarRing(phase, 0.5f)

        // Gradient core
        Box(
            modifier = Modifier
                .fillMaxSize(0.62f)
                .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
                .shadow(
                    elevation = if (isPressed) 30.dp else 20.dp,
                    shape = CircleShape,
                    spotColor = DesignRed
                )
                .background(SosGradient, CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            val fill = scope.launch {
                                holdProgress.animateTo(
                                    1f,
                                    tween(HOLD_DURATION_MS, easing = LinearEasing)
                                )
                            }
                            val released = tryAwaitRelease()
                            fill.cancel()
                            isPressed = false
                            if (released && holdProgress.value >= 0.99f) {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                onLongPress()
                                scope.launch { holdProgress.snapTo(0f) }
                            } else {
                                scope.launch { holdProgress.animateTo(0f, tween(150)) }
                            }
                        }
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
                    fontSize = 38.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = 2.sp
                )
                Text(
                    text = if (isPressed) "Keep holding..." else "Hold to trigger",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
            if (holdProgress.value > 0f) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .padding(PROGRESS_STROKE_DP.dp)
                ) {
                    val stroke = PROGRESS_STROKE_DP.dp.toPx()
                    drawArc(
                        color = Color.White,
                        startAngle = -90f,
                        sweepAngle = holdProgress.value * 360f,
                        useCenter = false,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}

/** Drop-in replacement for the old flat EmergencyButton (same call sites). */
@Composable
fun EmergencyButton(
    buttonSize: Dp,
    onLongPress: () -> Unit,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit
) = PremiumSosButton(buttonSize = buttonSize, onLongPress = onLongPress)

@Composable
private fun SonarRing(phase: Float, offset: Float) {
    val p = (phase + offset) % 1f
    val scale = 0.62f + p * 0.5f
    val alpha = (1f - p) * 0.5f
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale; scaleY = scale; this.alpha = alpha
            }
            .border(1.dp, DesignRed.copy(alpha = 0.55f), CircleShape)
    )
}

/** The standard v2 surface: 24dp radius, hairline border, optional alarm glow. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    glow: Boolean = false,
    glowColor: Color = DesignRed,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = if (glow) modifier.pulseGlow(enabled = true, color = glowColor) else modifier,
        colors = CardDefaults.cardColors(containerColor = DesignSurface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, DesignCardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

// ============================================================================
// Working app components (kept from v1 so every existing call site compiles)
// ============================================================================

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
            .background(DesignSurface, RoundedCornerShape(20.dp))
            .border(1.dp, DesignCardBorder, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        InitialsAvatar(name = name, size = 52.dp, modifier = Modifier.padding(end = 12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = Color(0xFF8E8E96),
                fontSize = 13.sp
            )
        }
        if (pill != null) {
            Box(
                modifier = Modifier
                    .background(DesignRedSoft, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = pill,
                    color = DesignRed,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
