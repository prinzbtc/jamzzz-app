package com.example.jamzzz.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import com.example.jamzzz.MusicFile
import com.example.jamzzz.data.MusicLibrary
import com.example.jamzzz.data.Playlist
import com.example.jamzzz.ui.components.EmptyState
import com.example.jamzzz.ui.components.PlaylistItem
import com.example.jamzzz.ui.theme.TextWhite
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.activity.compose.BackHandler

// All Songs Tab
@Composable
fun AllSongsScreen(
    musicFiles: List<MusicFile>,
    selectedTrack: MusicFile?,
    onTrackSelected: (MusicFile) -> Unit,
    musicLibrary: MusicLibrary,
    onBrowseMusic: () -> Unit
) {
    if (musicFiles.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.MusicNote,
            message = "No music files found",
            actionText = "Browse Music",
            onAction = onBrowseMusic
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp) // Add padding for player controls
        ) {
            items(musicFiles) { musicFile ->
                MusicFileItem(
                    musicFile = musicFile,
                    isSelected = selectedTrack?.id == musicFile.id,
                    isFavorite = musicLibrary.isFavorite(musicFile),
                    onTrackSelected = { onTrackSelected(musicFile) },
                    onToggleFavorite = {
                        if (musicLibrary.isFavorite(musicFile)) {
                            musicLibrary.removeFromFavorites(musicFile)
                        } else {
                            musicLibrary.addToFavorites(musicFile)
                        }
                    },
                    onAddToPlaylist = { 
                        // Check if there are existing playlists
                        if (musicLibrary.playlists.isEmpty()) {
                            // No playlists exist, show dialog to create a new one
                            true // We'll handle this in the MusicFileItem composable
                        } else {
                            // Show dialog with list of existing playlists
                            false // We'll handle this in the MusicFileItem composable
                        }
                    },
                    musicLibrary = musicLibrary
                )
            }
        }
    }
}

// Favorites Tab
@Composable
fun FavoritesScreen(
    musicFiles: List<MusicFile>,
    selectedTrack: MusicFile?,
    onTrackSelected: (MusicFile) -> Unit,
    musicLibrary: MusicLibrary
) {
    // Use the same ordering as in the MusicLibrary's favorites list
    val favoriteFiles = remember(musicFiles, musicLibrary.favorites) {
        // Convert favorite URIs to MusicFile objects in the same order as they appear in favorites
        musicLibrary.favorites.mapNotNull { favoriteUri ->
            musicFiles.find { it.uri.toString() == favoriteUri }
        }
    }
    
    // Debug log the favorite songs in the UI
    LaunchedEffect(favoriteFiles) {
        println("DEBUG: FavoritesScreen displaying ${favoriteFiles.size} songs")
        favoriteFiles.forEachIndexed { index, song ->
            println("DEBUG: UI Favorites song $index: ${song.title} (ID: ${song.id})")
        }
    }
    
    if (favoriteFiles.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.Favorite,
            message = "No favorite songs yet",
            actionText = "Browse Music",
            onAction = null // Will be connected to the tab change later
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp) // Add padding for player controls
        ) {
            items(favoriteFiles) { musicFile ->
                MusicFileItem(
                    musicFile = musicFile,
                    isSelected = selectedTrack?.id == musicFile.id,
                    isFavorite = true, // Always true in favorites tab
                    onTrackSelected = { onTrackSelected(musicFile) },
                    onToggleFavorite = {
                        musicLibrary.removeFromFavorites(musicFile)
                    },
                    onAddToPlaylist = { 
                        // Check if there are existing playlists
                        if (musicLibrary.playlists.isEmpty()) {
                            // No playlists exist, show dialog to create a new one
                            true // We'll handle this in the MusicFileItem composable
                        } else {
                            // Show dialog with list of existing playlists
                            false // We'll handle this in the MusicFileItem composable
                        }
                    },
                    musicLibrary = musicLibrary
                )
            }
        }
    }
}

