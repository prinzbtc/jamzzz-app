package com.example.jamzzz

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
    onNextClick: () -> Unit
) {
    // State for tracking expansion
    var expanded by remember { mutableStateOf(false) }
    val expansionTransition = updateTransition(targetState = expanded, label = "ExpansionTransition")
    
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
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colors.primary
                        )
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
                
                // Album art placeholder (large)
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .background(MaterialTheme.colors.primary.copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colors.primary
                    )
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
