package com.example.musicplayerdeck.viewmodel

import android.Manifest
import android.app.Application
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.example.musicplayerdeck.data.model.Song
import com.example.musicplayerdeck.data.repository.SavedPlaybackState
import com.example.musicplayerdeck.data.repository.fetchSongs
import com.example.musicplayerdeck.data.repository.loadSavedPlaybackState
import com.example.musicplayerdeck.data.repository.recordPlay
import com.example.musicplayerdeck.data.repository.savePlaybackState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicPlayerViewModel(app: Application) : AndroidViewModel(app) {
    private var controller: MediaController? = null
    private var playerListener: Player.Listener? = null
    private val pendingActions = mutableListOf<(MediaController) -> Unit>()
    private var currentPrefs: SharedPreferences? = null

    // Restoration deferred until the songs list is populated.
    private var pendingRestoration: SavedPlaybackState? = null

    var songs by mutableStateOf<ImmutableList<Song>>(persistentListOf())
        private set
    var isSongsLoading by mutableStateOf(false)
        private set

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
    var isRepeatOne by mutableStateOf(false)
        private set

    fun initialize(prefs: SharedPreferences) {
        currentPrefs = prefs
        isShuffleEnabled = prefs.getBoolean("shuffle_enabled", false)
        // Start loading songs immediately (before setContent) if permission already granted
        if (prefs.getBoolean("songs_loaded", false) && hasAudioPermission()) {
            loadSongs(prefs)
        }
        var lastPositionSaveTime = 0L
        viewModelScope.launch {
            while (true) {
                if (isPlaying) {
                    playbackPosition = controller?.currentPosition ?: 0L
                    // Persist position roughly every 10 s while playing so that
                    // a sudden process death loses at most ~10 s of progress.
                    val now = System.currentTimeMillis()
                    if (now - lastPositionSaveTime >= 10_000L) {
                        persistPlaybackState()
                        lastPositionSaveTime = now
                    }
                }
                delay(if (isPlaying) 250 else 1000)
            }
        }
    }

    fun loadSongs(prefs: SharedPreferences) {
        if (isSongsLoading) return
        isSongsLoading = true
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { fetchSongs(getApplication()) }
            songs = loaded
            prefs.edit { putBoolean("songs_loaded", true) }
            isSongsLoading = false

            // Apply any state restoration that was deferred because songs weren't
            // loaded yet when the controller first connected.
            val pending = pendingRestoration
            val ctrl = controller
            if (pending != null && ctrl != null) {
                pendingRestoration = null
                applyRestoredState(ctrl, pending)
            }
        }
    }

    private fun hasAudioPermission(): Boolean {
        val ctx = getApplication<Application>()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_MEDIA_AUDIO) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
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

        // Resync current song in case the track auto-advanced while the
        // controller was disconnected (e.g. screen was off).
        val mcMediaId = mc.currentMediaItem?.mediaId
        if (mcMediaId != null && currentSong?.id?.toString() != mcMediaId) {
            currentSong = activePlaybackQueue.find { it.id.toString() == mcMediaId }
                ?: songs.find { it.id.toString() == mcMediaId }
        }
        if (currentSong != null) {
            playbackPosition = mc.currentPosition
        }

        when {
            // Player is empty → fresh start after process death. Restore from saved state.
            mc.mediaItemCount == 0 -> {
                val prefs = currentPrefs
                if (prefs != null) {
                    val saved = loadSavedPlaybackState(prefs)
                    if (saved != null) {
                        if (songs.isNotEmpty()) {
                            applyRestoredState(mc, saved)
                        } else {
                            // Songs load is still in flight; apply once it finishes.
                            pendingRestoration = saved
                        }
                    }
                }
            }
            // Player has items (e.g. resumed via Bluetooth) but ViewModel lost its
            // queue (process was recreated). Rebuild the queue from the player.
            activePlaybackQueue.isEmpty() && songs.isNotEmpty() -> {
                val rebuilt = (0 until mc.mediaItemCount).mapNotNull { i ->
                    val id = mc.getMediaItemAt(i).mediaId.toLongOrNull() ?: return@mapNotNull null
                    songs.find { it.id == id }
                }.toImmutableList()
                if (rebuilt.isNotEmpty()) {
                    activePlaybackQueue = rebuilt
                    originalPlaylist = rebuilt
                }
            }
        }

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
                // Save after every track transition so the correct song is recorded
                // even if the process dies immediately after auto-advance.
                persistPlaybackState(positionMs = 0L)
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                // Save on pause so the exact stop position survives a process kill.
                if (!playing) persistPlaybackState()
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
        // Save immediately so the new queue is persisted even if the process dies
        // before the first onMediaItemTransition callback fires.
        val prefs = currentPrefs
        if (prefs != null && activePlaybackQueue.isNotEmpty()) {
            savePlaybackState(
                prefs = prefs,
                queueIds = activePlaybackQueue.map { it.id },
                queueIndex = if (idx >= 0) idx else 0,
                positionMs = 0L,
                songId = song.id,
            )
        }
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

    fun toggleRepeatOne() {
        isRepeatOne = !isRepeatOne
        exec { c -> c.repeatMode = if (isRepeatOne) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_ALL }
    }

    fun skipToQueueIndex(index: Int) {
        exec { c ->
            c.seekTo(index, 0)
            c.prepare()
            c.play()
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

    // Persist current playback state to SharedPreferences. Called on pause,
    // track transition, every 10 s during playback, and when a new queue starts.
    private fun persistPlaybackState(
        positionMs: Long = controller?.currentPosition ?: playbackPosition,
    ) {
        val prefs = currentPrefs ?: return
        val song = currentSong ?: return
        val queue = activePlaybackQueue
        if (queue.isEmpty()) return
        val idx = queue.indexOfFirst { it.id == song.id }
        savePlaybackState(
            prefs = prefs,
            queueIds = queue.map { it.id },
            queueIndex = if (idx >= 0) idx else 0,
            positionMs = positionMs,
            songId = song.id,
        )
    }

    // Populate the player and ViewModel with previously saved state.
    // Does NOT call play() — the user taps play to resume.
    private fun applyRestoredState(mc: MediaController, saved: SavedPlaybackState) {
        val queue = saved.queueIds
            .mapNotNull { id -> songs.find { it.id == id } }
            .toImmutableList()
        if (queue.isEmpty()) return

        activePlaybackQueue = queue
        originalPlaylist = queue
        val idx = saved.queueIndex.coerceIn(0, queue.size - 1)
        currentSong = queue.getOrNull(idx) ?: queue.first()
        playbackPosition = saved.positionMs

        mc.setMediaItems(buildItems(queue))
        mc.seekTo(idx, saved.positionMs)
        mc.prepare()
        // Intentionally no mc.play() — restored state is shown paused.
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
