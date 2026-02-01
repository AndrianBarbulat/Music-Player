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
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import java.io.File
import java.util.Locale

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

// --- 1. VIEWMODEL ARCHITECTURE ---
class MusicPlayerViewModel : ViewModel() {
    private var controller: MediaController? = null

    // THE FIX: Command Queue for asynchronous controller connection
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
                delay(500)
            }
        }
    }

    fun setController(mediaController: MediaController) {
        controller = mediaController
        isPlaying = mediaController.isPlaying

        mediaController.addListener(object : Player.Listener {
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
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                if (isShuffleEnabled && reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                    val newMediaItem = newPosition.mediaItem
                    val newSong = activePlaybackQueue.find { it.id.toString() == newMediaItem?.mediaId }
                    newSong?.let {
                        val newIndex = activePlaybackQueue.indexOfFirst { it.id == newSong.id }
                        if (newIndex != -1 && newIndex != shufflePosition) {
                            shufflePosition = newIndex
                        }
                    }
                }
            }
        })

        // Execute any actions the user clicked while the service was starting!
        pendingActions.forEach { action -> action(mediaController) }
        pendingActions.clear()
    }

    // THE FIX: Safe wrapper that guarantees commands aren't lost
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

        // Update state instantly for UI
        if (playlistChanged) {
            originalPlaylist = playlist
            activePlaybackQueue = generatePlaybackQueue(playlist, song)
        }

        val index = activePlaybackQueue.indexOfFirst { it.id == song.id }

        // Queue player commands safely
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
            shuffleOrder = (listOf(anchorIndex) + (0 until originalPlaylist.size).filter { it != anchorIndex }.shuffled()).toImmutableList()
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

    private fun generatePlaybackQueue(playlist: ImmutableList<Song>, anchorSong: Song?): ImmutableList<Song> {
        if (!isShuffleEnabled) return playlist
        if (playlist.size <= 1) return playlist

        val anchorIndex = anchorSong?.let { playlist.indexOf(it) } ?: -1
        shuffleOrder = if (anchorIndex >= 0) {
            (listOf(anchorIndex) + (0 until playlist.size).filter { it != anchorIndex }.shuffled()).toImmutableList()
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
        if (isShuffleEnabled && activePlaybackQueue.isNotEmpty()) {
            shufflePosition = (shufflePosition + 1) % activePlaybackQueue.size
        }
        executeCommand { it.seekToNext() }
    }

    fun playPrevious() {
        if (isShuffleEnabled && activePlaybackQueue.isNotEmpty()) {
            shufflePosition = (shufflePosition - 1 + activePlaybackQueue.size) % activePlaybackQueue.size
        }
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
        controller = null
        pendingActions.clear()
    }
}


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
                val onSongSelected: (Song, ImmutableList<Song>) -> Unit = remember { { song, playlist -> viewModel.playSong(song, playlist) } }
                val onShuffleToggle: () -> Unit = remember { { viewModel.toggleShuffleMode(prefs) } }
                val onReshuffle: () -> Unit = remember { { viewModel.reshuffleQueue() } }
                val onPlayPause: () -> Unit = remember { { viewModel.togglePlayPause() } }
                val onNext: () -> Unit = remember { { viewModel.playNext() } }
                val onPrevious: () -> Unit = remember { { viewModel.playPrevious() } }
                val onSeek: (Long) -> Unit = remember { { newPosition -> viewModel.seekTo(newPosition) } }

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

    // Connect to the Background Service
    override fun onStart() {
        super.onStart()
        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
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

// --- 2. BACKGROUND SERVICE & AUDIO FOCUS ---
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        // AudioAttributes handles pausing for phone calls automatically
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true) // TRUE = Request Audio Focus
            .build()

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

