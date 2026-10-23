package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val OtnorColorScheme = darkColorScheme(
    primary = OtnorCyan,
    onPrimary = OtnorDarkBg,
    primaryContainer = OtnorSurfaceElevated,
    onPrimaryContainer = OtnorCyan,
    secondary = OtnorViolet,
    onSecondary = OtnorTextPrimary,
    secondaryContainer = OtnorSurfaceElevated,
    onSecondaryContainer = OtnorTextPrimary,
    tertiary = OtnorEmerald,
    background = OtnorDarkBg,
    onBackground = OtnorTextPrimary,
    surface = OtnorSurface,
    onSurface = OtnorTextPrimary,
    surfaceVariant = OtnorSurfaceElevated,
    onSurfaceVariant = OtnorTextSecondary,
    error = OtnorCoral,
    outline = OtnorBorder
)

@Composable
fun OtnorTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = OtnorColorScheme,
        typography = Typography,
        content = content
    )
}
