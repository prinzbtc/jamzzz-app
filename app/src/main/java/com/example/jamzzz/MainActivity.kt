package com.example.jamzzz

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jamzzz.ui.theme.GradientBackground
import com.example.jamzzz.ui.theme.JamzzzTheme
import com.example.jamzzz.ui.theme.TextWhite
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JamzzzTheme(darkTheme = true) {
                WelcomeScreen(onAnimationFinished = {
                    // Navigate to PlayerUI after animation completes
                    val intent = Intent(this, PlayerUI::class.java)
                    startActivity(intent)
                    finish() // Close MainActivity so user can't go back to it
                })
            }
        }
    }
}

@Composable
fun WelcomeScreen(onAnimationFinished: () -> Unit) {
    // Animation states
    var startAnimation by remember { mutableStateOf(false) }
    val logoScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.3f,
        animationSpec = tween(durationMillis = 1000, easing = EaseOutBack)
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 1000)
    )
    var showTagline by remember { mutableStateOf(false) }
    
    // Start animation after composition
    LaunchedEffect(key1 = true) {
        startAnimation = true
        delay(1000) // Wait for logo animation
        showTagline = true
        delay(1500) // Show tagline for a moment
        onAnimationFinished()
    }
    
    GradientBackground {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Logo
                Box(
                    modifier = Modifier
                        .size(260.dp)
                        .scale(logoScale)
                        .alpha(logoAlpha),
                    contentAlignment = Alignment.Center
                ) {
                    // Use the Jamzzz logo image
                    Image(
                        painter = painterResource(id = R.drawable.jamzzz_logo),
                        contentDescription = "Jamzzz Logo",
                        modifier = Modifier.fillMaxSize(0.95f)
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Tagline with fade-in animation
                AnimatedVisibility(
                    visible = showTagline,
                    enter = fadeIn(animationSpec = tween(1000)) + 
                           expandVertically(animationSpec = tween(1000))
                ) {
                    Text(
                        text = "Mix. Create. Enjoy.",
                        color = TextWhite.copy(alpha = 0.8f),
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}