@Composable
fun EnhancedShuffleToggle(
    isShuffleEnabled: Boolean,
    onShuffleToggle: () -> Unit,
    onReshuffle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isShuffleEnabled) {
            IconButton(
                onClick = onReshuffle,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reshuffle",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
        }
        IconButton(
            onClick = onShuffleToggle,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Shuffle,
                contentDescription = "Shuffle",
                tint = if (isShuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@SuppressLint("UseKtx")
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
    val prefs = remember { context.getSharedPreferences("MusicPlayerDeckPrefs", Context.MODE_PRIVATE) }

    var songs by remember { mutableStateOf<ImmutableList<Song>>(persistentListOf()) }
    var isLoading by remember { mutableStateOf(false) }

    // Added POST_NOTIFICATIONS for Android 13+ Background Playback
    var hasPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    var favoriteIds by remember { mutableStateOf(prefs.getStringSet("favorite_ids", emptySet()) ?: emptySet()) }

    var playlists by remember { mutableStateOf(loadPlaylists(prefs)) }
    var isCreatingPlaylist by remember { mutableStateOf(false) }

    val toggleFavorite: (Long) -> Unit = remember {
        { songId: Long ->
            val newFavorites = favoriteIds.toMutableSet()
            val idStr = songId.toString()
            if (newFavorites.contains(idStr)) newFavorites.remove(idStr) else newFavorites.add(idStr)
            favoriteIds = newFavorites
            prefs.edit { putStringSet("favorite_ids", newFavorites) }
        }
    }

    // Handle multiple permissions for modern Android versions
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.READ_MEDIA_AUDIO] ?: permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
        hasPermission = audioGranted

        if (audioGranted) {
            isLoading = true
            scope.launch {
                try {
                    songs = withContext(Dispatchers.IO) { fetchSongs(context) }
                    prefs.edit { putBoolean("songs_loaded", true) }
                } finally { isLoading = false }
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
                } finally { isLoading = false }
            }
        } else {
            val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            launcher.launch(permissionsToRequest)
        }
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Songs", "Favorites", "Album", "Playlists", "Artist", "Folder")

    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var selectedAlbum by remember { mutableStateOf<String?>(null) }
    var selectedArtist by remember { mutableStateOf<String?>(null) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }

    val folders by remember { derivedStateOf { songs.groupBy { it.folder }.mapValues { it.value.size }.toList().sortedBy { it.first }.toImmutableList() } }
    val albums by remember { derivedStateOf { songs.groupBy { it.album }.mapValues { it.value.size }.toList().sortedBy { it.first }.toImmutableList() } }
    val artists by remember { derivedStateOf { songs.groupBy { it.artist }.mapValues { it.value.size }.toList().sortedBy { it.first }.toImmutableList() } }

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
                    title = { Text("Music Player Deck", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
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
            Box(modifier = Modifier.fillMaxSize().background(gradient).padding(innerPadding)) {
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(16.dp))

                    ScrollableTabRow(
                        selectedTabIndex = selectedTabIndex,
                        edgePadding = 0.dp,
                        containerColor = Color.Transparent,
                        divider = {},
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]), color = MaterialTheme.colorScheme.onBackground)
                        }
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTabIndex == index,
                                onClick = { selectedTabIndex = index },
                                text = { Text(title, color = if (selectedTabIndex == index) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        when (selectedTabIndex) {
                            0 -> {
                                Column {
                                    if (songs.isNotEmpty()) EnhancedShuffleToggle(isShuffleEnabled, onShuffleToggle, onReshuffle)
                                    if (isLoading) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground) }
                                    else if (songs.isEmpty()) FindSongsCTA(isLoading, onFindSongs)
                                    else {
                                        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                                            items(items = songs, key = { it.id }) { song ->
                                                SongItem(
                                                    song = song,
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
                                Column {
                                    val favoriteSongs by remember(songs, favoriteIds) { derivedStateOf { songs.filter { favoriteIds.contains(it.id.toString()) }.toImmutableList() } }
                                    if (favoriteSongs.isNotEmpty()) EnhancedShuffleToggle(isShuffleEnabled, onShuffleToggle, onReshuffle)

                                    if (favoriteSongs.isEmpty()) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No favorites yet.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), textAlign = TextAlign.Center) }
                                    } else {
                                        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                                            items(items = favoriteSongs, key = { it.id }) { song ->
                                                SongItem(
                                                    song = song,
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
                            2 -> {
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
                                    onFindSongs = onFindSongs
                                )
                            }
                            3 -> {
                                PlaylistsTab(
                                    playlists = playlists,
                                    songs = songs,
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
                                        playlists = playlists.filter { it.name != playlist.name }.toImmutableList()
                                        savePlaylists(prefs, playlists)
                                        prefs.edit().remove("playlist_ids_${playlist.name}").apply()
                                    }
                                )
                            }
                            4 -> {
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
                                    onFindSongs = onFindSongs
                                )
                            }
                            5 -> {
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
                                    onFindSongs = onFindSongs
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FindSongsCTA(isLoading: Boolean, onFindSongs: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Your library is empty", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onFindSongs, enabled = !isLoading, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                else {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Find All Songs")
                }
            }
        }
    }
}

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
    onFindSongs: () -> Unit
) {
    if (selectedItem == null) {
        Column {
            if (items.isNotEmpty()) EnhancedShuffleToggle(isShuffleEnabled, onShuffleToggle, onReshuffle)
            if (isLoading) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground) }
            else if (items.isEmpty()) FindSongsCTA(isLoading, onFindSongs)
            else {
                LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(items) { (name, count) ->
                        GroupItem(name = name, count = count, icon = icon, onClick = { onItemClick(name) })
                    }
                }
            }
        }
    } else {
        val groupSongs by remember(songs, selectedItem) { derivedStateOf { songs.filter(filterPredicate).toImmutableList() } }

        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.weight(1f).clickable { onBackClick() }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(selectedItem, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground)
                }
                if (isShuffleEnabled) {
                    IconButton(onClick = onReshuffle, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Refresh, contentDescription = "Reshuffle", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
                }
                IconButton(onClick = onShuffleToggle, modifier = Modifier.size(32.dp)) { Icon(imageVector = Icons.Default.Shuffle, contentDescription = "Shuffle", tint = if (isShuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f), modifier = Modifier.size(20.dp)) }
            }
            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(items = groupSongs, key = { it.id }) { song ->
                    SongItem(
                        song = song,
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

@Composable
fun PlaylistsTab(
    playlists: ImmutableList<Playlist>,
    songs: ImmutableList<Song>,
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
            title = { Text("Delete Playlist") },
            text = { Text("Are you sure you want to delete this playlist?") },
            confirmButton = { TextButton(onClick = { playlistToDelete?.let { onDeletePlaylist(it) }; playlistToDelete = null }) { Text("Delete", color = Color.Red) } },
            dismissButton = { TextButton(onClick = { playlistToDelete = null }) { Text("Cancel") } }
        )
    }

    if (selectedPlaylist == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onCreateClick, modifier = Modifier.weight(1f).padding(bottom = 8.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create Playlist")
                }
                if (playlists.isNotEmpty()) {
                    if (isShuffleEnabled) IconButton(onClick = onReshuffle, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp).size(32.dp)) { Icon(Icons.Default.Refresh, contentDescription = "Reshuffle", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
                    IconButton(onClick = onShuffleToggle, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp).size(32.dp)) { Icon(imageVector = Icons.Default.Shuffle, contentDescription = "Shuffle", tint = if (isShuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f), modifier = Modifier.size(20.dp)) }
                }
            }

            if (playlists.isEmpty()) Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) { Text("No playlists yet.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)) }
            else {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(playlists) { playlist ->
                        GroupItem(name = playlist.name, count = playlist.songIds.size, icon = Icons.AutoMirrored.Filled.PlaylistPlay, onClick = { onPlaylistClick(playlist) }, onDeleteClick = { playlistToDelete = playlist })
                    }
                }
            }
        }
    } else {
        val playlistSongs by remember(songs, selectedPlaylist) { derivedStateOf { selectedPlaylist.songIds.mapNotNull { id -> songs.find { it.id == id } }.toImmutableList() } }

        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.weight(1f).clickable { onBackClick() }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(selectedPlaylist.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground)
                }
                if (isShuffleEnabled) IconButton(onClick = onReshuffle, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Refresh, contentDescription = "Reshuffle", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
                IconButton(onClick = onShuffleToggle, modifier = Modifier.size(32.dp)) { Icon(imageVector = Icons.Default.Shuffle, contentDescription = "Shuffle", tint = if (isShuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f), modifier = Modifier.size(20.dp)) }
            }
            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(items = playlistSongs, key = { it.id }) { song ->
                    SongItem(
                        song = song,
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
        else allSongs.filter { it.title.contains(debouncedQuery, ignoreCase = true) || it.artist.contains(debouncedQuery, ignoreCase = true) }.toImmutableList()
    }

    val gradient = if (isSystemInDarkTheme()) DarkMintGradient else MintGradient

    BackHandler { onDismiss() }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        Box(modifier = Modifier.fillMaxSize().background(gradient)) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground) }
                    Text("Create Playlist", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                }

                OutlinedTextField(value = playlistName, onValueChange = { playlistName = it }, label = { Text("Playlist Name") }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f), focusedBorderColor = MaterialTheme.colorScheme.primary))
                OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, label = { Text("Search Songs") }, leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f), focusedBorderColor = MaterialTheme.colorScheme.primary))

                Text("Selection Options", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onBackground)
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    var showFolderDialog by remember { mutableStateOf(false) }
                    Button(onClick = { showFolderDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))) { Text("Select Folder") }

                    if (showFolderDialog) {
                        AlertDialog(
                            onDismissRequest = { showFolderDialog = false },
                            title = { Text("Select Folder") },
                            text = {
                                LazyColumn {
                                    items(folders) { (folderName, count) ->
                                        Row(modifier = Modifier.fillMaxWidth().clickable { val folderSongs = allSongs.filter { it.folder == folderName }; selectedSongs = selectedSongs + folderSongs.map { it.id }.toSet(); showFolderDialog = false }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Folder, contentDescription = null)
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text("$folderName ($count songs)")
                                        }
                                    }
                                }
                            },
                            confirmButton = { TextButton(onClick = { showFolderDialog = false }) { Text("Cancel") } }
                        )
                    }
                    Button(onClick = { selectedSongs = emptySet() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f))) { Text("Clear All") }
                }

                Text("Songs (${selectedSongs.size} selected)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filteredSongs) { song ->
                        Row(modifier = Modifier.fillMaxWidth().clickable { selectedSongs = if (selectedSongs.contains(song.id)) selectedSongs - song.id else selectedSongs + song.id }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = selectedSongs.contains(song.id), onCheckedChange = { checked -> selectedSongs = if (checked) selectedSongs + song.id else selectedSongs - song.id })
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(song.title, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
                                Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                            }
                        }
                    }
                }

                Button(onClick = { if (playlistName.isNotBlank()) onSave(playlistName, selectedSongs) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), enabled = playlistName.isNotBlank() && selectedSongs.isNotEmpty(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text("Save Playlist") }
            }
        }
    }
}

