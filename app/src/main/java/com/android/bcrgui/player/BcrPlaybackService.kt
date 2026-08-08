package com.android.bcrgui.player

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.session.MediaSession
import android.os.Build
import android.os.IBinder

class BcrPlaybackService : Service() {

    private var currentTitle = ""
    private var currentArtist = ""
    private var isPlaying = false
    private var sessionToken: MediaSession.Token? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Fetch session token directly from player singleton
        sessionToken = BcrAudioPlayer.instance?.mediaSession?.sessionToken
        // Start foreground immediately inside onCreate to strictly satisfy OS start times and prevent crashes
        val placeholder = buildNotification("Call Recording", "", false, sessionToken)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, placeholder, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, placeholder)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Ensure token is fetched
        if (sessionToken == null) {
            sessionToken = BcrAudioPlayer.instance?.mediaSession?.sessionToken
        }

        when (intent?.action) {
            ACTION_START -> {
                currentTitle = intent.getStringExtra(EXTRA_TITLE) ?: "Call Recording"
                currentArtist = intent.getStringExtra(EXTRA_ARTIST) ?: ""
                isPlaying = true

                val notification = buildNotification(currentTitle, currentArtist, isPlaying, sessionToken)
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, notification)
            }
            ACTION_UPDATE_STATE -> {
                isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false)
                val notification = buildNotification(currentTitle, currentArtist, isPlaying, sessionToken)
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, notification)
            }
            ACTION_STOP -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
            ACTION_PLAY_PAUSE -> {
                BcrAudioPlayer.instance?.let { player ->
                    if (player.isPlaying.value) {
                        player.pause()
                    } else {
                        player.resume()
                    }
                }
            }
            ACTION_SKIP_FORWARD -> {
                BcrAudioPlayer.instance?.let { player ->
                    val newPos = (player.currentPosition.value + 10000).coerceAtMost(player.duration.value)
                    player.seekTo(newPos)
                }
            }
            ACTION_SKIP_BACKWARD -> {
                BcrAudioPlayer.instance?.let { player ->
                    val newPos = (player.currentPosition.value - 10000).coerceAtLeast(0)
                    player.seekTo(newPos)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Playback Controls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows playback notification controls for call recordings"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    @Suppress("DEPRECATION")
    private fun buildNotification(
        title: String,
        artist: String,
        isPlaying: Boolean,
        token: MediaSession.Token?
    ): Notification {
        val playPauseIntent = Intent(this, BcrPlaybackService::class.java).apply { action = ACTION_PLAY_PAUSE }
        val playPausePending = PendingIntent.getService(
            this, 1, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val skipForwardIntent = Intent(this, BcrPlaybackService::class.java).apply { action = ACTION_SKIP_FORWARD }
        val skipForwardPending = PendingIntent.getService(
            this, 2, skipForwardIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val skipBackwardIntent = Intent(this, BcrPlaybackService::class.java).apply { action = ACTION_SKIP_BACKWARD }
        val skipBackwardPending = PendingIntent.getService(
            this, 3, skipBackwardIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openActivityIntent = Intent(this, Class.forName("com.android.bcrgui.MainActivity")).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openActivityPending = PendingIntent.getActivity(
            this, 0, openActivityIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mediaStyle = android.app.Notification.MediaStyle()
        if (token != null) {
            mediaStyle.setMediaSession(token)
        }
        mediaStyle.setShowActionsInCompactView(0, 1, 2)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setStyle(mediaStyle)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(openActivityPending)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, "Rewind", skipBackwardPending)
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                playPausePending
            )
            .addAction(android.R.drawable.ic_media_next, "Forward", skipForwardPending)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "bcr_player_channel"
        const val NOTIFICATION_ID = 2026

        const val ACTION_START = "com.android.bcrgui.action.START"
        const val ACTION_STOP = "com.android.bcrgui.action.STOP"
        const val ACTION_UPDATE_STATE = "com.android.bcrgui.action.UPDATE_STATE"

        const val ACTION_PLAY_PAUSE = "com.android.bcrgui.action.PLAY_PAUSE"
        const val ACTION_SKIP_FORWARD = "com.android.bcrgui.action.SKIP_FORWARD"
        const val ACTION_SKIP_BACKWARD = "com.android.bcrgui.action.SKIP_BACKWARD"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"
        const val EXTRA_TOKEN = "extra_token"
        const val EXTRA_IS_PLAYING = "extra_is_playing"
    }
}
