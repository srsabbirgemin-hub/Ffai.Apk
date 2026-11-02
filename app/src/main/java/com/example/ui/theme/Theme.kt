package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FloatingAIDarkColorScheme = darkColorScheme(
    primary = NeonPurple,
    onPrimary = Color.White,
    primaryContainer = NeonPurpleDark,
    onPrimaryContainer = Color.White,
    secondary = NeonTeal,
    onSecondary = Color.Black,
    secondaryContainer = NeonTealDark,
    onSecondaryContainer = Color.White,
    tertiary = NeonCyan,
    onTertiary = Color.Black,
    background = DeepSpace,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkCard,
    onSurfaceVariant = TextSecondary,
    outline = DarkCardBorder
)

@Composable
fun FloatingAITheme(
    darkTheme: Boolean = true, // Always default to dark futuristic aesthetic
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = FloatingAIDarkColorScheme,
        typography = Typography,
        content = content
    )
}
