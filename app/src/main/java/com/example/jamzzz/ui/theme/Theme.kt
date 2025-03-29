package com.example.jamzzz.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dark color palette with dark blue gradient colors
private val DarkColorPalette = darkColors(
    primary = AccentBlue,              // Bright blue for primary actions
    primaryVariant = Color(0xFF0A4DA6), // Darker blue
    secondary = AccentCyan,            // Bright cyan for secondary actions
    background = DarkBlueGradientStart, // Very dark blue background (will be overridden by gradient)
    surface = Color(0xFF162B45),       // Dark blue-gray surface with better contrast
    onPrimary = TextWhite,             // Slightly off-white for better eye comfort
    onSecondary = TextWhite,           // Slightly off-white for better eye comfort
    onBackground = TextWhite,          // Slightly off-white for better eye comfort
    onSurface = TextWhite              // Slightly off-white for better eye comfort
)

// Light color palette (we'll still create this but won't use it by default)
private val LightColorPalette = lightColors(
    primary = Color(0xFF0A4DA6),       // Darker blue for better contrast on light background
    primaryVariant = Color(0xFF083A80), // Even darker blue
    secondary = Color(0xFF00B0CC),     // Darker cyan for better contrast
    background = Color(0xFFF8FAFC),    // Very light blue-gray
    surface = Color(0xFFFFFFFF),       // White
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF0A1929),  // Very dark blue for text on light background
    onSurface = Color(0xFF0A1929)      // Very dark blue for text on light background
)

@Composable
fun JamzzzTheme(
    darkTheme: Boolean = true, // Always use dark theme by default
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) {
        DarkColorPalette
    } else {
        LightColorPalette
    }

    MaterialTheme(
        colors = colors,
        content = content
    )
}
