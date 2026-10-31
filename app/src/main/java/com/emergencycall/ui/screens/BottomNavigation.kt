package com.emergencycall.ui.screens

/**
 * Navigation slots shared by the bottom nav and all screens.
 * The composable now lives in FloatingBottomNav.kt (design v2);
 * this file only hosts the enum so existing references keep compiling.
 */
enum class NavigationTab { HOME, ICE, CONNECTION, SETTINGS }
