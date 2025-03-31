package com.example.jamzzz

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.OnBackPressedCallback
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
import com.example.jamzzz.service.MusicService
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
    private var musicService: MusicService? = null
    private var serviceBound = false
    private var currentTrack: MusicFile? = null
    
    // Service connection for binding to the MusicService
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            serviceBound = true
            
            // Set the ExoPlayer instance to the service
            musicService?.setPlayer(exoPlayer)
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            serviceBound = false
        }
    }
    private val PREFS_NAME = "JamzzzPlayerPrefs"
    private val KEY_LAST_TRACK_ID = "lastTrackId"
    private val KEY_LAST_TAB_INDEX = "lastTabIndex"
    private val KEY_LAST_QUEUE_SOURCE = "lastQueueSource"
    private val KEY_LAST_PLAYLIST_ID = "lastPlaylistId"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Handle back button press to minimize app instead of closing it
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Minimize the app instead of closing it
                moveTaskToBack(true)
            }
        })
        
        // Initialize ExoPlayer
        exoPlayer = ExoPlayer.Builder(this).build()
        exoPlayer.prepare()
        
        // Set up a listener to start the service when playback begins
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    startMusicService()
                }
            }
        })
        
        // Bind to the music service (don't start it yet - we'll start it when playback begins)
        val serviceIntent = Intent(this, MusicService::class.java)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        
        // Get saved state from SharedPreferences
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastTrackId = prefs.getLong(KEY_LAST_TRACK_ID, -1L)
        val lastTabIndex = prefs.getInt(KEY_LAST_TAB_INDEX, 0)
        val lastQueueSource = prefs.getString(KEY_LAST_QUEUE_SOURCE, "AllSongs") ?: "AllSongs"
        val lastPlaylistId = prefs.getString(KEY_LAST_PLAYLIST_ID, null)
        
        setContent {
            JamzzzTheme(darkTheme = true) {
                MainApp(
                    exoPlayer = exoPlayer,
                    initialTrackId = if (lastTrackId != -1L) lastTrackId else null,
                    initialTabIndex = lastTabIndex,
                    initialQueueSource = lastQueueSource,
                    initialPlaylistId = lastPlaylistId,
                    onOpenEqualizer = { openEqualizerActivity() },
                    musicService = musicService,
                    onTrackChanged = { track -> 
                        // Update the activity's currentTrack property
                        currentTrack = track
                    }
                )
            }
        }
    }
    
    private fun openEqualizerActivity() {
        val intent = Intent(this, EqualizerActivity::class.java).apply {
            putExtra("audioSessionId", exoPlayer.audioSessionId)
        }
        startActivity(intent)
    }
    
    override fun onPause() {
        super.onPause()
        // ExoPlayer will continue playing in the background via the service
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Unbind from the service
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
        // Note: We don't release the ExoPlayer here as the service is handling it
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Set this new intent as the activity's intent
        setIntent(intent)
        println("DEBUG: PlayerUI received new intent")
        
        // If we're already bound to the music service, update it with the current track info
        musicService?.let { service ->
            currentTrack?.let { track ->
                service.updateTrackInfo(
                    title = track.title ?: "Unknown",
                    artist = track.artist ?: "Unknown"
                )
            }
        }
    }
    
    // Start the music service as a foreground service
    private fun startMusicService() {
        val serviceIntent = Intent(this, MusicService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }
}

