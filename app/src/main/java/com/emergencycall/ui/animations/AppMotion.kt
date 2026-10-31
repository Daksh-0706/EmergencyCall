package com.emergencycall.ui.animations

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween

// ============================================================
// Central motion system for EmergencyCall.
// Every animation in the app uses these tokens so the whole
// product feels designed as one.
// ============================================================

// Durations (ms)
const val MOTION_FAST = 150
const val MOTION_BASE = 300
const val MOTION_SLOW = 500

// Easing curves (Material 3 "emphasized" feel)
val EaseIn  = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)   // elements leaving the screen
val EaseOut = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)   // elements entering the screen
val EaseInOut = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f) // symmetrical moves / loops