// Playlists Tab
@Composable
fun PlaylistsScreen(
    musicLibrary: MusicLibrary,
    initialPlaylistId: String? = null,
    onPlaylistSelected: (Playlist) -> Unit,
    onTrackSelected: (MusicFile, List<MusicFile>) -> Unit = { _, _ -> }
) {
    // State variables
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    
    // Simplified playlist selection state
    var selectedPlaylistId by remember { mutableStateOf(initialPlaylistId) }
    
    // Derive the selected playlist from the ID
    val selectedPlaylist = if (selectedPlaylistId != null) {
        musicLibrary.playlists.find { it.id == selectedPlaylistId }
    } else {
        null
    }
    
    // If we have an initialPlaylistId and a valid playlist, notify the parent component
    LaunchedEffect(Unit) {
        if (initialPlaylistId != null && selectedPlaylist != null) {
            onPlaylistSelected(selectedPlaylist)
            println("DEBUG: Restored playlist ${selectedPlaylist.name} from saved state")
        }
    }
    
    // Simple function to go back to the playlists list
    fun goBackToPlaylistsList() {
        selectedPlaylistId = null
        musicLibrary.saveLastPlaylistId(null)
        println("DEBUG: Navigated back to playlists list")
    }
    
    // Handle back button press when viewing a playlist's songs
    BackHandler(enabled = selectedPlaylist != null) {
        goBackToPlaylistsList()
    }
    
    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("Create Playlist") },
            text = {
                TextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Playlist Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            musicLibrary.createPlaylist(newPlaylistName)
                            newPlaylistName = ""
                            showCreatePlaylistDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                Button(
                    onClick = { showCreatePlaylistDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
    
    if (selectedPlaylist == null) {
        // Show playlists list
        if (musicLibrary.playlists.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.QueueMusic,
                message = "No playlists yet",
                actionText = "Create Playlist",
                onAction = { showCreatePlaylistDialog = true }
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Your Playlists",
                        style = MaterialTheme.typography.h6,
                        color = TextWhite
                    )
                    
                    IconButton(
                        onClick = { showCreatePlaylistDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Create Playlist",
                            tint = MaterialTheme.colors.primary
                        )
                    }
                }
                
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(musicLibrary.playlists) { playlist ->
                        PlaylistItem(
                            name = playlist.name,
                            songCount = playlist.songs.size,
                            onClick = { 
                                // Update the selected playlist ID
                                selectedPlaylistId = playlist.id
                                // Save the selected playlist ID to preferences
                                musicLibrary.saveLastPlaylistId(playlist.id)
                                // Also notify the parent component
                                onPlaylistSelected(playlist)
                                println("DEBUG: Selected playlist ${playlist.name} with ID ${playlist.id}")
                            },
                            onRenameClick = { /* Will implement rename later */ },
                            onDeleteClick = { /* Will implement delete later */ }
                        )
                    }
                }
            }
        }
    } else {
        // Show selected playlist songs
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { 
                        println("DEBUG: Back button clicked in UI, returning to playlists list")
                        // Use the helper function to go back to playlists list
                        goBackToPlaylistsList()
                    }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back to Playlists",
                            tint = MaterialTheme.colors.primary
                        )
                    }
                    Text(
                        text = selectedPlaylist!!.name,
                        style = MaterialTheme.typography.h6,
                        color = TextWhite
                    )
                }
                
                if (selectedPlaylist!!.songs.isNotEmpty()) {
                    IconButton(onClick = {
                        // Find the first song in the playlist from all music files
                        val allMusicFiles = musicLibrary.getAllMusicFiles()
                        val firstSongUri = selectedPlaylist!!.songs.first()
                        val firstSong = allMusicFiles.find { it.uri.toString() == firstSongUri }
                        
                        // Convert playlist song URIs to MusicFile objects
                        // Maintain the order of songs as they appear in the playlist
                        val playlistMusicFiles = selectedPlaylist!!.songs.mapNotNull { songUri ->
                            allMusicFiles.find { it.uri.toString() == songUri }
                        }
                        
                        // Debug log
                        println("DEBUG: Play all button clicked for playlist ${selectedPlaylist!!.name}")
                        playlistMusicFiles.forEachIndexed { index, song ->
                            println("DEBUG: Queue song $index: ${song.title}")
                        }
                        
                        // Play the first song if found
                        if (firstSong != null && playlistMusicFiles.isNotEmpty()) {
                            println("DEBUG: Playing first song ${firstSong.title} from playlist")
                            onTrackSelected(firstSong, playlistMusicFiles)
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play All",
                            tint = MaterialTheme.colors.primary
                        )
                    }
                }
            }
            
            if (selectedPlaylist!!.songs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No songs in this playlist",
                        color = TextWhite
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    // Get all music files to find the ones in this playlist
                    val allMusicFiles = musicLibrary.getAllMusicFiles()
                    
                    // Convert song URIs to MusicFile objects
                    // Maintain the order of songs as they appear in the playlist
                    val playlistMusicFiles = selectedPlaylist!!.songs.mapNotNull { songUri ->
                        allMusicFiles.find { it.uri.toString() == songUri }
                    }
                    
                    // Debug log the playlist songs
                    println("DEBUG: Playlist ${selectedPlaylist!!.name} has ${playlistMusicFiles.size} songs")
                    playlistMusicFiles.forEachIndexed { index, song ->
                        println("DEBUG: Playlist song $index: ${song.title}")
                    }
                    
                    items(playlistMusicFiles) { musicFile ->
                        MusicFileItem(
                            musicFile = musicFile,
                            isSelected = musicFile.id == musicLibrary.getCurrentlyPlayingTrack()?.id,
                            isFavorite = musicLibrary.isFavorite(musicFile),
                            onTrackSelected = { 
                                // Make sure we're passing the exact playlist songs list
                                println("DEBUG: Selected ${musicFile.title} from playlist ${selectedPlaylist!!.name}")
                                onTrackSelected(musicFile, playlistMusicFiles)
                            },
                            onToggleFavorite = {
                                if (musicLibrary.isFavorite(musicFile)) {
                                    musicLibrary.removeFromFavorites(musicFile)
                                } else {
                                    musicLibrary.addToFavorites(musicFile)
                                }
                            },
                            onAddToPlaylist = { false },
                            musicLibrary = musicLibrary
                        )
                    }
                }
            }
        }
    }
}

