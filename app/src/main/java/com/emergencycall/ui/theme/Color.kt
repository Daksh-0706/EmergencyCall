package com.emergencycall.ui.theme

import androidx.compose.ui.graphics.Color

// Emergency red palette
val EmergencyRed = Color(0xFFB71C1C)
val EmergencyRedLight = Color(0xFFEF5350)
val EmergencyRedDark = Color(0xFF7F0000)

val EmergencyRedContainer = Color(0xFFFFEBEE)
val EmergencyRedOnContainer = Color(0xFF7F0000)

// Standard Material 3 palette extensions — DARK scheme (dark bg, light text)
val MD3Primary = Color(0xFFD0BCFF)
val MD3OnPrimary = Color(0xFF381E72)
val MD3PrimaryContainer = Color(0xFF4F378B)
val MD3OnPrimaryContainer = Color(0xFFEADDFF)

val MD3Secondary = Color(0xFFCCC2DC)
val MD3OnSecondary = Color(0xFF332D41)
val MD3SecondaryContainer = Color(0xFF4A4458)
val MD3OnSecondaryContainer = Color(0xFFE8DEF8)

val MD3Tertiary = Color(0xFFEFB8C8)
val MD3OnTertiary = Color(0xFF492532)
val MD3TertiaryContainer = Color(0xFF633B48)
val MD3OnTertiaryContainer = Color(0xFFFFD8E4)

// Error kept a strong, vivid RED (not the MD3 desaturated dark-error pink):
// the EMERGENCY button uses colorScheme.error as its fill, so it must read as red.
val MD3Error = Color(0xFFFF5449)
val MD3OnError = Color(0xFFFFFFFF)
val MD3ErrorContainer = Color(0xFF93000A)
val MD3OnErrorContainer = Color(0xFFFFDAD6)

val MD3Background = Color(0xFF121212)
val MD3OnBackground = Color(0xFFE6E1E5)
val MD3Surface = Color(0xFF1C1B1F)
val MD3OnSurface = Color(0xFFE6E1E5)
val MD3SurfaceVariant = Color(0xFF49454F)
// Brightened from #CAC4D0 so secondary/subtitle text is actually readable on dark.
val MD3OnSurfaceVariant = Color(0xFFDAD5DF)
val MD3Outline = Color(0xFF948F99)
val MD3OutlineVariant = Color(0xFF49454F)
val MD3Shadow = Color(0xFF000000)
val MD3Scrim = Color(0xFF000000)
val MD3InverseSurface = Color(0xFFE6E1E5)
val MD3InverseOnSurface = Color(0xFF313033)
val MD3InversePrimary = Color(0xFF6750A4)

// ============================================================================
// Whispr design palette — extracted from the Figma design (CodeTea export).
// These tokens drive the app's "sexy" dark + emergency-red look.
// ============================================================================
val DesignBackground = Color(0xFF1A1A1E)        // app background
val DesignSurface = Color(0xFF16161A)           // cards
val DesignSurfaceDeep = Color(0xFF0C0C0E)       // deep panels / bottom nav
val DesignBorder = Color(0xFF222226)            // card borders
val DesignTextPrimary = Color(0xFFFFFFFF)
val DesignTextSecondary = Color(0xFF8E8E93)
val DesignRed = Color(0xFFFF3B30)               // primary emergency red
val DesignRedSoft = Color(0x1FFF3B30)           // tinted red (pills, glow)
val DesignRedFaint = Color(0x08FF3B30)          // faint red (outer SOS ring)