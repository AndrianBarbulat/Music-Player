package com.example.musicplayerdeck.service

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val aa = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(aa, true)
            .build()
            .apply { repeatMode = Player.REPEAT_MODE_ALL }
        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return session
    }

    // Keep the service as a foreground service whenever media is loaded, not just
    // when actively playing. Without this, Media3 drops the foreground notification
    // on pause, and Android kills the process when the screen turns off.
    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        val hasMedia = session.player.currentMediaItem != null
        super.onUpdateNotification(session, startInForegroundRequired || hasMedia)
    }

    // Don't stop the service when the user swipes the app away from recents while
    // a song is loaded. The notification gives explicit control to stop playback.
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (session?.player?.currentMediaItem == null) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.player?.release()
        session?.release()
        super.onDestroy()
    }
}