package com.emergencycall.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Fade + slide-up entrance. Wrap LazyColumn items, role-selection
 * cards, or any content that should appear gracefully instead of
 * popping in raw.
 *
 * Usage:
 *   items(list) { item ->
 *       FadeSlideIn(delayMs = index * 60) { ItemCard(item) }
 *   }
 */
@Composable
fun FadeSlideIn(
    delayMs: Int = 0,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 400, delayMillis = delayMs, easing = FastOutSlowInEasing),
        label = "enterAlpha"
    )
    val translationY by animateFloatAsState(
        targetValue = if (visible) 0f else 28f,
        animationSpec = tween(durationMillis = 400, delayMillis = delayMs, easing = FastOutSlowInEasing),
        label = "enterTranslation"
    )

    Box(
        modifier.graphicsLayer {
            this.alpha = alpha
            this.translationY = translationY
        }
    ) {
        content()
    }
}