// Show dialog to create a new playlist and add the song to it
@Composable
fun ShowCreatePlaylistDialog(musicFile: MusicFile, musicLibrary: MusicLibrary) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(true) }
    var playlistName by remember { mutableStateOf("") }
    
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Create New Playlist") },
            text = {
                Column {
                    Text("Enter a name for your new playlist:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = playlistName,
                        onValueChange = { playlistName = it },
                        label = { Text("Playlist Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (playlistName.isNotBlank()) {
                            // Create new playlist and add song to it
                            val newPlaylist = musicLibrary.createPlaylist(playlistName)
                            musicLibrary.addToPlaylist(newPlaylist.id, musicFile)
                            Toast.makeText(context, "Added to new playlist: ${playlistName}", Toast.LENGTH_SHORT).show()
                            showDialog = false
                        } else {
                            Toast.makeText(context, "Please enter a playlist name", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// Show dialog to select from existing playlists
@Composable
fun ShowSelectPlaylistDialog(musicFile: MusicFile, musicLibrary: MusicLibrary) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(true) }
    var showCreateNewDialog by remember { mutableStateOf(false) }
    
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Add to Playlist") },
            text = {
                Column {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp)
                    ) {
                        val playlistNames = musicLibrary.playlists.map { it.name }
                        items(playlistNames) { playlistName ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // Find the playlist by name and add song to it
                                        val selectedPlaylist = musicLibrary.playlists.find { it.name == playlistName }
                                        if (selectedPlaylist != null) {
                                            musicLibrary.addToPlaylist(selectedPlaylist.id, musicFile)
                                            Toast.makeText(context, "Added to playlist: $playlistName", Toast.LENGTH_SHORT).show()
                                        }
                                        showDialog = false
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.QueueMusic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colors.secondary
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = playlistName,
                                    style = MaterialTheme.typography.body1
                                )
                            }
                        }
                    
                        // Option to create a new playlist
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showDialog = false
                                        showCreateNewDialog = true
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colors.secondary
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = "Create New Playlist",
                                    style = MaterialTheme.typography.body1
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Show create new playlist dialog if requested
    if (showCreateNewDialog) {
        ShowCreatePlaylistDialog(musicFile, musicLibrary)
    }
}

// Music File Item
@Composable
fun MusicFileItem(
    musicFile: MusicFile,
    isSelected: Boolean,
    isFavorite: Boolean,
    onTrackSelected: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Boolean, // Return true if no playlists exist, false otherwise
    musicLibrary: MusicLibrary
) {
    var showMenu by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showSelectPlaylistDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable(onClick = onTrackSelected),
        elevation = 2.dp,
        backgroundColor = if (isSelected) 
            MaterialTheme.colors.primary.copy(alpha = 0.3f) 
            else MaterialTheme.colors.surface.copy(alpha = 0.9f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = "Music File",
                tint = MaterialTheme.colors.secondary,
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = musicFile.title ?: musicFile.uri.toString().substringAfterLast('/'),
                    style = MaterialTheme.typography.body1,
                    color = TextWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                musicFile.artist?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.caption,
                        color = TextWhite.copy(alpha = 0.7f)
                    )
                }
            }
            
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                    tint = if (isFavorite) MaterialTheme.colors.secondary else TextWhite.copy(alpha = 0.7f)
                )
            }
            
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "More Options",
                    tint = TextWhite.copy(alpha = 0.7f)
                )
            }
            
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(onClick = {
                    val noPlaylistsExist = onAddToPlaylist()
                    showMenu = false
                    
                    // Handle playlist dialog based on whether playlists exist
                    if (noPlaylistsExist) {
                        showCreatePlaylistDialog = true
                    } else {
                        showSelectPlaylistDialog = true
                    }
                }) {
                    Icon(
                        imageVector = Icons.Filled.PlaylistAdd,
                        contentDescription = "Add to Playlist"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add to Playlist")
                }
            }
        }
    }
    
    // Show appropriate dialog based on state
    if (showCreatePlaylistDialog) {
        ShowCreatePlaylistDialog(
            musicFile = musicFile,
            musicLibrary = musicLibrary
        )
    }
    
    if (showSelectPlaylistDialog) {
        ShowSelectPlaylistDialog(
            musicFile = musicFile,
            musicLibrary = musicLibrary
        )
    }
}
