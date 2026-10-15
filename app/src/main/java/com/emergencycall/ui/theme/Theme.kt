package com.emergencycall.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

// Light color scheme
private val LightColorScheme = lightColorScheme(
    primary = MD3Primary,
    onPrimary = MD3OnPrimary,
    primaryContainer = MD3PrimaryContainer,
    onPrimaryContainer = MD3OnPrimaryContainer,
    secondary = MD3Secondary,
    onSecondary = MD3OnSecondary,
    secondaryContainer = MD3SecondaryContainer,
    onSecondaryContainer = MD3OnSecondaryContainer,
    tertiary = MD3Tertiary,
    onTertiary = MD3OnTertiary,
    tertiaryContainer = MD3TertiaryContainer,
    onTertiaryContainer = MD3OnTertiaryContainer,
    error = MD3Error,
    onError = MD3OnError,
    errorContainer = MD3ErrorContainer,
    onErrorContainer = MD3OnErrorContainer,
    background = MD3Background,
    onBackground = MD3OnBackground,
    surface = MD3Surface,
    onSurface = MD3OnSurface,
    surfaceVariant = MD3SurfaceVariant,
    onSurfaceVariant = MD3OnSurfaceVariant,
    outline = MD3Outline,
    outlineVariant = MD3OutlineVariant,
    inverseSurface = MD3InverseSurface,
    inverseOnSurface = MD3InverseOnSurface,
    inversePrimary = MD3InversePrimary
)

// Dark color scheme — Whispr design palette (dark bg, emergency red accent).
// primary/secondary/tertiary/error all drive a consistent red accent, with the
// dark surfaces matching the Figma design so every screen inherits the look.
private val DarkColorScheme = darkColorScheme(
    primary = DesignRed,
    onPrimary = DesignTextPrimary,
    primaryContainer = DesignRedSoft,
    onPrimaryContainer = DesignRed,
    secondary = DesignRed,
    onSecondary = DesignTextPrimary,
    secondaryContainer = DesignRedSoft,
    onSecondaryContainer = DesignRed,
    tertiary = DesignRed,
    onTertiary = DesignTextPrimary,
    tertiaryContainer = DesignRedSoft,
    onTertiaryContainer = DesignRed,
    error = DesignRed,
    onError = DesignTextPrimary,
    errorContainer = DesignRedSoft,
    onErrorContainer = DesignRed,
    background = DesignBackground,
    onBackground = DesignTextPrimary,
    surface = DesignSurface,
    onSurface = DesignTextPrimary,
    surfaceContainerHighest = DesignSurface,
    surfaceVariant = DesignSurfaceDeep,
    onSurfaceVariant = DesignTextSecondary,
    outline = DesignBorder,
    outlineVariant = DesignBorder,
    inverseSurface = DesignTextPrimary,
    inverseOnSurface = DesignSurfaceDeep,
    inversePrimary = DesignRed
)

@Composable
fun EmergencyCall(
    // App is a guaranteed dark theme (dark bg, light text). darkTheme=true also makes
    // isAppearanceLightStatusBars=false below → light status/nav icons on the dark bar.
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val context = LocalContext.current

    // Set system bar colors and edge-to-edge
    SideEffect {
        val activity = (context as? Activity) ?: return@SideEffect
        val window = activity.window
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)

        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Set system bar colors to transparent/background
        window.statusBarColor = colorScheme.background.toArgb()
        window.navigationBarColor = colorScheme.background.toArgb()

        // Set icon colors based on theme
        val isLight = !darkTheme
        insetsController.isAppearanceLightStatusBars = isLight
        insetsController.isAppearanceLightNavigationBars = isLight
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}