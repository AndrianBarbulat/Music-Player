@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.musicplayerdeck

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import com.example.musicplayerdeck.ui.theme.DarkMintGradient
import com.example.musicplayerdeck.ui.theme.MintGradient
import com.example.musicplayerdeck.ui.theme.MusicPlayerDeckTheme
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.collections.immutable.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

// ─────────────────────────────────────────────
// Data classes
// ─────────────────────────────────────────────

@Stable
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Int,
    val uri: Uri,
    val albumArtUri: Uri?,
    val folder: String
)

@Stable
data class Playlist(
    val name: String,
    val songIds: ImmutableList<Long>
)

data class MiniPlayerState(
    val position: Long,
    val isPlaying: Boolean,
    val song: Song?
)

// ─────────────────────────────────────────────
// Utility
// ─────────────────────────────────────────────

fun formatDuration(ms: Int): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}

fun formatDurationLong(ms: Long): String = formatDuration(ms.toInt())

// ─────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────

class MusicPlayerViewModel : ViewModel() {
    private var controller: MediaController? = null
    private var playerListener: Player.Listener? = null

    private val pendingActions = mutableListOf<(MediaController) -> Unit>()

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
        isShuffleEnabled = prefs.getBoolean("shuffle_enabled", false)
        viewModelScope.launch {
            while (true) {
                if (isPlaying) {
                    playbackPosition = controller?.currentPosition ?: 0L
                }
                // Poll slower when paused to save battery
                delay(if (isPlaying) 250 else 1000)
            }
        }
    }

    fun setController(mediaController: MediaController) {
        // Remove previous listener to prevent stacking duplicates
        val oldListener = playerListener
        val oldController = controller
        if (oldListener != null && oldController != null) {
            oldController.removeListener(oldListener)
        }

        controller = mediaController
        isPlaying = mediaController.isPlaying

        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val newSong = activePlaybackQueue.find { it.id.toString() == mediaItem?.mediaId }
                currentSong = newSong
                playbackPosition = 0L

                if (isShuffleEnabled && newSong != null) {
                    val newIndex = activePlaybackQueue.indexOfFirst { it.id == newSong.id }
                    if (newIndex != -1 && newIndex != shufflePosition) {
                        shufflePosition = newIndex
                    }
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
                    val newMediaItem = newPosition.mediaItem
                    val newSong =
                        activePlaybackQueue.find { it.id.toString() == newMediaItem?.mediaId }
                    newSong?.let {
                        val newIndex = activePlaybackQueue.indexOfFirst { it.id == newSong.id }
                        if (newIndex != -1 && newIndex != shufflePosition) {
                            shufflePosition = newIndex
                        }
                    }
                }
            }
        }

        playerListener = listener
        mediaController.addListener(listener)

        pendingActions.forEach { action -> action(mediaController) }
        pendingActions.clear()
    }

    private fun executeCommand(action: (MediaController) -> Unit) {
        val ctrl = controller
        if (ctrl != null) {
            action(ctrl)
        } else {
            pendingActions.add(action)
        }
    }

    fun playSong(song: Song, playlist: ImmutableList<Song>) {
        if (playlist.isEmpty()) return

        val playlistChanged = originalPlaylist != playlist || activePlaybackQueue.isEmpty()

        if (playlistChanged) {
            originalPlaylist = playlist
            activePlaybackQueue = generatePlaybackQueue(playlist, song)
        }

        val index = activePlaybackQueue.indexOfFirst { it.id == song.id }

        executeCommand { ctrl ->
            if (playlistChanged) {
                ctrl.setMediaItems(buildMediaItems(activePlaybackQueue))
            }
            if (index != -1) {
                ctrl.seekTo(index, 0)
                ctrl.prepare()
                ctrl.play()
            }
        }
    }

    fun toggleShuffleMode(prefs: SharedPreferences) {
        if (originalPlaylist.isEmpty()) {
            isShuffleEnabled = !isShuffleEnabled
            prefs.edit { putBoolean("shuffle_enabled", isShuffleEnabled) }
            return
        }

        val anchorSong = currentSong

        isShuffleEnabled = !isShuffleEnabled
        prefs.edit { putBoolean("shuffle_enabled", isShuffleEnabled) }

        if (isShuffleEnabled) {
            val anchorIndex = anchorSong?.let { originalPlaylist.indexOf(it) } ?: 0
            shuffleOrder = (listOf(anchorIndex) + (0 until originalPlaylist.size)
                .filter { it != anchorIndex }
                .shuffled())
                .toImmutableList()

            shufflePosition = 0
            activePlaybackQueue = shuffleOrder.map { originalPlaylist[it] }.toImmutableList()
        } else {
            activePlaybackQueue = originalPlaylist
        }

        val newIndex = activePlaybackQueue.indexOfFirst { it.id == anchorSong?.id }

        executeCommand { ctrl ->
            val wasPlaying = ctrl.isPlaying
            val currentPositionMs = ctrl.currentPosition
            ctrl.setMediaItems(buildMediaItems(activePlaybackQueue))
            ctrl.seekTo(if (newIndex >= 0) newIndex else 0, currentPositionMs)
            ctrl.prepare()
            if (wasPlaying) ctrl.play()
        }
    }

    private fun generatePlaybackQueue(
        playlist: ImmutableList<Song>,
        anchorSong: Song?
    ): ImmutableList<Song> {
        if (!isShuffleEnabled) return playlist
        if (playlist.size <= 1) return playlist

        val anchorIndex = anchorSong?.let { playlist.indexOf(it) } ?: -1

        shuffleOrder = if (anchorIndex >= 0) {
            (listOf(anchorIndex) + (0 until playlist.size)
                .filter { it != anchorIndex }
                .shuffled())
                .toImmutableList()
        } else {
            (0 until playlist.size).shuffled().toImmutableList()
        }

        shufflePosition = 0
        return shuffleOrder.map { playlist[it] }.toImmutableList()
    }

    fun reshuffleQueue() {
        if (!isShuffleEnabled || originalPlaylist.isEmpty()) return

        val playedIndices = shuffleOrder.take(shufflePosition + 1)
        val remainingIndices = shuffleOrder.drop(shufflePosition + 1)

        if (remainingIndices.isNotEmpty()) {
            val newRemaining = remainingIndices.shuffled()
            shuffleOrder = (playedIndices + newRemaining).toImmutableList()
            activePlaybackQueue = shuffleOrder.map { originalPlaylist[it] }.toImmutableList()

            executeCommand { ctrl ->
                val wasPlaying = ctrl.isPlaying
                val currentPositionMs = ctrl.currentPosition
                ctrl.setMediaItems(buildMediaItems(activePlaybackQueue))
                ctrl.seekTo(shufflePosition, currentPositionMs)
                ctrl.prepare()
                if (wasPlaying) ctrl.play()
            }
        }
    }

    fun togglePlayPause() {
        executeCommand { ctrl ->
            if (ctrl.isPlaying) ctrl.pause() else ctrl.play()
        }
    }

    fun playNext() {
        executeCommand { ctrl ->
            if (ctrl.hasNextMediaItem()) {
                ctrl.seekToNext()
            } else {
                ctrl.seekTo(0, 0)
                ctrl.pause()
            }
        }
    }

    fun playPrevious() {
        executeCommand { it.seekToPrevious() }
    }

    fun seekTo(position: Long) {
        playbackPosition = position
        executeCommand { it.seekTo(position) }
    }

    private fun buildMediaItems(queue: ImmutableList<Song>): List<MediaItem> {
        return queue.map { s ->
            MediaItem.Builder()
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
    }

    override fun onCleared() {
        super.onCleared()
        val listener = playerListener
        if (listener != null) {
            controller?.removeListener(listener)
        }
        playerListener = null
        controller = null
        pendingActions.clear()
    }
}