@SuppressLint("UseKtx")
fun savePlaylists(prefs: SharedPreferences, playlists: ImmutableList<Playlist>) {
    val names = playlists.map { it.name }.toSet()
    prefs.edit { putStringSet("playlist_names", names) }
    for (playlist in playlists) {
        val ids = playlist.songIds.map { it.toString() }.toSet()
        prefs.edit { putStringSet("playlist_ids_${playlist.name}", ids) }
    }
}

fun loadPlaylists(prefs: SharedPreferences): ImmutableList<Playlist> {
    val names = prefs.getStringSet("playlist_names", emptySet()) ?: emptySet()
    return names.map { name ->
        val idsStr = prefs.getStringSet("playlist_ids_$name", emptySet()) ?: emptySet()
        val ids = idsStr.mapNotNull { it.toLongOrNull() }.toImmutableList()
        Playlist(name, ids)
    }.toImmutableList()
}

@Composable
fun GroupItem(
    name: String,
    count: Int,
    icon: ImageVector,
    onClick: () -> Unit,
    onDeleteClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
    ) {
        Row(modifier = Modifier.padding(8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                Text(text = "$count songs", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
            }
            if (onDeleteClick != null) {
                IconButton(onClick = onDeleteClick, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(18.dp)) }
            }
        }
    }
}

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

    // SAFETY FIX: Mathematical clamp to absolutely prevent Compose Slider from throwing an IllegalArgumentException
    val safeDuration = song.duration.toFloat().coerceAtLeast(1000f) // Guarantee at least a 1-second range
    val rawPosition = if (isDragging) dragPosition else playbackPosition.toFloat()
    val safePosition = rawPosition.coerceIn(0f, safeDuration) // Clamp so it can NEVER exceed duration

    Surface(modifier = Modifier.fillMaxWidth().wrapContentHeight(), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f), tonalElevation = 8.dp) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth()) {
            if (isShuffleEnabled && queueSize > 0) {
                Text(text = "Shuffle: ${shufflePosition + 1}/$queueSize", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 4.dp))
            }

            key(song.id) {
                Text(text = song.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE, repeatDelayMillis = 2000), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = formatTime(safePosition.toLong()), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                Text(text = formatTime(song.duration.toLong()), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }

            Slider(
                value = safePosition, // Using clamped position
                onValueChange = { newPosition -> isDragging = true; dragPosition = newPosition },
                onValueChangeFinished = { onSeek(dragPosition.toLong()); isDragging = false },
                valueRange = 0f..safeDuration, // Using clamped duration
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary, inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onPrevious) { Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton(onClick = onPlayPause) { Icon(imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = if (isPlaying) "Pause" else "Play", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text(text = song.artist, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Card(modifier = Modifier.size(48.dp), shape = MaterialTheme.shapes.small) {
                        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                            AsyncImage(model = song.albumArtUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                    }
                }
            }
        }
    }
}

fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

@Composable
fun SongItem(
    song: Song,
    currentList: ImmutableList<Song>,
    isFavorite: Boolean,
    onFavoriteToggle: (Long) -> Unit,
    onSongClick: (Song, ImmutableList<Song>) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onSongClick(song, currentList) },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
    ) {
        Row(modifier = Modifier.padding(8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Card(modifier = Modifier.size(36.dp), shape = MaterialTheme.shapes.small) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    AsyncImage(model = song.albumArtUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = song.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                Text(text = "${song.artist} • ${song.album}", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { onFavoriteToggle(song.id) }, modifier = Modifier.size(32.dp)) {
                Icon(imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "Toggle Favorite", tint = if (isFavorite) Color.Red.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

fun fetchSongs(context: Context): ImmutableList<Song> {
    val songs = mutableListOf<Song>()
    try {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DATA)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        context.contentResolver.query(collection, projection, selection, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                try {
                    val id = cursor.getLong(idColumn)
                    val title = cursor.getString(titleColumn) ?: "Unknown"
                    val artist = cursor.getString(artistColumn) ?: "Unknown"
                    val album = cursor.getString(albumColumn) ?: "Unknown"
                    val duration = cursor.getInt(durationColumn)
                    val albumId = cursor.getLong(albumIdColumn)
                    val path = cursor.getString(dataColumn) ?: ""
                    val folder = try { if (path.isNotEmpty()) File(path).parentFile?.name ?: "Unknown" else "Unknown" } catch (_: Exception) { "Unknown" }
                    val contentUri: Uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    val albumArtUri = try { if (albumId > 0) ContentUris.withAppendedId("content://media/external/audio/albumart".toUri(), albumId) else null } catch (_: Exception) { null }

                    songs.add(Song(id, title, artist, album, duration, contentUri, albumArtUri, folder))
                } catch (_: Exception) {}
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