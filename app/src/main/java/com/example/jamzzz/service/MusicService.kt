package com.example.jamzzz.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.jamzzz.PlayerUI
import com.example.jamzzz.R

/**
 * Service for handling music playback in the background.
 * This allows music to continue playing when the app is minimized or swiped away.
 */
class MusicService : Service() {
    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "JamzzzMusicChannel"
        private const val ACTION_PLAY = "com.example.jamzzz.ACTION_PLAY"
        private const val ACTION_PAUSE = "com.example.jamzzz.ACTION_PAUSE"
        private const val ACTION_PREVIOUS = "com.example.jamzzz.ACTION_PREVIOUS"
        private const val ACTION_NEXT = "com.example.jamzzz.ACTION_NEXT"
    }

    private val binder = MusicBinder()
    private lateinit var exoPlayer: ExoPlayer
    private var currentTrackTitle: String = "Unknown"
    private var currentArtist: String = "Unknown"
    private lateinit var mediaSession: MediaSessionCompat

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initMediaSession()
    }

    private fun initMediaSession() {
        // Create a media session callback to handle media button events
        val mediaSessionCallback = object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                if (::exoPlayer.isInitialized) {
                    println("DEBUG: MediaSession onPlay called")
                    exoPlayer.play()
                    updatePlaybackState(true)
                    updateNotification()
                }
            }
            
            override fun onPause() {
                if (::exoPlayer.isInitialized) {
                    println("DEBUG: MediaSession onPause called")
                    exoPlayer.pause()
                    updatePlaybackState(false)
                    updateNotification()
                }
            }
            
            override fun onSkipToNext() {
                if (::exoPlayer.isInitialized && exoPlayer.hasNextMediaItem()) {
                    println("DEBUG: MediaSession onSkipToNext called")
                    exoPlayer.seekToNext()
                    updateCurrentTrackInfo()
                }
            }
            
            override fun onSkipToPrevious() {
                if (::exoPlayer.isInitialized && exoPlayer.hasPreviousMediaItem()) {
                    println("DEBUG: MediaSession onSkipToPrevious called")
                    exoPlayer.seekToPrevious()
                    updateCurrentTrackInfo()
                }
            }
            
            // Handle media button events directly
            override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
                println("DEBUG: MediaSession onMediaButtonEvent called: ${mediaButtonEvent.action}")
                return super.onMediaButtonEvent(mediaButtonEvent)
            }
        }
        
        // Create a pending intent for the activity
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, PlayerUI::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Initialize the media session
        mediaSession = MediaSessionCompat(this, "JamzzzMediaSession").apply {
            setSessionActivity(sessionActivityPendingIntent)
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(
                        PlaybackStateCompat.STATE_PAUSED,
                        0,
                        1.0f
                    )
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                    )
                    .build()
            )
            // Set the callback to handle media button events
            setCallback(mediaSessionCallback)
            // Make the media session active
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                ACTION_PLAY -> {
                    if (::exoPlayer.isInitialized) {
                        println("DEBUG: Notification PLAY action received")
                        exoPlayer.play()
                        updatePlaybackState(true)
                        // Update notification to show pause button
                        updateNotification()
                    }
                }
                ACTION_PAUSE -> {
                    if (::exoPlayer.isInitialized) {
                        println("DEBUG: Notification PAUSE action received")
                        exoPlayer.pause()
                        updatePlaybackState(false)
                        // Update notification to show play button
                        updateNotification()
                    }
                }
                ACTION_PREVIOUS -> {
                    if (::exoPlayer.isInitialized && exoPlayer.hasPreviousMediaItem()) {
                        println("DEBUG: Notification PREVIOUS action received")
                        exoPlayer.seekToPrevious()
                        // Update track info in notification if track changed
                        updateCurrentTrackInfo()
                    }
                }
                ACTION_NEXT -> {
                    if (::exoPlayer.isInitialized && exoPlayer.hasNextMediaItem()) {
                        println("DEBUG: Notification NEXT action received")
                        exoPlayer.seekToNext()
                        // Update track info in notification if track changed
                        updateCurrentTrackInfo()
                    }
                }
            }
        }
        
        // If we're not playing music and no action was specified, don't keep the service running
        if (intent?.action == null && ::exoPlayer.isInitialized && !exoPlayer.isPlaying) {
            stopForeground(true)
            return START_NOT_STICKY
        }
        
        // Return START_STICKY to ensure the service restarts if it's killed
        return START_STICKY
    }

    private fun updatePlaybackState(isPlaying: Boolean) {
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(
                    state,
                    exoPlayer.currentPosition,
                    1.0f
                )
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
                .build()
        )
        
        // Update notification to reflect current playback state
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    fun setPlayer(player: ExoPlayer) {
        this.exoPlayer = player
        
        // Add a listener to update the notification when playback state changes
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                super.onPlaybackStateChanged(playbackState)
                try {
                    updatePlaybackState(player.isPlaying)
                    // Update notification when playback state changes
                    updateNotification()
                } catch (e: Exception) {
                    // Log error but don't crash
                    println("ERROR: Failed to update playback state: ${e.message}")
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)
                try {
                    updatePlaybackState(isPlaying)
                    // Update notification when playing state changes
                    updateNotification()
                } catch (e: Exception) {
                    // Log error but don't crash
                    println("ERROR: Failed to update playback state: ${e.message}")
                }
            }
            
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                // When the track changes, update the notification
                try {
                    // The PlayerUI should update the track info, but we'll update the notification anyway
                    updateNotification()
                } catch (e: Exception) {
                    println("ERROR: Failed to handle media item transition: ${e.message}")
                }
            }
        })
        
        try {
            // Create notification and start foreground service
            val notification = createNotification()
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            // If notification creation fails, use a simple notification instead
            println("ERROR: Failed to create media notification: ${e.message}")
            val fallbackNotification = createFallbackNotification()
            startForeground(NOTIFICATION_ID, fallbackNotification)
        }
    }

    fun updateTrackInfo(title: String, artist: String) {
        currentTrackTitle = title
        currentArtist = artist
        
        // Update the notification with new track info
        updateNotification()
    }
    
    /**
     * Updates the notification with current playback state and track info
     */
    private fun updateNotification() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }
    
    /**
     * Updates the current track info from the ExoPlayer's current media item
     */
    private fun updateCurrentTrackInfo() {
        if (::exoPlayer.isInitialized && exoPlayer.currentMediaItem != null) {
            // Try to extract title and artist from the media ID
            try {
                val mediaId = exoPlayer.currentMediaItem?.mediaId
                // If we have a valid media ID, we can update the track info
                // This assumes the PlayerUI has set the mediaId to contain track info
                // For now, we'll just update the notification with the current values
                updateNotification()
            } catch (e: Exception) {
                println("ERROR: Failed to update track info: ${e.message}")
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Jamzzz Music Player",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Channel for Jamzzz music playback"
                lightColor = Color.BLUE
                setShowBadge(true)
            }

            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        // Create an intent to launch the app when the notification is tapped
        val contentIntent = Intent(this, PlayerUI::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create media session-based pending intents for media controls using MediaButtonReceiver
        // This is the recommended way to create notification actions that work with MediaSession
        
        // Play/Pause action
        val playPauseIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
        val playPauseKeyEvent = if (::exoPlayer.isInitialized && exoPlayer.isPlaying) {
            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE)
        } else {
            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)
        }
        playPauseIntent.putExtra(Intent.EXTRA_KEY_EVENT, playPauseKeyEvent)
        val playPausePendingIntent = PendingIntent.getBroadcast(
            this, 1, playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Previous action
        val previousIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
        val previousKeyEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        previousIntent.putExtra(Intent.EXTRA_KEY_EVENT, previousKeyEvent)
        val previousPendingIntent = PendingIntent.getBroadcast(
            this, 2, previousIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Next action
        val nextIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
        val nextKeyEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT)
        nextIntent.putExtra(Intent.EXTRA_KEY_EVENT, nextKeyEvent)
        val nextPendingIntent = PendingIntent.getBroadcast(
            this, 3, nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Get the album art or use a default icon
        val largeIcon: Bitmap? = try {
            BitmapFactory.decodeResource(resources, R.drawable.ic_music_note)
        } catch (e: Exception) {
            null
        }

        // Create the notification
        val isPlaying = ::exoPlayer.isInitialized && exoPlayer.isPlaying
        val playPauseIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play

        // Create a media style with the media session token
        val mediaStyle = MediaStyle()
            .setMediaSession(mediaSession.sessionToken)
            .setShowActionsInCompactView(0, 1, 2) // Show previous, play/pause, next in compact view

        // Create notification builder with basic info
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentTrackTitle)
            .setContentText(currentArtist)
            .setContentIntent(contentPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setStyle(mediaStyle)
            .setOngoing(true)
            
        // Try to set the small icon
        try {
            builder.setSmallIcon(R.drawable.ic_music_note)
        } catch (e: Exception) {
            // If the drawable is not available, use a default system icon
            builder.setSmallIcon(android.R.drawable.ic_media_play)
        }
        
        // Set the large icon if available
        if (largeIcon != null) {
            builder.setLargeIcon(largeIcon)
        }
        
        // Try to add media control actions
        try {
            builder.addAction(R.drawable.ic_previous, "Previous", previousPendingIntent)
            builder.addAction(playPauseIcon, if (isPlaying) "Pause" else "Play", playPausePendingIntent)
            builder.addAction(R.drawable.ic_next, "Next", nextPendingIntent)
        } catch (e: Exception) {
            // If the drawable resources are not available, add actions without icons
            builder.addAction(0, "Previous", previousPendingIntent)
            builder.addAction(0, if (isPlaying) "Pause" else "Play", playPausePendingIntent)
            builder.addAction(0, "Next", nextPendingIntent)
        }
        
        return builder.build()
    }

    // Create a simple fallback notification when the media notification fails
    private fun createFallbackNotification(): Notification {
        val contentIntent = Intent(this, PlayerUI::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Jamzzz Music Player")
            .setContentText("Music playback")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(contentPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .build()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (::mediaSession.isInitialized) {
            mediaSession.release()
        }
        if (::exoPlayer.isInitialized) {
            exoPlayer.release()
        }
    }
    
    /**
     * Called when the app is swiped away from recent apps.
     * We want to completely stop the service and music playback in this case.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        println("DEBUG: App swiped away from recents, stopping service and playback")
        
        // Stop playback if it's playing
        if (::exoPlayer.isInitialized && exoPlayer.isPlaying) {
            exoPlayer.stop()
        }
        
        // Stop the service completely
        stopSelf()
    }
}