// ─────────────────────────────────────────────
// Activity
// ─────────────────────────────────────────────

class MainActivity : ComponentActivity() {
    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private val viewModel: MusicPlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("MusicPlayerDeckPrefs", MODE_PRIVATE)
        viewModel.initialize(prefs)

        enableEdgeToEdge()
        setContent {
            MusicPlayerDeckTheme {
                val onSongSelected: (Song, ImmutableList<Song>) -> Unit = remember {
                    { song, playlist -> viewModel.playSong(song, playlist) }
                }
                val onShuffleToggle: () -> Unit = remember {
                    { viewModel.toggleShuffleMode(prefs) }
                }
                val onReshuffle: () -> Unit = remember {
                    { viewModel.reshuffleQueue() }
                }
                val onPlayPause: () -> Unit = remember {
                    { viewModel.togglePlayPause() }
                }
                val onNext: () -> Unit = remember {
                    { viewModel.playNext() }
                }
                val onPrevious: () -> Unit = remember {
                    { viewModel.playPrevious() }
                }
                val onSeek: (Long) -> Unit = remember {
                    { newPosition -> viewModel.seekTo(newPosition) }
                }

                MainScreen(
                    currentSong = viewModel.currentSong,
                    isPlaying = viewModel.isPlaying,
                    isShuffleEnabled = viewModel.isShuffleEnabled,
                    playbackPositionProvider = { viewModel.playbackPosition },
                    shufflePosition = viewModel.shufflePosition,
                    queueSize = viewModel.activePlaybackQueue.size,
                    onSongSelected = onSongSelected,
                    onShuffleToggle = onShuffleToggle,
                    onReshuffle = onReshuffle,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onSeek = onSeek
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val sessionToken = SessionToken(
            this,
            ComponentName(this, PlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener(
            { viewModel.setController(controllerFuture.get()) },
            ContextCompat.getMainExecutor(this)
        )
    }

    override fun onStop() {
        MediaController.releaseFuture(controllerFuture)
        super.onStop()
    }
}

// ─────────────────────────────────────────────
// Playback Service
// ─────────────────────────────────────────────

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .build().apply {
                repeatMode = Player.REPEAT_MODE_ALL
            }

        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.player?.release()
        mediaSession?.release()
        super.onDestroy()
    }
}

// ─────────────────────────────────────────────
// Shuffle Toggle
// ─────────────────────────────────────────────

@Composable
fun EnhancedShuffleToggle(
    isShuffleEnabled: Boolean,
    onShuffleToggle: () -> Unit,
    onReshuffle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 16.dp, start = 8.dp, end = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onShuffleToggle,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isShuffleEnabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (isShuffleEnabled) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant
            ),
            shape = RoundedCornerShape(12.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Shuffle,
                contentDescription = "Toggle Shuffle",
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isShuffleEnabled) "Shuffle Mode: ON" else "Shuffle Mode: OFF",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1
            )
        }

        if (isShuffleEnabled) {
            Spacer(modifier = Modifier.width(12.dp))
            FilledIconButton(
                onClick = onReshuffle,
                shape = CircleShape,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reshuffle Queue",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// Main Screen
// ─────────────────────────────────────────────

@Composable
fun MainScreen(
    currentSong: Song?,
    isPlaying: Boolean,
    isShuffleEnabled: Boolean,
    playbackPositionProvider: () -> Long,
    shufflePosition: Int,
    queueSize: Int,
    onSongSelected: (Song, ImmutableList<Song>) -> Unit,
    onShuffleToggle: () -> Unit,
    onReshuffle: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember {
        context.getSharedPreferences("MusicPlayerDeckPrefs", Context.MODE_PRIVATE)
    }

    var songs by remember { mutableStateOf<ImmutableList<Song>>(persistentListOf()) }
    var isLoading by remember { mutableStateOf(false) }

    var hasPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_MEDIA_AUDIO
                ) == PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(
                            context, Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    var favoriteIds by remember {
        mutableStateOf(prefs.getStringSet("favorite_ids", emptySet()) ?: emptySet())
    }

    var playlists by remember { mutableStateOf(loadPlaylists(prefs)) }
    var isCreatingPlaylist by remember { mutableStateOf(false) }

    val toggleFavorite: (Long) -> Unit = remember {
        { songId: Long ->
            val newFavorites = favoriteIds.toMutableSet()
            val idStr = songId.toString()
            if (newFavorites.contains(idStr)) newFavorites.remove(idStr)
            else newFavorites.add(idStr)
            favoriteIds = newFavorites
            prefs.edit { putStringSet("favorite_ids", newFavorites) }
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted =
            permissions[Manifest.permission.READ_MEDIA_AUDIO]
                ?: permissions[Manifest.permission.READ_EXTERNAL_STORAGE]
                ?: false
        hasPermission = audioGranted
        if (audioGranted) {
            isLoading = true
            scope.launch {
                try {
                    songs = withContext(Dispatchers.IO) { fetchSongs(context) }
                    prefs.edit { putBoolean("songs_loaded", true) }
                } finally {
                    isLoading = false
                }
            }
        }
    }

    val onFindSongs: () -> Unit = {
        if (hasPermission) {
            isLoading = true
            scope.launch {
                try {
                    songs = withContext(Dispatchers.IO) { fetchSongs(context) }
                    prefs.edit { putBoolean("songs_loaded", true) }
                } finally {
                    isLoading = false
                }
            }
        } else {
            val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.POST_NOTIFICATIONS
                )
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            launcher.launch(permissionsToRequest)
        }
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Songs", "Playlists", "Folder", "Favorites", "Album", "Artist")

    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var selectedAlbum by remember { mutableStateOf<String?>(null) }
    var selectedArtist by remember { mutableStateOf<String?>(null) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }

    val folders by remember {
        derivedStateOf {
            songs.groupBy { it.folder }
                .mapValues { it.value.size }
                .toList()
                .sortedBy { it.first }
                .toImmutableList()
        }
    }
    val albums by remember {
        derivedStateOf {
            songs.groupBy { it.album }
                .mapValues { it.value.size }
                .toList()
                .sortedBy { it.first }
                .toImmutableList()
        }
    }
    val artists by remember {
        derivedStateOf {
            songs.groupBy { it.artist }
                .mapValues { it.value.size }
                .toList()
                .sortedBy { it.first }
                .toImmutableList()
        }
    }

    LaunchedEffect(selectedTabIndex) {
        selectedFolder = null
        selectedAlbum = null
        selectedArtist = null
        selectedPlaylist = null
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission && prefs.getBoolean("songs_loaded", false)) {
            isLoading = true
            songs = withContext(Dispatchers.IO) { fetchSongs(context) }
            isLoading = false
        }
    }

    val gradient = if (isSystemInDarkTheme()) DarkMintGradient else MintGradient

    if (isCreatingPlaylist) {
        CreatePlaylistScreen(
            allSongs = songs,
            folders = folders,
            onDismiss = { isCreatingPlaylist = false },
            onSave = { name, selectedIds ->
                val newPlaylist = Playlist(name, selectedIds.toImmutableList())
                playlists = (playlists + newPlaylist).toImmutableList()
                savePlaylists(prefs, playlists)
                isCreatingPlaylist = false
            }
        )
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Music Player Deck",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            bottomBar = {
                currentSong?.let { song ->
                    MiniPlayer(
                        song = song,
                        isPlaying = isPlaying,
                        playbackPositionProvider = playbackPositionProvider,
                        isShuffleEnabled = isShuffleEnabled,
                        shufflePosition = shufflePosition,
                        queueSize = queueSize,
                        onPlayPause = onPlayPause,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        onSeek = onSeek
                    )
                }
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(gradient)
                    .padding(innerPadding)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    ScrollableTabRow(
                        selectedTabIndex = selectedTabIndex,
                        edgePadding = 0.dp,
                        containerColor = Color.Transparent,
                        divider = {},
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTabIndex == index,
                                onClick = { selectedTabIndex = index },
                                text = {
                                    Text(
                                        text = title,
                                        color = if (selectedTabIndex == index)
                                            MaterialTheme.colorScheme.onBackground
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (selectedTabIndex == index)
                                            FontWeight.Bold
                                        else FontWeight.Medium
                                    )
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        when (selectedTabIndex) {
                            0 -> {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    if (songs.isNotEmpty()) {
                                        EnhancedShuffleToggle(
                                            isShuffleEnabled = isShuffleEnabled,
                                            onShuffleToggle = onShuffleToggle,
                                            onReshuffle = onReshuffle
                                        )
                                    }
                                    if (isLoading) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    } else if (songs.isEmpty()) {
                                        FindSongsCTA(
                                            isLoading = isLoading,
                                            onFindSongs = onFindSongs
                                        )
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            contentPadding = PaddingValues(bottom = 24.dp)
                                        ) {
                                            items(items = songs, key = { it.id }) { song ->
                                                SongItem(
                                                    song = song,
                                                    isPlaying = currentSong?.id == song.id,
                                                    currentList = songs,
                                                    isFavorite = favoriteIds.contains(song.id.toString()),
                                                    onFavoriteToggle = toggleFavorite,
                                                    onSongClick = onSongSelected
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            1 -> {
                                PlaylistsTab(
                                    playlists = playlists,
                                    songs = songs,
                                    currentSong = currentSong,
                                    selectedPlaylist = selectedPlaylist,
                                    onPlaylistClick = { selectedPlaylist = it },
                                    onBackClick = { selectedPlaylist = null },
                                    onCreateClick = { isCreatingPlaylist = true },
                                    favoriteIds = favoriteIds,
                                    isShuffleEnabled = isShuffleEnabled,
                                    onShuffleToggle = onShuffleToggle,
                                    onReshuffle = onReshuffle,
                                    onToggleFavorite = toggleFavorite,
                                    onSongSelected = onSongSelected,
                                    onDeletePlaylist = { playlist ->
                                        playlists = playlists.filter { it.name != playlist.name }
                                            .toImmutableList()
                                        savePlaylists(prefs, playlists)
                                    }
                                )
                            }

                            2 -> {
                                GroupedTab(
                                    items = folders,
                                    isLoading = isLoading,
                                    icon = Icons.Default.Folder,
                                    selectedItem = selectedFolder,
                                    onItemClick = { selectedFolder = it },
                                    onBackClick = { selectedFolder = null },
                                    songs = songs,
                                    filterPredicate = { it.folder == selectedFolder },
                                    favoriteIds = favoriteIds,
                                    isShuffleEnabled = isShuffleEnabled,
                                    onShuffleToggle = onShuffleToggle,
                                    onReshuffle = onReshuffle,
                                    onToggleFavorite = toggleFavorite,
                                    onSongSelected = onSongSelected,
                                    onFindSongs = onFindSongs,
                                    currentSong = currentSong
                                )
                            }

                            3 -> {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    val favoriteSongs by remember(songs, favoriteIds) {
                                        derivedStateOf {
                                            songs.filter { favoriteIds.contains(it.id.toString()) }
                                                .toImmutableList()
                                        }
                                    }

                                    if (favoriteSongs.isNotEmpty()) {
                                        EnhancedShuffleToggle(
                                            isShuffleEnabled = isShuffleEnabled,
                                            onShuffleToggle = onShuffleToggle,
                                            onReshuffle = onReshuffle
                                        )
                                    }

                                    if (favoriteSongs.isEmpty()) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "No favorites yet.",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            contentPadding = PaddingValues(bottom = 24.dp)
                                        ) {
                                            items(
                                                items = favoriteSongs,
                                                key = { it.id }
                                            ) { song ->
                                                SongItem(
                                                    song = song,
                                                    isPlaying = currentSong?.id == song.id,
                                                    currentList = favoriteSongs,
                                                    isFavorite = true,
                                                    onFavoriteToggle = toggleFavorite,
                                                    onSongClick = onSongSelected
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            4 -> {
                                GroupedTab(
                                    items = albums,
                                    isLoading = isLoading,
                                    icon = Icons.Default.Album,
                                    selectedItem = selectedAlbum,
                                    onItemClick = { selectedAlbum = it },
                                    onBackClick = { selectedAlbum = null },
                                    songs = songs,
                                    filterPredicate = { it.album == selectedAlbum },
                                    favoriteIds = favoriteIds,
                                    isShuffleEnabled = isShuffleEnabled,
                                    onShuffleToggle = onShuffleToggle,
                                    onReshuffle = onReshuffle,
                                    onToggleFavorite = toggleFavorite,
                                    onSongSelected = onSongSelected,
                                    onFindSongs = onFindSongs,
                                    currentSong = currentSong
                                )
                            }

                            5 -> {
                                GroupedTab(
                                    items = artists,
                                    isLoading = isLoading,
                                    icon = Icons.Default.Person,
                                    selectedItem = selectedArtist,
                                    onItemClick = { selectedArtist = it },
                                    onBackClick = { selectedArtist = null },
                                    songs = songs,
                                    filterPredicate = { it.artist == selectedArtist },
                                    favoriteIds = favoriteIds,
                                    isShuffleEnabled = isShuffleEnabled,
                                    onShuffleToggle = onShuffleToggle,
                                    onReshuffle = onReshuffle,
                                    onToggleFavorite = toggleFavorite,
                                    onSongSelected = onSongSelected,
                                    onFindSongs = onFindSongs,
                                    currentSong = currentSong
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// Find Songs CTA
// ─────────────────────────────────────────────

@Composable
fun FindSongsCTA(
    isLoading: Boolean,
    onFindSongs: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Your library is empty",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onFindSongs,
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Scan Device for Music",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// Grouped Tab (Folder / Album / Artist)
// ─────────────────────────────────────────────

@Composable
fun GroupedTab(
    items: ImmutableList<Pair<String, Int>>,
    isLoading: Boolean,
    icon: ImageVector,
    selectedItem: String?,
    onItemClick: (String) -> Unit,
    onBackClick: () -> Unit,
    songs: ImmutableList<Song>,
    filterPredicate: (Song) -> Boolean,
    favoriteIds: Set<String>,
    isShuffleEnabled: Boolean,
    onShuffleToggle: () -> Unit,
    onReshuffle: () -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onSongSelected: (Song, ImmutableList<Song>) -> Unit,
    onFindSongs: () -> Unit,
    currentSong: Song?
) {
    if (selectedItem == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (items.isNotEmpty()) {
                EnhancedShuffleToggle(
                    isShuffleEnabled = isShuffleEnabled,
                    onShuffleToggle = onShuffleToggle,
                    onReshuffle = onReshuffle
                )
            }
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (items.isEmpty()) {
                FindSongsCTA(isLoading = isLoading, onFindSongs = onFindSongs)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(items) { (name, count) ->
                        GroupItem(
                            name = name,
                            count = count,
                            icon = icon,
                            onClick = { onItemClick(name) }
                        )
                    }
                }
            }
        }
    } else {
        val groupSongs by remember(songs, selectedItem) {
            derivedStateOf {
                songs.filter(filterPredicate).toImmutableList()
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onBackClick() }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = selectedItem,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            if (groupSongs.isNotEmpty()) {
                EnhancedShuffleToggle(
                    isShuffleEnabled = isShuffleEnabled,
                    onShuffleToggle = onShuffleToggle,
                    onReshuffle = onReshuffle
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(items = groupSongs, key = { it.id }) { song ->
                    SongItem(
                        song = song,
                        isPlaying = currentSong?.id == song.id,
                        currentList = groupSongs,
                        isFavorite = favoriteIds.contains(song.id.toString()),
                        onFavoriteToggle = onToggleFavorite,
                        onSongClick = onSongSelected
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// Playlists Tab
// ─────────────────────────────────────────────

@Composable
fun PlaylistsTab(
    playlists: ImmutableList<Playlist>,
    songs: ImmutableList<Song>,
    currentSong: Song?,
    selectedPlaylist: Playlist?,
    onPlaylistClick: (Playlist) -> Unit,
    onBackClick: () -> Unit,
    onCreateClick: () -> Unit,
    favoriteIds: Set<String>,
    isShuffleEnabled: Boolean,
    onShuffleToggle: () -> Unit,
    onReshuffle: () -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onSongSelected: (Song, ImmutableList<Song>) -> Unit,
    onDeletePlaylist: (Playlist) -> Unit
) {
    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }

    if (playlistToDelete != null) {
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text(text = "Delete Playlist", fontWeight = FontWeight.Bold) },
            text = { Text(text = "Are you sure you want to delete this playlist?") },
            confirmButton = {
                TextButton(onClick = {
                    playlistToDelete?.let { onDeletePlaylist(it) }
                    playlistToDelete = null
                }) {
                    Text(text = "Delete", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) {
                    Text(
                        text = "Cancel",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }

    if (selectedPlaylist == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            Button(
                onClick = onCreateClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                contentPadding = PaddingValues(16.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Create New Playlist",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            if (playlists.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No playlists created yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(playlists) { playlist ->
                        GroupItem(
                            name = playlist.name,
                            count = playlist.songIds.size,
                            icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                            onClick = { onPlaylistClick(playlist) },
                            onDeleteClick = { playlistToDelete = playlist }
                        )
                    }
                }
            }
        }
    } else {
        val playlistSongs by remember(songs, selectedPlaylist) {
            derivedStateOf {
                selectedPlaylist.songIds
                    .mapNotNull { id -> songs.find { it.id == id } }
                    .toImmutableList()
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onBackClick() }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = selectedPlaylist.name,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            if (playlistSongs.isNotEmpty()) {
                EnhancedShuffleToggle(
                    isShuffleEnabled = isShuffleEnabled,
                    onShuffleToggle = onShuffleToggle,
                    onReshuffle = onReshuffle
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(items = playlistSongs, key = { it.id }) { song ->
                    SongItem(
                        song = song,
                        // FIX: now properly shows playing state inside playlists
                        isPlaying = currentSong?.id == song.id,
                        currentList = playlistSongs,
                        isFavorite = favoriteIds.contains(song.id.toString()),
                        onFavoriteToggle = onToggleFavorite,
                        onSongClick = onSongSelected
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// Create Playlist Screen
// ─────────────────────────────────────────────

@Composable
fun CreatePlaylistScreen(
    allSongs: ImmutableList<Song>,
    folders: ImmutableList<Pair<String, Int>>,
    onDismiss: () -> Unit,
    onSave: (String, Set<Long>) -> Unit
) {
    var playlistName by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    var selectedSongs by remember { mutableStateOf(setOf<Long>()) }

    LaunchedEffect(searchQuery) {
        delay(300)
        debouncedQuery = searchQuery
    }

    val filteredSongs = remember(allSongs, debouncedQuery) {
        if (debouncedQuery.isBlank()) allSongs
        else allSongs.filter {
            it.title.contains(debouncedQuery, ignoreCase = true) ||
                    it.artist.contains(debouncedQuery, ignoreCase = true)
        }.toImmutableList()
    }

    val gradient = if (isSystemInDarkTheme()) DarkMintGradient else MintGradient

    BackHandler { onDismiss() }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Text(
                        text = "Create New Playlist",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text(text = "Playlist Name") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text(text = "Search Songs to Add") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Songs (${selectedSongs.size} selected)",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 16.sp
                    )

                    var showFolderDialog by remember { mutableStateOf(false) }

                    TextButton(onClick = { showFolderDialog = true }) {
                        Text(
                            text = "Add by Folder",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (showFolderDialog) {
                        AlertDialog(
                            onDismissRequest = { showFolderDialog = false },
                            title = { Text(text = "Select Folder", fontWeight = FontWeight.Bold) },
                            text = {
                                LazyColumn {
                                    items(folders) { (folderName, count) ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    val folderSongs =
                                                        allSongs.filter { it.folder == folderName }
                                                    selectedSongs =
                                                        selectedSongs + folderSongs.map { it.id }
                                                            .toSet()
                                                    showFolderDialog = false
                                                }
                                                .padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Folder,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Text(
                                                text = "$folderName ($count songs)",
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showFolderDialog = false }) {
                                    Text(text = "Cancel", fontWeight = FontWeight.Bold)
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filteredSongs) { song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    selectedSongs = if (selectedSongs.contains(song.id)) {
                                        selectedSongs - song.id
                                    } else {
                                        selectedSongs + song.id
                                    }
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedSongs.contains(song.id),
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = song.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            // Duration in create-playlist song rows
                            Text(
                                text = formatDuration(song.duration),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        if (playlistName.isNotBlank()) onSave(playlistName, selectedSongs)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    enabled = playlistName.isNotBlank() && selectedSongs.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(text = "Save Playlist", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// Playlist persistence — JSON-based to preserve order
// ─────────────────────────────────────────────

fun savePlaylists(prefs: SharedPreferences, playlists: ImmutableList<Playlist>) {
    val jsonArray = JSONArray()
    for (playlist in playlists) {
        val obj = JSONObject()
        obj.put("name", playlist.name)
        val idsArray = JSONArray()
        for (id in playlist.songIds) {
            idsArray.put(id)
        }
        obj.put("songIds", idsArray)
        jsonArray.put(obj)
    }
    prefs.edit {
        putString("playlists_json", jsonArray.toString())
        // Clean up legacy keys
        remove("playlist_names")
    }
}

fun loadPlaylists(prefs: SharedPreferences): ImmutableList<Playlist> {
    val json = prefs.getString("playlists_json", null)

    // Migrate from legacy StringSet-based storage if present
    if (json == null) {
        val legacyNames = prefs.getStringSet("playlist_names", null)
        if (legacyNames != null) {
            val migrated = legacyNames.map { name ->
                val idsStr = prefs.getStringSet("playlist_ids_$name", emptySet()) ?: emptySet()
                val ids = idsStr.mapNotNull { it.toLongOrNull() }.toImmutableList()
                Playlist(name = name, songIds = ids)
            }.toImmutableList()
            // Save in new format immediately
            savePlaylists(prefs, migrated)
            return migrated
        }
        return persistentListOf()
    }

    return try {
        val jsonArray = JSONArray(json)
        val result = mutableListOf<Playlist>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val name = obj.getString("name")
            val idsArray = obj.getJSONArray("songIds")
            val ids = mutableListOf<Long>()
            for (j in 0 until idsArray.length()) {
                ids.add(idsArray.getLong(j))
            }
            result.add(Playlist(name = name, songIds = ids.toImmutableList()))
        }
        result.toImmutableList()
    } catch (e: Exception) {
        e.printStackTrace()
        persistentListOf()
    }
}

// ─────────────────────────────────────────────
// Group Item
// ─────────────────────────────────────────────

@Composable
fun GroupItem(
    name: String,
    count: Int,
    icon: ImageVector,
    onClick: () -> Unit,
    onDeleteClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$count songs",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (onDeleteClick != null) {
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// Mini Player — now with time labels
// ─────────────────────────────────────────────

@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    playbackPositionProvider: () -> Long,
    isShuffleEnabled: Boolean,
    shufflePosition: Int,
    queueSize: Int,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit
) {
    val playbackPosition = playbackPositionProvider()
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }

    val safeDuration = song.duration.toFloat().coerceAtLeast(1000f)
    val rawPosition = if (isDragging) dragPosition else playbackPosition.toFloat()
    val safePosition = rawPosition.coerceIn(0f, safeDuration)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 16.dp
    ) {
        Column(
            modifier = Modifier
                .padding(top = 12.dp, bottom = 16.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth()
        ) {
            // Slider
            Slider(
                value = safePosition,
                onValueChange = { newPosition ->
                    isDragging = true
                    dragPosition = newPosition
                },
                onValueChangeFinished = {
                    onSeek(dragPosition.toLong())
                    isDragging = false
                },
                valueRange = 0f..safeDuration,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
            )

            // Time labels
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDurationLong(safePosition.toLong()),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatDuration(song.duration),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album art
                Card(
                    modifier = Modifier.size(54.dp),
                    shape = MaterialTheme.shapes.small,
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        AsyncImage(
                            model = song.albumArtUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee(
                                iterations = Int.MAX_VALUE,
                                repeatDelayMillis = 2000
                            ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = song.artist,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onPrevious) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    IconButton(onClick = onPlayPause) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(42.dp)
                        )
                    }
                    IconButton(onClick = onNext) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// Song Item — now shows duration
// ─────────────────────────────────────────────

@Composable
fun SongItem(
    song: Song,
    isPlaying: Boolean,
    currentList: ImmutableList<Song>,
    isFavorite: Boolean,
    onFavoriteToggle: (Long) -> Unit,
    onSongClick: (Song, ImmutableList<Song>) -> Unit
) {
    val cardColor = MaterialTheme.colorScheme.surface
    val borderColor = if (isPlaying) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSongClick(song, currentList) },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = borderColor
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                modifier = Modifier.size(44.dp),
                shape = MaterialTheme.shapes.small
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    AsyncImage(
                        model = song.albumArtUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    fontWeight = if (isPlaying) FontWeight.ExtraBold else FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isPlaying) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${song.artist} • ${song.album} • ${formatDuration(song.duration)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isPlaying) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(end = 12.dp)
                ) {
                    Text(
                        text = "PLAYING",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            IconButton(
                onClick = { onFavoriteToggle(song.id) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite
                    else Icons.Default.FavoriteBorder,
                    contentDescription = "Toggle Favorite",
                    tint = if (isFavorite) Color.Red
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// Song fetching — sorted by title, uses RELATIVE_PATH on Q+
// ─────────────────────────────────────────────

fun fetchSongs(context: Context): ImmutableList<Song> {
    val songs = mutableListOf<Song>()
    try {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATA // still needed as fallback & for pre-Q
        )

        // Prefer RELATIVE_PATH on Q+ for folder extraction
        val hasRelativePath = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        if (hasRelativePath) {
            projection.add(MediaStore.Audio.Media.RELATIVE_PATH)
        }

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        context.contentResolver.query(
            collection,
            projection.toTypedArray(),
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val relPathCol = if (hasRelativePath) {
                cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
            } else -1

            while (cursor.moveToNext()) {
                try {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: "Unknown Track"
                    val artist = cursor.getString(artistCol) ?: "Unknown Artist"
                    val album = cursor.getString(albumCol) ?: "Unknown Album"
                    val duration = cursor.getInt(durationCol)
                    val albumId = cursor.getLong(albumIdCol)
                    val path = cursor.getString(dataCol) ?: ""

                    // Prefer RELATIVE_PATH, fall back to parent dir from DATA
                    val folder = if (relPathCol >= 0) {
                        val relPath = cursor.getString(relPathCol) ?: ""
                        relPath.trimEnd('/').substringAfterLast('/').ifEmpty { "Unknown" }
                    } else {
                        try {
                            if (path.isNotEmpty()) File(path).parentFile?.name ?: "Unknown"
                            else "Unknown"
                        } catch (_: Exception) {
                            "Unknown"
                        }
                    }

                    val contentUri: Uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                    )

                    val albumArtUri = try {
                        if (albumId > 0) {
                            ContentUris.withAppendedId(
                                "content://media/external/audio/albumart".toUri(), albumId
                            )
                        } else null
                    } catch (_: Exception) {
                        null
                    }

                    songs.add(
                        Song(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            duration = duration,
                            uri = contentUri,
                            albumArtUri = albumArtUri,
                            folder = folder
                        )
                    )
                } catch (_: Exception) {
                    // Skip malformed entries silently
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return persistentListOf()
    }
    return songs.toImmutableList()
}


@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    MusicPlayerDeckTheme {
        MainScreen(
            currentSong = null,
            isPlaying = false,
            isShuffleEnabled = false,
            playbackPositionProvider = { 0L },
            shufflePosition = 0,
            queueSize = 0,
            onSongSelected = { _, _ -> },
            onShuffleToggle = {},
            onReshuffle = {},
            onPlayPause = {},
            onNext = {},
            onPrevious = {},
            onSeek = {}
        )
    }
}