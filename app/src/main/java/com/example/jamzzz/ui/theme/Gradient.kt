package com.example.jamzzz.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Dark blue gradient colors
val DarkBlueGradientStart = Color(0xFF0A1929)  // Very dark blue
val DarkBlueGradientMiddle = Color(0xFF0F2A4A) // Dark blue
val DarkBlueGradientEnd = Color(0xFF1A3A6C)    // Medium-dark blue

// Create a vertical gradient background
@Composable
fun GradientBackground(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        DarkBlueGradientStart,
                        DarkBlueGradientMiddle,
                        DarkBlueGradientEnd
                    )
                )
            )
    ) {
        content()
    }
}

// Create a horizontal gradient background
@Composable
fun HorizontalGradientBackground(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        DarkBlueGradientStart,
                        DarkBlueGradientMiddle,
                        DarkBlueGradientEnd
                    )
                )
            )
    ) {
        content()
    }
}

// Accent colors with high contrast for readability
val AccentBlue = Color(0xFF4DA6FF)      // Bright blue
val AccentCyan = Color(0xFF00E5FF)      // Bright cyan
val TextWhite = Color(0xFFEEF2F6)       // Slightly off-white for better eye comfort
val TextSecondary = Color(0xFFB0BEC5)   // Light blue-gray for secondary text
