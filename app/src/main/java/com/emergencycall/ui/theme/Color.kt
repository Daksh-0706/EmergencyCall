package com.emergencycall.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// Design system v2 — deep charcoal surfaces, layered cards, refined red.
// Old token names kept so every existing reference keeps compiling.
// ============================================================================

// Backgrounds
val DesignBackground   = Color(0xFF0B0B0F)   // app background
val DesignSurface      = Color(0xFF141419)   // cards / pills
val DesignSurfaceDeep  = Color(0xFF0F0F13)   // bottom nav, deep panels
val DesignBorder       = Color(0xFF222226)   // hairline borders
val DesignCardBorder   = Color.White.copy(alpha = 0.07f)

// Text
val DesignTextPrimary   = Color(0xFFFFFFFF)
val DesignTextSecondary = Color(0xFF8E8E96)

// Emergency red
val DesignRed       = Color(0xFFFF3B30)
val DesignRedLight  = Color(0xFFFF6B5E)      // gradient top
val DesignRedDeep   = Color(0xFFD9261B)      // gradient bottom
val DesignRedSoft   = Color(0x1FFF3B30)      // tinted red (pills, glow)
val DesignRedFaint  = Color(0x08FF3B30)      // faint red (outer SOS ring)

// Caregiver accent (blue, used sparingly for contrast on caregiver UI)
val DesignCareBlue     = Color(0xFF3E63DD)
val DesignCareBlueSoft = Color(0x143E63DD)

// ============================================================================
// Legacy Material 3 palette — kept because Theme.kt's fallback light scheme
// stillreferences these tokens.
// ============================================================================
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
