package com.example.jamzzz

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class MusicFile(
    val id: Long,
    val title: String,
    val artist: String,
    val duration: Long,
    val uri: Uri
)

@Composable
fun MusicBrowserScreen(onBackPressed: () -> Unit, onMusicSelected: (MusicFile) -> Unit) {
    val context = LocalContext.current
    var musicFiles by remember { mutableStateOf<List<MusicFile>>(emptyList()) }
    var permissionGranted by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var showFolderBrowser by remember { mutableStateOf(false) }
    var selectedFolder by remember { mutableStateOf<java.io.File?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allPermissionsGranted = permissions.all { it.value }
        if (allPermissionsGranted) {
            permissionGranted = true
            showFolderBrowser = true
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
                // Don't load files yet, wait for folder selection
                if (selectedFolder == null) {
                    showFolderBrowser = true
                }
            }
            else -> {
                permissionLauncher.launch(arrayOf(readPermission))
            }
        }
    }
    
    // Load music files when a folder is selected
    LaunchedEffect(selectedFolder) {
        selectedFolder?.let { folder ->
            isLoading = true
            coroutineScope.launch {
                try {
                    musicFiles = loadMusicFilesFromFolder(context, folder)
                } catch (e: Exception) {
                    // Handle error
                } finally {
                    isLoading = false
                }
            }
        }
    }

    if (showFolderBrowser) {
        FolderBrowserScreen(
            onBackPressed = onBackPressed,
            onFolderSelected = { folder ->
                selectedFolder = folder
                showFolderBrowser = false
            }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(selectedFolder?.name ?: "Music Browser") },
                    navigationIcon = {
                        IconButton(onClick = onBackPressed) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showFolderBrowser = true }) {
                            Icon(Icons.Filled.Folder, contentDescription = "Change Folder")
                        }
                    }
                )
            }
        ) { paddingValues ->
            if (!permissionGranted) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Permission required to access music files")
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            val readPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                Manifest.permission.READ_MEDIA_AUDIO
                            } else {
                                Manifest.permission.READ_EXTERNAL_STORAGE
                            }
                            permissionLauncher.launch(arrayOf(readPermission))
                        }) {
                            Text("Grant Permission")
                        }
                    }
                }
            } else if (selectedFolder == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Button(onClick = { showFolderBrowser = true }) {
                        Text("Select Music Folder")
                    }
                }
            } else if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (musicFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No music files found in this folder")
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { showFolderBrowser = true }) {
                            Text("Select Another Folder")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    items(musicFiles) { musicFile ->
                        MusicFileItem(musicFile = musicFile, onClick = { onMusicSelected(musicFile) })
                    }
                }
            }
        }
    }
}

@Composable
fun MusicFileItem(musicFile: MusicFile, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = musicFile.title,
                style = MaterialTheme.typography.subtitle1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = musicFile.artist,
                style = MaterialTheme.typography.body2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = formatDuration(musicFile.duration),
            style = MaterialTheme.typography.caption
        )
    }
    Divider()
}

fun formatDuration(duration: Long): String {
    if (duration <= 0) return "0:00"
    
    val totalSeconds = duration / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

suspend fun loadMusicFilesFromFolder(context: Context, folder: java.io.File): List<MusicFile> = withContext(Dispatchers.IO) {
    val musicFiles = mutableListOf<MusicFile>()
    
    try {
        // Method 1: Using MediaStore with folder path filter
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        
        val folderPath = folder.absolutePath
        
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )
        
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DATA} LIKE ?"
        val selectionArgs = arrayOf("$folderPath/%")
        
        context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn) ?: "Unknown Title"
                val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                val duration = cursor.getLong(durationColumn)
                
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                
                musicFiles.add(
                    MusicFile(
                        id = id,
                        title = title,
                        artist = artist,
                        duration = duration,
                        uri = contentUri
                    )
                )
            }
        }
        
        // If no files found through MediaStore, try direct file access as fallback
        if (musicFiles.isEmpty()) {
            scanFolderForMusicFiles(folder, musicFiles)
        }
    } catch (e: Exception) {
        // Handle exceptions
        e.printStackTrace()
    }
    
    return@withContext musicFiles
}

/**
 * Load all music files from the device
 * @param context The application context
 * @param exoPlayer Optional ExoPlayer instance to prepare with initial track
 * @param initialTrackId Optional ID of track to select initially
 */
suspend fun loadAllMusicFilesFromDevice(context: Context, exoPlayer: ExoPlayer? = null, initialTrackId: Long? = null): List<MusicFile> = withContext(Dispatchers.IO) {
    val musicFiles = mutableListOf<MusicFile>()
    
    try {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )
        
        // Only get music files, exclude recordings (can be refined further if needed)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        
        context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn) ?: "Unknown Title"
                val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                val duration = cursor.getLong(durationColumn)
                
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                
                musicFiles.add(
                    MusicFile(
                        id = id,
                        title = title,
                        artist = artist,
                        duration = duration,
                        uri = contentUri
                    )
                )
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    
    return@withContext musicFiles
}

private fun scanFolderForMusicFiles(folder: java.io.File, musicFiles: MutableList<MusicFile>) {
    folder.listFiles()?.forEach { file ->
        if (file.isFile && isMusicFile(file)) {
            val uri = Uri.fromFile(file)
            musicFiles.add(
                MusicFile(
                    id = file.hashCode().toLong(),
                    title = file.nameWithoutExtension,
                    artist = "Unknown Artist",
                    duration = 0, // We don't have duration info without MediaMetadataRetriever
                    uri = uri
                )
            )
        }
    }
}

private fun isMusicFile(file: java.io.File): Boolean {
    val musicExtensions = listOf(".mp3", ".wav", ".ogg", ".flac", ".aac", ".m4a")
    val name = file.name.lowercase()
    return musicExtensions.any { name.endsWith(it) }
}
