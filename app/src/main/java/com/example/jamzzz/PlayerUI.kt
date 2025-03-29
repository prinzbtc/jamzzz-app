package com.example.jamzzz

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.jamzzz.data.MusicLibrary
import com.example.jamzzz.EqualizerActivity
import com.example.jamzzz.ui.components.TabItem
import com.example.jamzzz.ui.components.TabbedScreen
import com.example.jamzzz.ui.screens.AllSongsScreen
import com.example.jamzzz.ui.screens.FavoritesScreen
import com.example.jamzzz.ui.screens.PlaylistsScreen
import com.example.jamzzz.ui.theme.JamzzzTheme
import com.example.jamzzz.ui.theme.TextWhite
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File

class PlayerUI : ComponentActivity() {
    private lateinit var exoPlayer: ExoPlayer
    private val PREFS_NAME = "JamzzzPlayerPrefs"
    private val KEY_LAST_TRACK_ID = "lastTrackId"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize ExoPlayer
        exoPlayer = ExoPlayer.Builder(this).build()
        
        // Get last track ID from SharedPreferences
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastTrackId = prefs.getLong(KEY_LAST_TRACK_ID, -1L)
        
        setContent {
            JamzzzTheme(darkTheme = true) {
                MainApp(
                    exoPlayer = exoPlayer,
                    initialTrackId = if (lastTrackId != -1L) lastTrackId else null
                )
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        // ExoPlayer will continue playing in the background
    }
    
    override fun onDestroy() {
        super.onDestroy()
        exoPlayer.release()
    }
}

@Composable
fun MainApp(
    exoPlayer: ExoPlayer,
    initialTrackId: Long? = null
) {
    // Initialize MusicLibrary
    val context = LocalContext.current
    val musicLibrary = remember { MusicLibrary(context) }
    var musicFiles by remember { mutableStateOf<List<MusicFile>>(emptyList()) }
    var selectedTrack by remember { mutableStateOf<MusicFile?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var permissionGranted by remember { mutableStateOf(false) }
    
    val coroutineScope = rememberCoroutineScope()
    
    // Function to save current state to SharedPreferences
    fun saveCurrentState() {
        val prefs = context.getSharedPreferences("JamzzzPlayerPrefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        selectedTrack?.let { track ->
            editor.putLong("lastTrackId", track.id)
        }
        
        editor.apply()
    }
    
    // Set up ExoPlayer listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(Player.EVENT_POSITION_DISCONTINUITY, Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                    duration = player.duration.coerceAtLeast(0L)
                }
            }
            
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    duration = exoPlayer.duration.coerceAtLeast(0L)
                }
            }
        }
        
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }
    
    // Update current position periodically with more frequent updates for smoother slider movement
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
            // Update duration as well in case it wasn't properly set
            if (duration <= 0 && exoPlayer.duration > 0) {
                duration = exoPlayer.duration
            }
            delay(100) // Update 10 times per second for smoother slider movement
        }
    }
    
    // Function to load music files from the device
    suspend fun loadAllMusic() {
        isLoading = true
        try {
            val files = com.example.jamzzz.loadAllMusicFilesFromDevice(context, exoPlayer, initialTrackId)
            musicFiles = files
            
            // If we have an initialTrackId, try to select that track
            if (initialTrackId != null) {
                val track = files.find { it.id == initialTrackId }
                if (track != null) {
                    selectedTrack = track
                    
                    // Initialize ExoPlayer with the selected track
                    exoPlayer.setMediaItem(MediaItem.fromUri(track.uri))
                    exoPlayer.prepare()
                    // Don't auto-play, let the user click play
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allPermissionsGranted = permissions.all { it.value }
        if (allPermissionsGranted) {
            permissionGranted = true
            // Load all music files when permission is granted
            coroutineScope.launch {
                loadAllMusic()
            }
        }
    }
    
    LaunchedEffect(Unit) {
        val readPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(context, readPermission) == PackageManager.PERMISSION_GRANTED -> {
                permissionGranted = true
                
                // Load all music files when permission is granted
                // This is already in a coroutine scope (LaunchedEffect)
                loadAllMusic()
            }
            else -> {
                permissionLauncher.launch(arrayOf(readPermission))
            }
        }
    }
    
    // Set up tabs
    val tabs = listOf(
        TabItem(
            title = "All Songs",
            icon = Icons.Filled.MusicNote,
            screen = {
                AllSongsScreen(
                    musicFiles = musicFiles,
                    selectedTrack = selectedTrack,
                    musicLibrary = musicLibrary,
                    onBrowseMusic = { /* No longer needed */ },
                    onTrackSelected = { track ->
                        selectedTrack = track
                        
                        // Save track selection to preferences
                        saveCurrentState()
                        
                        // Play the selected track
                        exoPlayer.setMediaItem(MediaItem.fromUri(track.uri))
                        exoPlayer.prepare()
                        exoPlayer.play()
                    }
                )
            }
        ),
        TabItem(
            title = "Favorites",
            icon = Icons.Filled.Favorite,
            screen = {
                FavoritesScreen(
                    musicFiles = musicFiles,
                    selectedTrack = selectedTrack,
                    musicLibrary = musicLibrary,
                    onTrackSelected = { track ->
                        selectedTrack = track
                        
                        // Save track selection to preferences
                        saveCurrentState()
                        
                        // Play the selected track
                        exoPlayer.setMediaItem(MediaItem.fromUri(track.uri))
                        exoPlayer.prepare()
                        exoPlayer.play()
                    }
                )
            }
        ),
        TabItem(
            title = "Playlists",
            icon = Icons.Filled.List,
            screen = {
                PlaylistsScreen(
                    musicLibrary = musicLibrary,
                    onPlaylistSelected = { playlist ->
                        // Handle playlist selection - will be implemented later
                        // For now, just save current state
                        saveCurrentState()
                    }
                )
            }
        )
    )
    
    // State for dropdown menu visibility
    var showMenu by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    // Replace text with logo image
                    Image(
                        painter = painterResource(id = R.drawable.jamzzz_header_logo),
                        contentDescription = "Jamzzz Logo",
                        modifier = Modifier.height(32.dp),
                        contentScale = ContentScale.Fit
                    )
                },
                backgroundColor = MaterialTheme.colors.surface.copy(alpha = 0.9f),
                contentColor = MaterialTheme.colors.onPrimary,
                actions = {
                    // Three-dot menu button
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    
                    // Dropdown menu
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(onClick = {
                            // Handle Core Mixer click
                            showMenu = false
                        }) {
                            Text("Core Mixer")
                        }
                        
                        DropdownMenuItem(onClick = {
                            // Handle MashUp click
                            showMenu = false
                        }) {
                            Text("MashUp")
                        }
                        
                        DropdownMenuItem(onClick = {
                            // Handle AI Mixer click
                            showMenu = false
                        }) {
                            Text("AI Mixer")
                        }
                        
                        DropdownMenuItem(onClick = {
                            // Open Equalizer activity
                            val intent = android.content.Intent(context, EqualizerActivity::class.java)
                            context.startActivity(intent)
                            showMenu = false
                        }) {
                            Text("Equalizer")
                        }
                        
                        DropdownMenuItem(onClick = {
                            // Handle Settings click
                            showMenu = false
                        }) {
                            Text("Settings")
                        }
                        
                        DropdownMenuItem(onClick = {
                            // Handle About click
                            showMenu = false
                        }) {
                            Text("About")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Tabs take most of the screen
                Box(modifier = Modifier.weight(1f)) {
                    TabbedScreen(tabs = tabs)
                }
                
                // Player at the bottom with swipe-to-expand functionality
                if (selectedTrack != null) {
                    // Use the ExpandablePlayer component
                    val playPauseAction: () -> Unit = {
                        if (isPlaying) {
                            exoPlayer.pause()
                        } else {
                            exoPlayer.play()
                        }
                    }
                    
                    val previousTrackAction: () -> Unit = {
                        // Find previous track in the list
                        selectedTrack?.let { current ->
                            val currentIndex = musicFiles.indexOfFirst { it.id == current.id }
                            if (currentIndex > 0) {
                                val prevTrack = musicFiles[currentIndex - 1]
                                selectedTrack = prevTrack
                                
                                // Save track selection to preferences
                                saveCurrentState()
                                
                                exoPlayer.setMediaItem(MediaItem.fromUri(prevTrack.uri))
                                exoPlayer.prepare()
                                exoPlayer.play()
                            }
                        }
                    }
                    
                    val nextTrackAction: () -> Unit = {
                        // Find next track in the list
                        selectedTrack?.let { current ->
                            val currentIndex = musicFiles.indexOfFirst { it.id == current.id }
                            if (currentIndex < musicFiles.size - 1) {
                                val nextTrack = musicFiles[currentIndex + 1]
                                selectedTrack = nextTrack
                                
                                // Save track selection to preferences
                                saveCurrentState()
                                
                                exoPlayer.setMediaItem(MediaItem.fromUri(nextTrack.uri))
                                exoPlayer.prepare()
                                exoPlayer.play()
                            }
                        }
                    }
                    
                    ExpandablePlayer(
                        selectedTrack = selectedTrack,
                        isPlaying = isPlaying,
                        currentPosition = currentPosition,
                        duration = duration,
                        exoPlayer = exoPlayer,
                        onPlayPauseClick = playPauseAction,
                        onSeekTo = { position ->
                            exoPlayer.seekTo(position)
                        },
                        onPreviousClick = previousTrackAction,
                        onNextClick = nextTrackAction
                    )
                }
            }
        }
    }
}

@Composable
fun MusicFileItem(
    musicFile: MusicFile,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colors.primary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colors.surface
    }
    
    Surface(
        color = backgroundColor,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = if (isSelected) MaterialTheme.colors.primary else LocalContentColor.current
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = musicFile.title,
                    style = MaterialTheme.typography.subtitle1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isSelected) MaterialTheme.colors.primary else LocalContentColor.current
                )
                Text(
                    text = musicFile.artist,
                    style = MaterialTheme.typography.body2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = formatTrackDuration(musicFile.duration),
                style = MaterialTheme.typography.caption
            )
        }
    }
    Divider()
}

// Helper function to format duration
private fun formatTrackDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}