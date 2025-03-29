package com.example.jamzzz

import android.content.Context
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.jamzzz.ui.theme.GradientBackground
import com.example.jamzzz.ui.theme.JamzzzTheme
import com.example.jamzzz.ui.theme.TextWhite
import java.io.File

@Composable
fun FolderBrowserScreen(
    onBackPressed: () -> Unit,
    onFolderSelected: (File) -> Unit
) {
    val context = LocalContext.current
    var currentPath by remember { mutableStateOf(getInitialPath(context)) }
    var folderContents by remember { mutableStateOf<List<File>>(emptyList()) }
    var musicFilesCount by remember { mutableStateOf<Map<File, Int>>(emptyMap()) }
    
    // Load folders and files when currentPath changes
    LaunchedEffect(currentPath) {
        folderContents = getFolderContents(currentPath)
        // Count music files in each folder
        val counts = mutableMapOf<File, Int>()
        folderContents.filter { it.isDirectory }.forEach { folder ->
            counts[folder] = countMusicFiles(folder)
        }
        musicFilesCount = counts
    }
    
    GradientBackground {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Select Music Folder") },
                    navigationIcon = {
                        IconButton(onClick = onBackPressed) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    backgroundColor = MaterialTheme.colors.primary,
                    contentColor = MaterialTheme.colors.onPrimary
                )
            }
        ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Show current path
            Text(
                text = "Current folder: ${currentPath.name}",
                style = MaterialTheme.typography.subtitle1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            // Go up button
            if (currentPath.absolutePath != Environment.getExternalStorageDirectory().absolutePath) {
                Button(
                    onClick = { 
                        currentPath = currentPath.parentFile ?: currentPath
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Go Up One Level")
                }
            }
            
            // Count music files in current folder
            val musicFilesInCurrentFolder = folderContents.filter { it.isFile && isMusicFile(it) }.size
            
            // Select current folder button with music file count
            Button(
                onClick = { onFolderSelected(currentPath) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (musicFilesInCurrentFolder > 0) MaterialTheme.colors.primary else MaterialTheme.colors.surface
                )
            ) {
                if (musicFilesInCurrentFolder > 0) {
                    Text("Select This Folder (${musicFilesInCurrentFolder} music files)")
                } else {
                    Text("Select This Folder (No music files)")
                }
            }
            
            Divider()
            
            if (folderContents.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Empty folder")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Show folders first
                    val folders = folderContents.filter { it.isDirectory }
                    if (folders.isNotEmpty()) {
                        item {
                            Text(
                                text = "Folders",
                                style = MaterialTheme.typography.subtitle1,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colors.surface)
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        
                        items(folders) { folder ->
                            FolderItem(
                                folder = folder,
                                musicFilesCount = musicFilesCount[folder] ?: 0,
                                onClick = { currentPath = folder }
                            )
                        }
                    }
                    
                    // Show music files
                    val musicFiles = folderContents.filter { it.isFile && isMusicFile(it) }
                    if (musicFiles.isNotEmpty()) {
                        item {
                            Text(
                                text = "Music Files",
                                style = MaterialTheme.typography.subtitle1,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colors.surface)
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        
                        items(musicFiles) { file ->
                            MusicFileItemInBrowser(file = file)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FolderItem(folder: File, musicFilesCount: Int, onClick: () -> Unit) {
    val hasMusic = musicFilesCount > 0
    val folderIcon = if (hasMusic) Icons.Filled.FolderOpen else Icons.Filled.Folder
    val folderTint = if (hasMusic) MaterialTheme.colors.primary else LocalContentColor.current
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = folderIcon,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = folderTint
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name,
                style = MaterialTheme.typography.subtitle1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (hasMusic) {
                Text(
                    text = "$musicFilesCount music files",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.primary
                )
            }
        }
    }
    Divider()
}

@Composable
fun MusicFileItemInBrowser(file: File) {
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
            tint = MaterialTheme.colors.primary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = file.name,
            style = MaterialTheme.typography.body1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
    Divider()
    }
}

private fun getInitialPath(context: Context): File {
    return Environment.getExternalStorageDirectory()
}

private fun getFolderContents(directory: File): List<File> {
    return directory.listFiles()
        ?.filter { !it.isHidden }
        ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        ?: emptyList()
}

private fun countMusicFiles(folder: File): Int {
    return folder.listFiles()
        ?.count { it.isFile && isMusicFile(it) }
        ?: 0
}

private fun isMusicFile(file: File): Boolean {
    val musicExtensions = listOf(".mp3", ".wav", ".ogg", ".flac", ".aac", ".m4a")
    val name = file.name.lowercase()
    return musicExtensions.any { name.endsWith(it) }
}

@Composable
fun FolderItem(folder: File, musicFilesCount: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        elevation = 2.dp,
        backgroundColor = MaterialTheme.colors.surface.copy(alpha = 0.9f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = "Folder",
                tint = MaterialTheme.colors.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.body1,
                    color = TextWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$musicFilesCount music files",
                    style = MaterialTheme.typography.caption,
                    color = TextWhite.copy(alpha = 0.7f)
                )
            }
            Icon(
                imageVector = Icons.Filled.FolderOpen,
                contentDescription = "Open Folder",
                tint = MaterialTheme.colors.primary
            )
        }
    }
}

@Composable
fun MusicFileItemInBrowser(file: File) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        elevation = 2.dp,
        backgroundColor = MaterialTheme.colors.surface.copy(alpha = 0.9f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = "Music File",
                tint = MaterialTheme.colors.secondary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = file.name,
                style = MaterialTheme.typography.body1,
                color = TextWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
