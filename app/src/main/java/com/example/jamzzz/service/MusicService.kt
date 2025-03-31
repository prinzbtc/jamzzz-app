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
        private const val PROGRESS_UPDATE_INTERVAL = 1000L // Update progress every second
    }

    private val binder = MusicBinder()
    private lateinit var exoPlayer: ExoPlayer
    private var currentTrackTitle: String = "Unknown"
    private var currentArtist: String = "Unknown"
    private lateinit var mediaSession: MediaSessionCompat
    
    // For progress updates
    private var progressUpdateHandler: android.os.Handler? = null
    private var progressUpdateRunnable: Runnable? = null
    private var currentProgress: Long = 0
    private var totalDuration: Long = 0

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
        
        // Initialize the handler for progress updates
        progressUpdateHandler = android.os.Handler(android.os.Looper.getMainLooper())
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

    // Track the last playback state update time to prevent too frequent updates
    private var lastPlaybackStateUpdateTime = 0L
    private val MIN_PLAYBACK_UPDATE_INTERVAL = 300L // Minimum time between playback state updates in ms
    
    private fun updatePlaybackState(isPlaying: Boolean) {
        try {
            val currentTime = System.currentTimeMillis()
            
            // Skip updates that are too close together (debouncing)
            if (currentTime - lastPlaybackStateUpdateTime < MIN_PLAYBACK_UPDATE_INTERVAL) {
                return
            }
            
            lastPlaybackStateUpdateTime = currentTime
            
            // Update current progress
            if (::exoPlayer.isInitialized) {
                currentProgress = exoPlayer.currentPosition
                if (totalDuration <= 0 && exoPlayer.duration > 0) {
                    totalDuration = exoPlayer.duration
                }
            }
            
            // Update playback state in a try-catch block to prevent crashes
            try {
                val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
                mediaSession.setPlaybackState(
                    PlaybackStateCompat.Builder()
                        .setState(
                            state,
                            currentProgress,
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
            } catch (e: Exception) {
                println("ERROR: Failed to update media session state: ${e.message}")
            }
            
            // Use the optimized updateNotification method which already handles threading
            updateNotification()
        } catch (e: Exception) {
            println("ERROR: Exception in updatePlaybackState: ${e.message}")
        }
    }

    fun setPlayer(player: ExoPlayer) {
        this.exoPlayer = player
        
        // Add a listener to update the notification when playback state changes
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                super.onPlaybackStateChanged(playbackState)
                try {
                    // Update duration when playback state changes
                    if (playbackState == Player.STATE_READY) {
                        totalDuration = player.duration
                    }
                    
                    updatePlaybackState(player.isPlaying)
                    // Update notification when playback state changes
                    updateNotification()
                    
                    // Start or stop progress updates based on playback state
                    if (player.isPlaying) {
                        startProgressUpdates()
                    } else {
                        stopProgressUpdates()
                    }
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
                    
                    // Start or stop progress updates based on playing state
                    if (isPlaying) {
                        startProgressUpdates()
                    } else {
                        stopProgressUpdates()
                    }
                } catch (e: Exception) {
                    // Log error but don't crash
                    println("ERROR: Failed to update playback state: ${e.message}")
                }
            }
            
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                // When the track changes, update the notification
                try {
                    // Reset duration for the new track
                    totalDuration = player.duration
                    // The PlayerUI should update the track info, but we'll update the notification anyway
                    updateNotification()
                } catch (e: Exception) {
                    println("ERROR: Failed to handle media item transition: ${e.message}")
                }
            }
            
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                super.onPositionDiscontinuity(oldPosition, newPosition, reason)
                // Update current progress when position changes abruptly (e.g., seeking)
                currentProgress = player.currentPosition
                updateNotification()
            }
        })
        
        try {
            // Initialize duration
            if (player.duration > 0) {
                totalDuration = player.duration
            }
            
            // Create notification and start foreground service
            val notification = createNotification()
            startForeground(NOTIFICATION_ID, notification)
            
            // Start progress updates if already playing
            if (player.isPlaying) {
                startProgressUpdates()
            }
        } catch (e: Exception) {
            // If notification creation fails, use a simple notification instead
            println("ERROR: Failed to create media notification: ${e.message}")
            val fallbackNotification = createFallbackNotification()
            startForeground(NOTIFICATION_ID, fallbackNotification)
        }
    }

    fun updateTrackInfo(title: String, artist: String) {
        println("DEBUG: MusicService.updateTrackInfo called with title=$title, artist=$artist")
        currentTrackTitle = title
        currentArtist = artist
        
        // Update the notification with new track info
        updateNotification()
    }
    
    /**
     * Force an immediate notification update without debouncing
     * This should be called when track information changes to ensure
     * the notification is updated right away
     */
    fun updateNotificationNow() {
        println("DEBUG: Forcing immediate notification update")
        // Create and update notification on a background thread without debouncing
        Thread {
            try {
                // Get current position from ExoPlayer if initialized
                if (::exoPlayer.isInitialized) {
                    currentProgress = exoPlayer.currentPosition
                    if (totalDuration <= 0 && exoPlayer.duration > 0) {
                        totalDuration = exoPlayer.duration
                    }
                }
                
                // Create a new notification
                val notification = createNotification()
                
                // Update the notification
                val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, notification)
                
                // Also update the foreground service notification
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification)
                }
                
                println("DEBUG: Notification updated with title=$currentTrackTitle, artist=$currentArtist, progress=$currentProgress, duration=$totalDuration")
            } catch (e: Exception) {
                println("ERROR: Failed to update notification immediately: ${e.message}")
                e.printStackTrace()
            }
        }.start()
    }
    
    // Track the last notification update time to prevent too frequent updates
    private var lastNotificationUpdateTime = 0L
    private val MIN_UPDATE_INTERVAL = 500L // Minimum time between notification updates in ms
    
    /**
     * Updates the notification with current playback state and track info
     * Uses debouncing to prevent too frequent updates which can cause ANRs
     */
    private fun updateNotification() {
        val currentTime = System.currentTimeMillis()
        
        // Skip updates that are too close together (debouncing)
        if (currentTime - lastNotificationUpdateTime < MIN_UPDATE_INTERVAL) {
            return
        }
        
        lastNotificationUpdateTime = currentTime
        
        // Move notification creation and update to a background thread
        Thread {
            try {
                // Get current position from ExoPlayer if initialized
                if (::exoPlayer.isInitialized) {
                    currentProgress = exoPlayer.currentPosition
                    if (totalDuration <= 0 && exoPlayer.duration > 0) {
                        totalDuration = exoPlayer.duration
                    }
                }
                
                val notification = createNotification()
                val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, notification)
            } catch (e: Exception) {
                println("ERROR: Failed to update notification: ${e.message}")
            }
        }.start()
    }
    
    /**
     * Starts periodic progress updates for the notification
     */
    private fun startProgressUpdates() {
        // Stop any existing updates first
        stopProgressUpdates()
        
        // Create a new runnable for progress updates
        progressUpdateRunnable = Runnable {
            // Update notification with current progress
            updateNotification()
            
            // Schedule the next update
            progressUpdateHandler?.postDelayed(progressUpdateRunnable!!, PROGRESS_UPDATE_INTERVAL)
        }
        
        // Start the updates
        progressUpdateHandler?.post(progressUpdateRunnable!!)
    }
    
    /**
     * Stops periodic progress updates
     */
    private fun stopProgressUpdates() {
        progressUpdateRunnable?.let { runnable ->
            progressUpdateHandler?.removeCallbacks(runnable)
        }
        progressUpdateRunnable = null
    }
    
    /**
     * Formats duration in milliseconds to MM:SS format
     */
    private fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0) return "0:00"
        
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        
        return String.format("%d:%02d", minutes, seconds)
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
                enableVibration(false) // No vibration for media notifications
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC // Show on lock screen
            }

            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            println("DEBUG: Created notification channel $CHANNEL_ID")
        }
    }

    private fun createNotification(): Notification {
        println("DEBUG: Creating notification with title=$currentTrackTitle, artist=$currentArtist, progress=$currentProgress, duration=$totalDuration")
        
        // Create direct action intents instead of media button intents
        // Play/Pause action
        val playPauseIntent = Intent(this, MusicService::class.java).apply {
            action = if (::exoPlayer.isInitialized && exoPlayer.isPlaying) ACTION_PAUSE else ACTION_PLAY
        }
        val playPausePendingIntent = PendingIntent.getService(
            this, 1, playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Previous action
        val previousIntent = Intent(this, MusicService::class.java).apply {
            action = ACTION_PREVIOUS
        }
        val previousPendingIntent = PendingIntent.getService(
            this, 2, previousIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Next action
        val nextIntent = Intent(this, MusicService::class.java).apply {
            action = ACTION_NEXT
        }
        val nextPendingIntent = PendingIntent.getService(
            this, 3, nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Create an intent to launch the app when the notification is tapped
        val contentIntent = Intent(this, PlayerUI::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
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

        // Format the progress and duration for the subtitle
        val progressText = formatDuration(currentProgress)
        val durationText = formatDuration(totalDuration)
        val progressInfo = if (totalDuration > 0) "$progressText / $durationText" else ""
        
        // Create a media style with the media session token
        val mediaStyle = MediaStyle()
            .setMediaSession(mediaSession.sessionToken)
            .setShowActionsInCompactView(0, 1, 2) // Show previous, play/pause, next in compact view
        
        // Create notification builder with basic info
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            // Force the title to be visible by setting it directly
            .setContentTitle(currentTrackTitle)
            .setContentText(currentArtist)
            .setSubText(progressInfo) // Show progress as subtext
            .setContentIntent(contentPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setStyle(mediaStyle)
            .setOngoing(true)
            .setShowWhen(false) // Don't show the time the notification was posted
            
        // Add progress bar if we have valid duration
        if (totalDuration > 0) {
            builder.setProgress(totalDuration.toInt(), currentProgress.toInt(), false)
        }
            
        // Set the small icon - use a system icon that's guaranteed to exist
        builder.setSmallIcon(android.R.drawable.ic_media_play)
        
        // Set the large icon if available
        if (largeIcon != null) {
            builder.setLargeIcon(largeIcon)
        }
        
        // Add media control actions using system icons that are guaranteed to exist
        builder.addAction(android.R.drawable.ic_media_previous, "Previous", previousPendingIntent)
        builder.addAction(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, 
                         if (isPlaying) "Pause" else "Play", 
                         playPausePendingIntent)
        builder.addAction(android.R.drawable.ic_media_next, "Next", nextPendingIntent)
        
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
        // Stop progress updates
        stopProgressUpdates()
        
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
        
        // Stop progress updates
        stopProgressUpdates()
        
        // Stop playback if it's playing
        if (::exoPlayer.isInitialized && exoPlayer.isPlaying) {
            exoPlayer.stop()
        }
        
        // Stop the service completely
        stopSelf()
    }
}
