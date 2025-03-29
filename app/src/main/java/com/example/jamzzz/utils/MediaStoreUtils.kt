package com.example.jamzzz.utils

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log

/**
 * Utility functions for working with MediaStore
 */
object MediaStoreUtils {
    private const val TAG = "MediaStoreUtils"

    /**
     * Get album art URI for a specific audio file
     * @param context Application context
     * @param audioId ID of the audio file
     * @return URI of the album art or null if not found
     */
    fun getAlbumArtUri(context: Context, audioId: Long): Uri? {
        try {
            // Query for the album ID associated with this audio file
            val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(MediaStore.Audio.Media.ALBUM_ID)
            val selection = MediaStore.Audio.Media._ID + "=?"
            val selectionArgs = arrayOf(audioId.toString())
            
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val albumId = cursor.getLong(0)
                    
                    // Get the album art URI using the album ID
                    return ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting album art: ${e.message}")
        }
        
        return null
    }
}
