package com.example.musicplayerdeck.viewmodel

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.example.musicplayerdeck.data.model.Song
import com.example.musicplayerdeck.data.repository.recordPlay
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MusicPlayerViewModel : ViewModel() {
    private var controller: MediaController? = null
    private var playerListener: Player.Listener? = null
    private val pendingActions = mutableListOf<(MediaController) -> Unit>()
    private var currentPrefs: SharedPreferences? = null

    var currentSong by mutableStateOf<Song?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var playbackPosition by mutableLongStateOf(0L)
        private set
    var activePlaybackQueue by mutableStateOf<ImmutableList<Song>>(persistentListOf())
        private set
    private var originalPlaylist by mutableStateOf<ImmutableList<Song>>(persistentListOf())
    var isShuffleEnabled by mutableStateOf(false)
        private set
    private var shuffleOrder by mutableStateOf<ImmutableList<Int>>(persistentListOf())
    var shufflePosition by mutableIntStateOf(0)
        private set

    fun initialize(prefs: SharedPreferences) {
        currentPrefs = prefs
        isShuffleEnabled = prefs.getBoolean("shuffle_enabled", false)
        viewModelScope.launch {
            while (true) {
                if (isPlaying) {
                    playbackPosition = controller?.currentPosition ?: 0L
                }
                delay(if (isPlaying) 250 else 1000)
            }
        }
    }

    fun setController(mc: MediaController) {
        val oldListener = playerListener
        val oldController = controller
        if (oldListener != null && oldController != null) {
            oldController.removeListener(oldListener)
        }

        controller = mc
        isPlaying = mc.isPlaying

        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val s = activePlaybackQueue.find { it.id.toString() == mediaItem?.mediaId }
                currentSong = s
                playbackPosition = 0L
                if (s != null) {
                    currentPrefs?.let { recordPlay(it, s.id) }
                }
                if (isShuffleEnabled && s != null) {
                    val idx = activePlaybackQueue.indexOfFirst { it.id == s.id }
                    if (idx != -1 && idx != shufflePosition) shufflePosition = idx
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            @androidx.annotation.OptIn(UnstableApi::class)
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (isShuffleEnabled && reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                    val newItem = newPosition.mediaItem
                    val s = activePlaybackQueue.find { it.id.toString() == newItem?.mediaId }
                    if (s != null) {
                        val idx = activePlaybackQueue.indexOfFirst { it.id == s.id }
                        if (idx != -1 && idx != shufflePosition) shufflePosition = idx
                    }
                }
            }
        }

        playerListener = listener
        mc.addListener(listener)
        pendingActions.forEach { it(mc) }
        pendingActions.clear()
    }

    private fun exec(action: (MediaController) -> Unit) {
        val ctrl = controller
        if (ctrl != null) {
            action(ctrl)
        } else {
            pendingActions.add(action)
        }
    }

    fun playSong(song: Song, playlist: ImmutableList<Song>) {
        if (playlist.isEmpty()) return
        val changed = originalPlaylist != playlist || activePlaybackQueue.isEmpty()
        if (changed) {
            originalPlaylist = playlist
            activePlaybackQueue = genQueue(playlist, song)
        }
        val idx = activePlaybackQueue.indexOfFirst { it.id == song.id }
        exec { c ->
            if (changed) c.setMediaItems(buildItems(activePlaybackQueue))
            if (idx != -1) {
                c.seekTo(idx, 0)
                c.prepare()
                c.play()
            }
        }
    }

    fun addToQueue(song: Song) {
        if (activePlaybackQueue.isEmpty()) return
        val curIdx = activePlaybackQueue.indexOfFirst { it.id == currentSong?.id }
        val insertIdx = if (curIdx >= 0) curIdx + 1 else activePlaybackQueue.size
        val ml = activePlaybackQueue.toMutableList()
        ml.add(insertIdx, song)
        activePlaybackQueue = ml.toImmutableList()
        exec { c ->
            c.addMediaItem(insertIdx, buildItem(song))
        }
    }

    fun toggleShuffleMode(prefs: SharedPreferences) {
        if (originalPlaylist.isEmpty()) {
            isShuffleEnabled = !isShuffleEnabled
            prefs.edit { putBoolean("shuffle_enabled", isShuffleEnabled) }
            return
        }
        val anchor = currentSong
        isShuffleEnabled = !isShuffleEnabled
        prefs.edit { putBoolean("shuffle_enabled", isShuffleEnabled) }

        if (isShuffleEnabled) {
            val ai = anchor?.let { originalPlaylist.indexOf(it) } ?: 0
            shuffleOrder = (listOf(ai) + (0 until originalPlaylist.size)
                .filter { it != ai }
                .shuffled())
                .toImmutableList()
            shufflePosition = 0
            activePlaybackQueue = shuffleOrder.map { originalPlaylist[it] }.toImmutableList()
        } else {
            activePlaybackQueue = originalPlaylist
        }

        val ni = activePlaybackQueue.indexOfFirst { it.id == anchor?.id }
        exec { c ->
            val wp = c.isPlaying
            val cp = c.currentPosition
            c.setMediaItems(buildItems(activePlaybackQueue))
            c.seekTo(if (ni >= 0) ni else 0, cp)
            c.prepare()
            if (wp) c.play()
        }
    }

    private fun genQueue(pl: ImmutableList<Song>, anchor: Song?): ImmutableList<Song> {
        if (!isShuffleEnabled) return pl
        if (pl.size <= 1) return pl
        val ai = anchor?.let { pl.indexOf(it) } ?: -1
        shuffleOrder = if (ai >= 0) {
            (listOf(ai) + (0 until pl.size).filter { it != ai }.shuffled()).toImmutableList()
        } else {
            (0 until pl.size).shuffled().toImmutableList()
        }
        shufflePosition = 0
        return shuffleOrder.map { pl[it] }.toImmutableList()
    }

    fun reshuffleQueue() {
        if (!isShuffleEnabled || originalPlaylist.isEmpty()) return
        val played = shuffleOrder.take(shufflePosition + 1)
        val rest = shuffleOrder.drop(shufflePosition + 1)
        if (rest.isNotEmpty()) {
            shuffleOrder = (played + rest.shuffled()).toImmutableList()
            activePlaybackQueue = shuffleOrder.map { originalPlaylist[it] }.toImmutableList()
            exec { c ->
                val wp = c.isPlaying
                val cp = c.currentPosition
                c.setMediaItems(buildItems(activePlaybackQueue))
                c.seekTo(shufflePosition, cp)
                c.prepare()
                if (wp) c.play()
            }
        }
    }

    fun togglePlayPause() {
        exec { c -> if (c.isPlaying) c.pause() else c.play() }
    }

    fun playNext() {
        exec { c ->
            if (c.hasNextMediaItem()) c.seekToNext()
            else {
                c.seekTo(0, 0)
                c.pause()
            }
        }
    }

    fun playPrevious() {
        exec { c -> c.seekToPrevious() }
    }

    fun seekTo(pos: Long) {
        playbackPosition = pos
        exec { c -> c.seekTo(pos) }
    }

    private fun buildItem(s: Song): MediaItem {
        return MediaItem.Builder()
            .setUri(s.uri)
            .setMediaId(s.id.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(s.title)
                    .setArtist(s.artist)
                    .setArtworkUri(s.albumArtUri)
                    .build()
            )
            .build()
    }

    private fun buildItems(q: ImmutableList<Song>): List<MediaItem> {
        return q.map { buildItem(it) }
    }

    override fun onCleared() {
        val listener = playerListener
        if (listener != null) {
            controller?.removeListener(listener)
        }
        playerListener = null
        controller = null
        pendingActions.clear()
    }
}