package com.example.jamzzz

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.shape.CircleShape
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import androidx.compose.animation.core.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.random.Random
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.ExoPlayer
import com.example.jamzzz.ui.theme.TextWhite
import com.example.jamzzz.ui.theme.GradientBackground
import kotlinx.coroutines.launch

/**
 * An expandable player that can be swiped up to show full player controls
 * and swiped down to minimize back to a mini player.
 */
// Define the visual effect types
enum class VisualEffectType {
    NONE,
    ROTATION,
    PULSE,
    VISUALIZER
}

@Composable
fun ExpandablePlayer(
    selectedTrack: MusicFile?,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    exoPlayer: ExoPlayer,
    onPlayPauseClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onOpenEqualizer: () -> Unit = {}
) {
    // State for tracking expansion
    var expanded by remember { mutableStateOf(false) }
    val expansionTransition = updateTransition(targetState = expanded, label = "ExpansionTransition")
    
    // State for visual effect type
    var visualEffectType by remember { mutableStateOf(VisualEffectType.NONE) }
    
    // Animation values
    val miniPlayerHeight = 120.dp // Increased height for mini player
    var maxScreenHeight by remember { mutableStateOf(600.dp) }
    
    // Get the available height to fill the screen
    BoxWithConstraints {
        LaunchedEffect(Unit) {
            maxScreenHeight = maxHeight
        }
    }
    
    // Animation properties for player expansion
    val playerHeight by expansionTransition.animateDp(
        transitionSpec = { tween(durationMillis = 300, easing = FastOutSlowInEasing) },
        label = "PlayerHeight"
    ) { isExpanded ->
        if (isExpanded) maxScreenHeight else miniPlayerHeight
    }
    
    val cornerRadius by expansionTransition.animateDp(
        transitionSpec = { tween(durationMillis = 300, easing = FastOutSlowInEasing) },
        label = "CornerRadius"
    ) { isExpanded ->
        if (isExpanded) 16.dp else 0.dp
    }
    
    // Alpha values for crossfade between mini and full player
    val miniPlayerAlpha by expansionTransition.animateFloat(
        transitionSpec = { tween(durationMillis = 300) },
        label = "MiniPlayerAlpha"
    ) { isExpanded ->
        if (isExpanded) 0f else 1f
    }
    
    val fullPlayerAlpha by expansionTransition.animateFloat(
        transitionSpec = { tween(durationMillis = 300) },
        label = "FullPlayerAlpha"
    ) { isExpanded ->
        if (isExpanded) 1f else 0f
    }
    
    // Track total drag distance for determining final state
    var totalDragDistance by remember { mutableStateOf(0f) }
    
    // Main container for the player
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(playerHeight)
            .clip(RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius))
            .background(MaterialTheme.colors.surface)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = {
                        // Reset total drag distance when starting a new drag
                        totalDragDistance = 0f
                    },
                    onDragEnd = {
                        // Determine whether to expand or collapse based on total drag distance
                        // Negative totalDragDistance means dragged up (expand)
                        // Positive totalDragDistance means dragged down (collapse)
                        if (abs(totalDragDistance) > 50f) { // Threshold for gesture recognition
                            if (totalDragDistance < 0 && !expanded) {
                                expanded = true
                            } else if (totalDragDistance > 0 && expanded) {
                                expanded = false
                            }
                        }
                        
                        // Reset drag distance
                        totalDragDistance = 0f
                    },
                    onDragCancel = {
                        // Reset drag distance
                        totalDragDistance = 0f
                    },
                    onVerticalDrag = { change, dragAmount ->
                        // Consume the position change to prevent parent scrolling
                        change.consume()
                        
                        // Accumulate total drag distance
                        totalDragDistance += dragAmount
                        
                        // For immediate feedback during the drag
                        if (dragAmount < -10 && !expanded && totalDragDistance < -50f) {
                            expanded = true
                        } else if (dragAmount > 10 && expanded && totalDragDistance > 50f) {
                            expanded = false
                        }
                    }
                )
            }
    ) {
        // Mini player (visible when not expanded)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(miniPlayerAlpha)
        ) {
            // Top separator line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.Gray.copy(alpha = 0.3f))
                    .align(Alignment.TopCenter)
            )
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clickable { expanded = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Album art placeholder
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(MaterialTheme.colors.primary.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        // Use album art if available, otherwise use default logo
                        if (selectedTrack?.albumArtUrl != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data("https://via.placeholder.com/80x80.png?text=${selectedTrack.artist}+-+${selectedTrack.title}")
                                    .crossfade(true)
                                    .error(R.drawable.jamzzz_icon_logo)
                                    .fallback(R.drawable.jamzzz_icon_logo)
                                    .build(),
                                contentDescription = "Album Art",
                                modifier = Modifier.size(40.dp),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.jamzzz_icon_logo),
                                contentDescription = "Jamzzz Logo",
                                modifier = Modifier.size(40.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                    
                    // Track info
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = selectedTrack?.title ?: "No track selected",
                            style = MaterialTheme.typography.body1,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = selectedTrack?.artist ?: "",
                            style = MaterialTheme.typography.caption,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = TextWhite.copy(alpha = 0.7f)
                        )
                    }
                    
                    // Player controls
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous button
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.Transparent)
                                .clickable { 
                                    onPreviousClick()
                                }
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SkipPrevious,
                                contentDescription = "Previous",
                                tint = MaterialTheme.colors.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        
                        // Play/Pause button
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.Transparent)
                                .clickable { 
                                    onPlayPauseClick()
                                }
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = MaterialTheme.colors.primary,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        
                        // Next button
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.Transparent)
                                .clickable { 
                                    onNextClick()
                                }
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SkipNext,
                                contentDescription = "Next",
                                tint = MaterialTheme.colors.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
                
                // Progress bar with timestamps
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Progress bar
                    Slider(
                        value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                        onValueChange = { newPosition ->
                            onSeekTo((newPosition * duration).toLong())
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colors.primary,
                            activeTrackColor = MaterialTheme.colors.primary,
                            inactiveTrackColor = MaterialTheme.colors.primary.copy(alpha = 0.2f)
                        )
                    )
                
                    // Timestamps
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatPlayerDuration(currentPosition),
                            style = MaterialTheme.typography.caption,
                            color = TextWhite.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        )
                        Text(
                            text = formatPlayerDuration(duration),
                            style = MaterialTheme.typography.caption,
                            color = TextWhite.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
        
        // Full player (visible when expanded)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(fullPlayerAlpha)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Handle for dragging
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .background(Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Album art placeholder (large) with visual effects
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .background(MaterialTheme.colors.primary.copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp))
                        .clickable {
                            // Cycle through visual effects on click
                            visualEffectType = when (visualEffectType) {
                                VisualEffectType.NONE -> VisualEffectType.ROTATION
                                VisualEffectType.ROTATION -> VisualEffectType.PULSE
                                VisualEffectType.PULSE -> VisualEffectType.VISUALIZER
                                VisualEffectType.VISUALIZER -> VisualEffectType.NONE
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Apply different visual effects based on the selected type
                    when (visualEffectType) {
                        VisualEffectType.ROTATION -> {
                            // Rotation effect
                            val infiniteTransition = rememberInfiniteTransition()
                            val rotation by infiniteTransition.animateFloat(
                                initialValue = 0f,
                                targetValue = 360f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(10000, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                                )
                            )
                            
                            // Use album art if available, otherwise use default logo
                            if (selectedTrack?.albumArtUrl != null) {
                                // For iTunes API, we need to use a custom model transformer
                                // to extract the actual image URL from the JSON response
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data("https://via.placeholder.com/400x400.png?text=${selectedTrack.artist}+-+${selectedTrack.title}")
                                        .crossfade(true)
                                        .error(R.drawable.jamzzz_icon_logo)
                                        .fallback(R.drawable.jamzzz_icon_logo)
                                        .build(),
                                    contentDescription = "Album Art",
                                    modifier = Modifier
                                        .size(160.dp)
                                        .graphicsLayer {
                                            rotationZ = rotation
                                        },
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Image(
                                    painter = painterResource(id = R.drawable.jamzzz_icon_logo),
                                    contentDescription = "Jamzzz Logo",
                                    modifier = Modifier
                                        .size(160.dp)
                                        .graphicsLayer {
                                            rotationZ = rotation
                                        },
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                        VisualEffectType.PULSE -> {
                            // Pulse effect - size pulsates with the beat
                            val infiniteTransition = rememberInfiniteTransition()
                            val scale by infiniteTransition.animateFloat(
                                initialValue = 0.9f,
                                targetValue = 1.1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(600, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                )
                            )
                            
                            // Use album art if available, otherwise use default logo
                            if (selectedTrack?.albumArtUrl != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data("https://via.placeholder.com/400x400.png?text=${selectedTrack.artist}+-+${selectedTrack.title}")
                                        .crossfade(true)
                                        .error(R.drawable.jamzzz_icon_logo)
                                        .fallback(R.drawable.jamzzz_icon_logo)
                                        .build(),
                                    contentDescription = "Album Art",
                                    modifier = Modifier
                                        .size(160.dp)
                                        .graphicsLayer {
                                            scaleX = if (isPlaying) scale else 1f
                                            scaleY = if (isPlaying) scale else 1f
                                        },
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Image(
                                    painter = painterResource(id = R.drawable.jamzzz_icon_logo),
                                    contentDescription = "Jamzzz Logo",
                                    modifier = Modifier
                                        .size(160.dp)
                                        .graphicsLayer {
                                            scaleX = if (isPlaying) scale else 1f
                                            scaleY = if (isPlaying) scale else 1f
                                        },
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                        VisualEffectType.VISUALIZER -> {
                            // Visualizer effect - simulated audio visualization
                            val infiniteTransition = rememberInfiniteTransition()
                            val animatedValues = List(5) { index ->
                                infiniteTransition.animateFloat(
                                    initialValue = 0.8f,
                                    targetValue = 1.2f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(
                                            durationMillis = 500 + Random.nextInt(500),
                                            easing = FastOutSlowInEasing
                                        ),
                                        repeatMode = RepeatMode.Reverse
                                    )
                                )
                            }
                            
                            val rotation by infiniteTransition.animateFloat(
                                initialValue = -5f,
                                targetValue = 5f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(2000, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                )
                            )
                            
                            // Get animated values
                            val scales = animatedValues.map { it.value }
                            val avgScale = scales.average().toFloat()
                            
                            // Use album art if available, otherwise use default logo
                            if (selectedTrack?.albumArtUrl != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data("https://via.placeholder.com/400x400.png?text=${selectedTrack.artist}+-+${selectedTrack.title}")
                                        .crossfade(true)
                                        .error(R.drawable.jamzzz_icon_logo)
                                        .fallback(R.drawable.jamzzz_icon_logo)
                                        .build(),
                                    contentDescription = "Album Art",
                                    modifier = Modifier
                                        .size(160.dp)
                                        .graphicsLayer {
                                            if (isPlaying) {
                                                scaleX = avgScale
                                                scaleY = avgScale
                                                rotationZ = rotation
                                            }
                                        },
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Image(
                                    painter = painterResource(id = R.drawable.jamzzz_icon_logo),
                                    contentDescription = "Jamzzz Logo",
                                    modifier = Modifier
                                        .size(160.dp)
                                        .graphicsLayer {
                                            if (isPlaying) {
                                                scaleX = avgScale
                                                scaleY = avgScale
                                                rotationZ = rotation
                                            }
                                        },
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                        else -> {
                            // No effect - Use album art if available, otherwise use default logo
                            if (selectedTrack?.albumArtUrl != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data("https://via.placeholder.com/400x400.png?text=${selectedTrack.artist}+-+${selectedTrack.title}")
                                        .crossfade(true)
                                        .error(R.drawable.jamzzz_icon_logo)
                                        .fallback(R.drawable.jamzzz_icon_logo)
                                        .build(),
                                    contentDescription = "Album Art",
                                    modifier = Modifier.size(160.dp),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Image(
                                    painter = painterResource(id = R.drawable.jamzzz_icon_logo),
                                    contentDescription = "Jamzzz Logo",
                                    modifier = Modifier.size(160.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }
                    
                    // Display a small indicator of the current effect mode
                    if (visualEffectType != VisualEffectType.NONE) {
                        Text(
                            text = when(visualEffectType) {
                                VisualEffectType.ROTATION -> "Rotation"
                                VisualEffectType.PULSE -> "Pulse"
                                VisualEffectType.VISUALIZER -> "Visualizer"
                                else -> ""
                            },
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.primary.copy(alpha = 0.7f),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 8.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Track info
                Text(
                    text = selectedTrack?.title ?: "No track selected",
                    style = MaterialTheme.typography.h6,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = selectedTrack?.artist ?: "",
                    style = MaterialTheme.typography.subtitle1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = TextWhite.copy(alpha = 0.7f)
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Seek bar
                Slider(
                    value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                    onValueChange = { 
                        val newPosition = (it * duration).toLong()
                        onSeekTo(newPosition)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colors.primary,
                        activeTrackColor = MaterialTheme.colors.primary,
                        inactiveTrackColor = MaterialTheme.colors.primary.copy(alpha = 0.2f)
                    )
                )
                
                // Time indicators
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatPlayerDuration(currentPosition),
                        style = MaterialTheme.typography.body2,
                        color = TextWhite.copy(alpha = 0.7f)
                    )
                    Text(
                        text = formatPlayerDuration(duration),
                        style = MaterialTheme.typography.body2,
                        color = TextWhite.copy(alpha = 0.7f)
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Player controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous button
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.Transparent)
                            .clickable { 
                                onPreviousClick()
                            }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = "Previous",
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colors.primary
                        )
                    }
                    
                    // Play/Pause button
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colors.primary.copy(alpha = 0.2f))
                            .clickable { 
                                onPlayPauseClick()
                            }
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colors.primary
                        )
                    }
                    
                    // Next button
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.Transparent)
                            .clickable { 
                                onNextClick()
                            }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = "Next",
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colors.primary
                        )
                    }
                }
                
                // Equalizer button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colors.surface.copy(alpha = 0.3f))
                            .clickable { onOpenEqualizer() }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Equalizer,
                            contentDescription = "Equalizer",
                            tint = MaterialTheme.colors.primary
                        )
                    }
                }
            }
        }
    }
}

// Helper function to format duration in mm:ss format
private fun formatPlayerDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
