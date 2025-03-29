package com.example.jamzzz.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.example.jamzzz.MusicFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

// Data class for a playlist
@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val songs: List<String> // List of song URIs
)

// Class to manage favorites and playlists
class MusicLibrary(private val context: Context) {
    // Favorites list (song URIs)
    private val _favorites = mutableStateListOf<String>()
    val favorites: SnapshotStateList<String> = _favorites
    
    // Playlists
    private val _playlists = mutableStateListOf<Playlist>()
    val playlists: SnapshotStateList<Playlist> = _playlists
    
    init {
        loadFavorites()
        loadPlaylists()
    }
    
    // Add a song to favorites
    fun addToFavorites(musicFile: MusicFile) {
        if (!_favorites.contains(musicFile.uri.toString())) {
            _favorites.add(musicFile.uri.toString())
            saveFavorites()
        }
    }
    
    // Remove a song from favorites
    fun removeFromFavorites(musicFile: MusicFile) {
        _favorites.remove(musicFile.uri.toString())
        saveFavorites()
    }
    
    // Check if a song is in favorites
    fun isFavorite(musicFile: MusicFile): Boolean {
        return _favorites.contains(musicFile.uri.toString())
    }
    
    // Create a new playlist
    fun createPlaylist(name: String): Playlist {
        val id = System.currentTimeMillis().toString()
        val playlist = Playlist(id, name, emptyList())
        _playlists.add(playlist)
        savePlaylists()
        return playlist
    }
    
    // Add a song to a playlist
    fun addToPlaylist(playlistId: String, musicFile: MusicFile) {
        val index = _playlists.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            val playlist = _playlists[index]
            val uriString = musicFile.uri.toString()
            if (!playlist.songs.contains(uriString)) {
                val updatedSongs = playlist.songs + uriString
                _playlists[index] = playlist.copy(songs = updatedSongs)
                savePlaylists()
            }
        }
    }
    
    // Remove a song from a playlist
    fun removeFromPlaylist(playlistId: String, musicFile: MusicFile) {
        val index = _playlists.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            val playlist = _playlists[index]
            val uriString = musicFile.uri.toString()
            val updatedSongs = playlist.songs.filter { it != uriString }
            _playlists[index] = playlist.copy(songs = updatedSongs)
            savePlaylists()
        }
    }
    
    // Delete a playlist
    fun deletePlaylist(playlistId: String) {
        _playlists.removeIf { it.id == playlistId }
        savePlaylists()
    }
    
    // Rename a playlist
    fun renamePlaylist(playlistId: String, newName: String) {
        val index = _playlists.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            val playlist = _playlists[index]
            _playlists[index] = playlist.copy(name = newName)
            savePlaylists()
        }
    }
    
    // Save favorites to SharedPreferences
    private fun saveFavorites() {
        val prefs = context.getSharedPreferences("jamzzz_preferences", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("favorites", _favorites.toSet()).apply()
    }
    
    // Load favorites from SharedPreferences
    private fun loadFavorites() {
        val prefs = context.getSharedPreferences("jamzzz_preferences", Context.MODE_PRIVATE)
        val favSet = prefs.getStringSet("favorites", emptySet()) ?: emptySet()
        _favorites.clear()
        _favorites.addAll(favSet)
    }
    
    // Save playlists to a file
    private fun savePlaylists() {
        try {
            val json = Json { prettyPrint = true }
            val playlistsJson = json.encodeToString(_playlists.toList())
            
            val file = File(context.filesDir, "playlists.json")
            file.writeText(playlistsJson)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    // Load playlists from a file
    private fun loadPlaylists() {
        try {
            val file = File(context.filesDir, "playlists.json")
            if (file.exists()) {
                val json = Json { ignoreUnknownKeys = true }
                val playlistsJson = file.readText()
                val loadedPlaylists = json.decodeFromString<List<Playlist>>(playlistsJson)
                
                _playlists.clear()
                _playlists.addAll(loadedPlaylists)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
