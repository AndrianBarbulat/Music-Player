package com.example.musicplayerdeck.service

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.MediaSessionService
import com.example.musicplayerdeck.data.repository.loadSavedPlaybackState
import com.example.musicplayerdeck.data.repository.savePlaybackState
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

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
            .apply {
                repeatMode = Player.REPEAT_MODE_ALL
                // Hold a partial wake lock so the CPU doesn't sleep mid-playback.
                setWakeMode(C.WAKE_MODE_LOCAL)
            }
        session = MediaSession.Builder(this, player)
            .setCallback(PlaybackCallback())
            .build()
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
        // Last-chance save: capture the exact current position before the process dies.
        // This runs even when Android kills the process via OOM, giving the most
        // accurate position to restore from on next launch.
        val player = session?.player
        if (player != null && player.currentMediaItem != null) {
            val count = player.mediaItemCount
            val ids = (0 until count).mapNotNull {
                player.getMediaItemAt(it).mediaId.toLongOrNull()
            }
            if (ids.isNotEmpty()) {
                val prefs = getSharedPreferences("MusicPlayerDeckPrefs", Context.MODE_PRIVATE)
                savePlaybackState(
                    prefs = prefs,
                    queueIds = ids,
                    queueIndex = player.currentMediaItemIndex,
                    positionMs = player.currentPosition,
                    songId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: -1L,
                )
            }
        }
        session?.player?.release()
        session?.release()
        super.onDestroy()
    }

    @UnstableApi
    private inner class PlaybackCallback : MediaSession.Callback {
        // Called by the system when a media button (Bluetooth, lock screen) triggers
        // playback resumption after the service was killed. We rebuild the queue from
        // saved state so ExoPlayer has something to play.
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaItemsWithStartPosition> {
            val prefs = getSharedPreferences("MusicPlayerDeckPrefs", Context.MODE_PRIVATE)
            val saved = loadSavedPlaybackState(prefs)
                ?: return Futures.immediateFailedFuture(
                    UnsupportedOperationException("No saved playback state")
                )
            val items = saved.queueIds.map { id ->
                val uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                )
                MediaItem.Builder()
                    .setUri(uri)
                    .setMediaId(id.toString())
                    .build()
            }
            val startIndex = saved.queueIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
            return Futures.immediateFuture(
                MediaItemsWithStartPosition(items, startIndex, saved.positionMs)
            )
        }
    }
}