@Composable
fun MainApp(
    exoPlayer: ExoPlayer,
    initialTrackId: Long? = null,
    initialTabIndex: Int = 0,
    initialQueueSource: String = "AllSongs",
    initialPlaylistId: String? = null,
    onOpenEqualizer: () -> Unit = {},
    musicService: MusicService? = null,
    onTrackChanged: (MusicFile?) -> Unit = {}
) {
    // Initialize MusicLibrary
    val context = LocalContext.current
    val musicLibrary = remember { MusicLibrary(context) }
    var musicFiles by remember { mutableStateOf<List<MusicFile>>(emptyList()) }
    var selectedTrack by remember { mutableStateOf<MusicFile?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    
    // Call the onTrackChanged callback whenever selectedTrack changes
    LaunchedEffect(selectedTrack) {
        onTrackChanged(selectedTrack)
    }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var permissionGranted by remember { mutableStateOf(false) }
    
    // Track which queue is currently active - initialize with saved value
    var activeQueueSource by remember { mutableStateOf(initialQueueSource) } // Can be "AllSongs", "Favorites", or "Playlist"
    
    // Store the current queue songs for proper navigation
    var currentQueueSongs by remember { mutableStateOf<List<MusicFile>>(emptyList()) }
    
    // Track the current playlist ID when in playlist mode - initialize with saved value
    var currentPlaylistId by remember { mutableStateOf(initialPlaylistId) }
    
    // Track the current tab index
    var currentTabIndex by remember { mutableStateOf(initialTabIndex) }
    
    val coroutineScope = rememberCoroutineScope()
    
    // Function to save current state to SharedPreferences
    fun saveCurrentState(tabIndex: Int? = null) {
        val prefs = context.getSharedPreferences("JamzzzPlayerPrefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        selectedTrack?.let { track ->
            editor.putLong("lastTrackId", track.id)
        }
        
        // Save the active tab index if provided
        if (tabIndex != null) {
            editor.putInt("lastTabIndex", tabIndex)
        }
        
        // Save the active queue source
        editor.putString("lastQueueSource", activeQueueSource)
        
        // If we're in a playlist, save the playlist ID
        if (activeQueueSource == "Playlist" && currentPlaylistId != null) {
            editor.putString("lastPlaylistId", currentPlaylistId)
        }
        
        editor.apply()
    }
    
    // Function to queue songs from a playlist
    fun queueSongsFromPlaylist(selectedTrack: MusicFile, playlistSongs: List<MusicFile>, queueSource: String) {
        // Update the active queue source
        activeQueueSource = queueSource
        
        // Create a fresh copy of the playlist songs to avoid any reference issues
        val songsList = playlistSongs.toList()
        currentQueueSongs = songsList
        
        // Debug log the playlist songs
        println("DEBUG: Setting queue for $queueSource with ${songsList.size} songs")
        songsList.forEachIndexed { index, song ->
            println("DEBUG: Queue song $index: ${song.title} (ID: ${song.id})")
        }
        
        // Stop playback and clear the current playlist
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        
        // Find the index of the selected track in the playlist
        val selectedIndex = songsList.indexOfFirst { it.id == selectedTrack.id }
        println("DEBUG: Selected track ${selectedTrack.title} (ID: ${selectedTrack.id}) at index $selectedIndex")
        
        if (selectedIndex == -1) {
            println("ERROR: Selected track not found in playlist songs!")
            return
        }
        
        // Add all songs from the playlist to the queue
        for (song in songsList) {
            val mediaItem = MediaItem.Builder()
                .setUri(song.uri)
                .setMediaId(song.id.toString())
                .build()
            exoPlayer.addMediaItem(mediaItem)
        }
        
        // Seek to the selected track
        exoPlayer.seekTo(selectedIndex, 0)
        
        // Prepare and play
        exoPlayer.prepare()
        
        // The service will be started by the activity when playback begins
        
        exoPlayer.play()
        
        // Verify the queue is set up correctly
        println("DEBUG: ExoPlayer queue has ${exoPlayer.mediaItemCount} items")
        println("DEBUG: ExoPlayer is now playing item at index ${exoPlayer.currentMediaItemIndex}")
    }
    
    // Set up ExoPlayer listener with optimizations to prevent ANRs
    DisposableEffect(exoPlayer) {
        // Track the last event processing time to prevent too frequent updates
        var lastEventProcessingTime = 0L
        val MIN_EVENT_PROCESSING_INTERVAL = 100L // Minimum time between event processing in ms
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                try {
                    isPlaying = playing
                } catch (e: Exception) {
                    println("ERROR: Exception in onIsPlayingChanged: ${e.message}")
                }
            }
            
            override fun onEvents(player: Player, events: Player.Events) {
                try {
                    // Apply debouncing to prevent too frequent updates
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastEventProcessingTime < MIN_EVENT_PROCESSING_INTERVAL) {
                        return
                    }
                    lastEventProcessingTime = currentTime
                    
                    if (events.containsAny(Player.EVENT_POSITION_DISCONTINUITY, Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                        duration = player.duration.coerceAtLeast(0L)
                        
                        // Update selected track when media item changes (e.g., when next/previous is clicked)
                        val currentIndex = player.currentMediaItemIndex
                        if (currentIndex >= 0 && currentIndex < player.mediaItemCount) {
                            val currentUri = player.getMediaItemAt(currentIndex).localConfiguration?.uri
                            currentUri?.let { uri ->
                                // First try to find the track in the current queue songs
                                // This ensures we're using the correct track instance from the active queue
                                val newTrack = if (currentQueueSongs.isNotEmpty()) {
                                    currentQueueSongs.find { it.uri == uri }
                                } else {
                                    musicLibrary.getAllMusicFiles().find { it.uri == uri }
                                }
                                
                                if (newTrack != null && newTrack != selectedTrack) {
                                    selectedTrack = newTrack
                                    musicLibrary.setCurrentlyPlayingTrack(newTrack)
                                    println("DEBUG: Track changed to ${newTrack.title} from ${activeQueueSource}")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    println("ERROR: Exception in onEvents: ${e.message}")
                }
            }
            
            override fun onPlaybackStateChanged(state: Int) {
                try {
                    if (state == Player.STATE_READY) {
                        duration = exoPlayer.duration.coerceAtLeast(0L)
                    }
                } catch (e: Exception) {
                    println("ERROR: Exception in onPlaybackStateChanged: ${e.message}")
                }
            }
        }
        
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }
    
    // Update current position periodically with optimized update frequency to prevent ANRs
    LaunchedEffect(isPlaying) {
        try {
            while (isPlaying) {
                try {
                    // Use a less frequent update interval to reduce UI pressure
                    // 200ms is still smooth enough for the slider but reduces CPU usage by 50%
                    delay(200) 
                    
                    try {
                        // Update position safely
                        val position = exoPlayer.currentPosition
                        if (position >= 0) {
                            currentPosition = position
                        }
                        
                        // Update duration as well in case it wasn't properly set, but less frequently
                        val playerDuration = exoPlayer.duration
                        if (duration <= 0 && playerDuration > 0) {
                            duration = playerDuration
                        }
                    } catch (e: Exception) {
                        // Ignore ExoPlayer access errors
                    }
                } catch (e: Exception) {
                    // Catch any exceptions to prevent coroutine cancellation
                    println("ERROR: Exception in position update loop: ${e.message}")
                    delay(1000) // Longer delay if there was an error
                }
            }
        } catch (e: Exception) {
            println("ERROR: Fatal exception in position update coroutine: ${e.message}")
        }
    }
    
    // Function to update the music service with the current track information
    fun updateMusicService(track: MusicFile?) {
        track?.let {
            // Update the music service with the track information
            musicService?.updateTrackInfo(
                title = it.title ?: "Unknown",
                artist = it.artist ?: "Unknown"
            )
            // Force an immediate notification update
            musicService?.updateNotificationNow()
            println("DEBUG: Updated music service with track: ${it.title} by ${it.artist}")
        }
    }
    
    // Function to load music files from the device with optimizations to prevent ANRs
    suspend fun loadAllMusic() {
        isLoading = true
        try {
            // Use withContext to ensure we're on the IO dispatcher for heavy operations
            withContext(Dispatchers.IO) {
                try {
                    // Disable online metadata fetching by default to prevent ANRs
                    val files = com.example.jamzzz.loadAllMusicFilesFromDevice(context, null, null, false)
                    
                    // Switch back to the Main dispatcher for UI updates
                    withContext(Dispatchers.Main) {
                        musicFiles = files
                        
                        // Set the music files in the MusicLibrary for later use
                        musicLibrary.setAllMusicFiles(files)
                        
                        // If we have an initialTrackId, try to select that track
                        if (initialTrackId != null) {
                            val track = files.find { it.id == initialTrackId }
                            if (track != null) {
                                selectedTrack = track
                                
                                try {
                                    // Set up the queue with all songs
                                    println("DEBUG: Setting up initial queue with all songs")
                                    // Store all songs for proper queue navigation
                                    val allSongs = ArrayList(files)
                                    currentQueueSongs = allSongs
                                    activeQueueSource = "AllSongs"
                                    
                                    // Stop playback and clear the current playlist
                                    exoPlayer.stop()
                                    exoPlayer.clearMediaItems()
                                    
                                    // Find the index of the selected track
                                    val selectedIndex = allSongs.indexOfFirst { it.id == track.id }
                                    println("DEBUG: Selected track ${track.title} at index $selectedIndex")
                                    
                                    // Add all songs to the queue
                                    for (song in allSongs) {
                                        val mediaItem = MediaItem.Builder()
                                            .setUri(song.uri)
                                            .setMediaId(song.id.toString())
                                            .build()
                                        exoPlayer.addMediaItem(mediaItem)
                                    }
                                    
                                    // Seek to the selected track
                                    if (selectedIndex >= 0) {
                                        exoPlayer.seekTo(selectedIndex, 0)
                                    }
                                    
                                    // Prepare the player
                                    exoPlayer.prepare()
                                    
                                    // Update the music service with the track information
                                    updateMusicService(track)
                                    // Don't auto-play, let the user click play
                                    
                                    // Verify the queue is set up correctly
                                    println("DEBUG: ExoPlayer queue has ${exoPlayer.mediaItemCount} items")
                                    println("DEBUG: ExoPlayer is now at index ${exoPlayer.currentMediaItemIndex}")
                                } catch (e: Exception) {
                                    println("ERROR: Failed to initialize ExoPlayer: ${e.message}")
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    println("ERROR: Failed to load music files: ${e.message}")
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            println("ERROR: Fatal exception in loadAllMusic: ${e.message}")
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
                        
                        // Update the currently playing track in MusicLibrary
                        musicLibrary.setCurrentlyPlayingTrack(track)
                        
                        // Update the active queue source
                        activeQueueSource = "AllSongs"
                        
                        // Save track selection and tab to preferences
                        saveCurrentState(tabIndex = currentTabIndex)
                        
                        // Update the music service with track information
                        updateMusicService(track)
                        
                        // Store all songs for proper queue navigation
                        val allSongs = ArrayList(musicFiles)
                        
                        // Queue all songs from All Songs tab
                        queueSongsFromPlaylist(track, allSongs, "AllSongs")
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
                        
                        // Update the currently playing track in MusicLibrary
                        musicLibrary.setCurrentlyPlayingTrack(track)
                        
                        // Update the active queue source
                        activeQueueSource = "Favorites"
                        
                        // Save track selection and tab to preferences
                        saveCurrentState(tabIndex = currentTabIndex)
                        
                        // Update the music service with track information
                        updateMusicService(track)
                        
                        // Queue all favorite songs
                        // Convert favorite URIs to MusicFile objects
                        val allFiles = musicLibrary.getAllMusicFiles()
                        val favoriteMusicFiles = musicLibrary.favorites.mapNotNull { favoriteUri ->
                            allFiles.find { it.uri.toString() == favoriteUri }
                        }
                        
                        // Debug log the favorite songs
                        println("DEBUG: Setting queue for Favorites with ${favoriteMusicFiles.size} songs")
                        favoriteMusicFiles.forEachIndexed { index, song ->
                            println("DEBUG: Favorites song $index: ${song.title} (ID: ${song.id})")
                        }
                        
                        // Store favorite songs for proper queue navigation
                        val favoritesList = ArrayList(favoriteMusicFiles)
                        
                        // Use the updated queueSongsFromPlaylist function
                        queueSongsFromPlaylist(track, favoritesList, "Favorites")
                    }
                )
            }
        ),
        TabItem(
            title = "Playlists",
            icon = Icons.Filled.List,
            screen = {
                // If we have a saved playlist ID and we're in the Playlists tab, try to restore it
                LaunchedEffect(Unit) {
                    if (activeQueueSource == "Playlist" && currentPlaylistId != null) {
                        // Find the playlist by ID
                        val savedPlaylist = musicLibrary.playlists.find { it.id == currentPlaylistId }
                        if (savedPlaylist != null) {
                            println("DEBUG: Restoring playlist ${savedPlaylist.name}")
                            // This will trigger the onPlaylistSelected callback
                        }
                    }
                }
                
                PlaylistsScreen(
                    musicLibrary = musicLibrary,
                    initialPlaylistId = if (activeQueueSource == "Playlist") currentPlaylistId else null,
                    onPlaylistSelected = { playlist ->
                        // Save the current playlist ID
                        currentPlaylistId = playlist.id
                        // Save to preferences
                        saveCurrentState(tabIndex = currentTabIndex)
                        println("DEBUG: Selected playlist ${playlist.name} with ID ${playlist.id}")
                    },
                    onTrackSelected = { track, playlistSongs ->
                        selectedTrack = track
                        
                        // Update the currently playing track in MusicLibrary
                        musicLibrary.setCurrentlyPlayingTrack(track)
                        
                        // Update the active queue source
                        activeQueueSource = "Playlist"
                        
                        // Make sure we have the current playlist ID
                        if (currentPlaylistId == null) {
                            // Try to find the playlist ID from the musicLibrary
                            val playlist = musicLibrary.playlists.find { playlist ->
                                playlist.songs.any { songUri ->
                                    track.uri.toString() == songUri
                                }
                            }
                            if (playlist != null) {
                                currentPlaylistId = playlist.id
                            }
                        }
                        
                        // Save track selection and playlist to preferences
                        saveCurrentState(tabIndex = currentTabIndex)
                        
                        // Update the music service with track information
                        updateMusicService(track)
                        
                        // Store the current playlist songs for proper queue navigation
                        val currentPlaylistSongs = ArrayList(playlistSongs)
                        
                        // Make sure we're using the correct playlist songs for the queue
                        // This ensures next/previous buttons will navigate within this playlist
                        queueSongsFromPlaylist(track, currentPlaylistSongs, "Playlist")
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
                            // Open Equalizer activity with the proper audio session ID
                            val intent = Intent(context, EqualizerActivity::class.java).apply {
                                putExtra("audioSessionId", exoPlayer.audioSessionId)
                            }
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
                    TabbedScreen(
                        tabs = tabs,
                        initialTabIndex = initialTabIndex,
                        onTabChanged = { newTabIndex ->
                            // Update the current tab index
                            currentTabIndex = newTabIndex
                            // Save the tab index to preferences
                            saveCurrentState(tabIndex = newTabIndex)
                            println("DEBUG: Tab changed to $newTabIndex")
                        }
                    )
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
                        println("DEBUG: Previous track action triggered in PlayerUI")
                        try {
                            // Only proceed if we have a queue set up
                            if (currentQueueSongs.isNotEmpty()) {
                                // Use the ExoPlayer's built-in previous functionality
                                if (exoPlayer.hasPreviousMediaItem()) {
                                    println("DEBUG: ExoPlayer has previous media item, seeking to previous")
                                    // First seek to the beginning if we're more than 3 seconds into the song
                                    if (exoPlayer.currentPosition > 3000) {
                                        println("DEBUG: Current position > 3s, seeking to beginning of current track")
                                        exoPlayer.seekTo(0)
                                    } else {
                                        // Otherwise go to the previous track
                                        println("DEBUG: Seeking to previous track")
                                        exoPlayer.seekToPrevious()
                                        
                                        // Update the selected track based on the current media item
                                        val currentMediaItem = exoPlayer.currentMediaItem
                                        if (currentMediaItem != null) {
                                            val currentUri = currentMediaItem.localConfiguration?.uri
                                            if (currentUri != null) {
                                                // Find the track in the current queue
                                                val track = currentQueueSongs.find { it.uri == currentUri }
                                                if (track != null) {
                                                    selectedTrack = track
                                                    // Update the currently playing track in MusicLibrary
                                                    musicLibrary.setCurrentlyPlayingTrack(track)
                                                    // Save track selection to preferences
                                                    saveCurrentState()
                                                    // Update the music service with track information
                                                    updateMusicService(track)
                                                    println("DEBUG: Moved to previous track: ${track.title}")
                                                } else {
                                                    println("DEBUG: Could not find track in queue for URI: $currentUri")
                                                }
                                            } else {
                                                println("DEBUG: Current media item has null URI")
                                            }
                                        } else {
                                            println("DEBUG: Current media item is null after seeking to previous")
                                        }
                                    }
                                } else {
                                    println("DEBUG: No previous track available in the queue")
                                }
                            } else {
                                println("DEBUG: Queue is empty, cannot move to previous track")
                            }
                        } catch (e: Exception) {
                            println("ERROR: Exception in previousTrackAction: ${e.message}")
                            e.printStackTrace()
                        }
                    }
                    
                    val nextTrackAction: () -> Unit = {
                        println("DEBUG: Next track action triggered in PlayerUI")
                        try {
                            // Only proceed if we have a queue set up
                            if (currentQueueSongs.isNotEmpty()) {
                                // Use the ExoPlayer's built-in next functionality
                                if (exoPlayer.hasNextMediaItem()) {
                                    println("DEBUG: ExoPlayer has next media item, seeking to next")
                                    exoPlayer.seekToNext()
                                    
                                    // Update the selected track based on the current media item
                                    val currentMediaItem = exoPlayer.currentMediaItem
                                    if (currentMediaItem != null) {
                                        val currentUri = currentMediaItem.localConfiguration?.uri
                                        if (currentUri != null) {
                                            // Find the track in the current queue
                                            val track = currentQueueSongs.find { it.uri == currentUri }
                                            if (track != null) {
                                                selectedTrack = track
                                                // Update the currently playing track in MusicLibrary
                                                musicLibrary.setCurrentlyPlayingTrack(track)
                                                // Save track selection to preferences
                                                saveCurrentState()
                                                // Update the music service with track information
                                                updateMusicService(track)
                                                println("DEBUG: Moved to next track: ${track.title}")
                                            } else {
                                                println("DEBUG: Could not find track in queue for URI: $currentUri")
                                            }
                                        } else {
                                            println("DEBUG: Current media item has null URI")
                                        }
                                    } else {
                                        println("DEBUG: Current media item is null after seeking to next")
                                    }
                                } else {
                                    println("DEBUG: No next track available in the queue")
                                }
                            } else {
                                println("DEBUG: Queue is empty, cannot move to next track")
                            }
                        } catch (e: Exception) {
                            println("ERROR: Exception in nextTrackAction: ${e.message}")
                            e.printStackTrace()
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
                        onNextClick = nextTrackAction,
                        onOpenEqualizer = onOpenEqualizer
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