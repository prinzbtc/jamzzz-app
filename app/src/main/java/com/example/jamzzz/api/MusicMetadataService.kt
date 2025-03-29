package com.example.jamzzz.api

import android.net.Uri
import android.util.Log
import com.example.jamzzz.MusicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

private const val TAG = "MusicMetadataService"

/**
 * Service to fetch music metadata and album art
 */
class MusicMetadataService {

    /**
     * Fetch metadata for a music file
     * @param musicFile The music file to fetch metadata for
     * @return Updated music file with metadata
     */
    suspend fun fetchMetadata(musicFile: MusicFile): MusicFile {
        return try {
            // Parse artist and title from filename if needed
            val (artist, title) = parseArtistAndTitle(musicFile.title, musicFile.artist)
            
            Log.d(TAG, "Fetching metadata for: Artist='$artist', Title='$title'")
            
            // Generate album art URL using iTunes API (no API key required)
            val encodedArtist = URLEncoder.encode(artist, "UTF-8")
            val encodedTitle = URLEncoder.encode(title, "UTF-8")
            val albumArtUrl = "https://itunes.apple.com/search?term=$encodedArtist+$encodedTitle&entity=song&limit=1"
            
            Log.d(TAG, "Using album art URL: $albumArtUrl")
            
            // Update music file with the iTunes search URL
            // The actual image URL will be extracted from the JSON response by Coil
            val updatedFile = musicFile.copy(
                albumArtUrl = albumArtUrl
            )
            
            Log.d(TAG, "Updated music file: Artist='${updatedFile.artist}', Title='${updatedFile.title}', AlbumArtUrl='${updatedFile.albumArtUrl}'")
            updatedFile
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching metadata: ${e.message}")
            e.printStackTrace()
            musicFile
        }
    }
    
    /**
     * Parse artist and title from a filename
     * @param filename The filename to parse
     * @param fallbackArtist Fallback artist name if parsing fails
     * @return Pair of artist and title
     */
    private fun parseArtistAndTitle(filename: String, fallbackArtist: String): Pair<String, String> {
        // Try to extract artist and title from filename (e.g., "Artist - Title.mp3")
        val parts = filename.replace(".mp3", "").split(" - ", limit = 2)
        return if (parts.size == 2) {
            Pair(parts[0].trim(), parts[1].trim())
        } else {
            // If no separator found, use the whole filename as title
            Pair(fallbackArtist, filename.replace(".mp3", "").trim())
        }
    }
}
