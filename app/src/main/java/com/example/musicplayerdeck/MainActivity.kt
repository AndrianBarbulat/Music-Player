@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.musicplayerdeck

import android.Manifest
import android.annotation.SuppressLint
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
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.musicplayerdeck.ui.theme.MusicPlayerDeckTheme
import com.example.musicplayerdeck.ui.theme.MintGradient
import com.example.musicplayerdeck.ui.theme.DarkMintGradient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import androidx.core.content.edit
import androidx.core.net.toUri

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

data class Playlist(
    val name: String,
    val songIds: List<Long>
)

data class MiniPlayerState(
    val position: Long,
    val isPlaying: Boolean,
    val song: Song?
)

class MainActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private var currentSong by mutableStateOf<Song?>(null)
    private var isPlaying by mutableStateOf(false)
    private var originalPlaylist by mutableStateOf<List<Song>>(emptyList())
    private var activePlaybackQueue by mutableStateOf<List<Song>>(emptyList())
    private var isShuffleEnabled by mutableStateOf(false)
    private var shuffleOrder by mutableStateOf<List<Int>>(emptyList())
    private var shufflePosition by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("MusicPlayerDeckPrefs", MODE_PRIVATE)
        isShuffleEnabled = prefs.getBoolean("shuffle_enabled", false)

        player = ExoPlayer.Builder(this).build().apply {
            shuffleModeEnabled = false
            addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    this@MainActivity.currentSong = activePlaybackQueue.find { it.id.toString() == mediaItem?.mediaId }
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    this@MainActivity.isPlaying = playing
                }
            })
        }

        enableEdgeToEdge()
        setContent {
            MusicPlayerDeckTheme {
                val miniPlayerState = remember {
                    mutableStateOf(
                        MiniPlayerState(
                            position = 0L,
                            isPlaying = isPlaying,
                            song = currentSong
                        )
                    )
                }

                LaunchedEffect(isPlaying) {
                    while (isPlaying) {
                        miniPlayerState.value = miniPlayerState.value.copy(
                            position = player?.currentPosition ?: 0L,
                            isPlaying = true,
                            song = currentSong
                        )
                        delay(500)
                    }
                }

                LaunchedEffect(currentSong) {
                    currentSong?.let {
                        miniPlayerState.value = miniPlayerState.value.copy(
                            song = it,
                            position = 0L
                        )
                    }
                }

                MainScreen(
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    isShuffleEnabled = isShuffleEnabled,
                    playbackPosition = miniPlayerState.value.position,
                    shufflePosition = shufflePosition,
                    queueSize = activePlaybackQueue.size,
                    onSongSelected = { song, playlist ->
                        playSong(song, playlist)
                    },
                    onShuffleToggle = {
                        toggleShuffleMode()
                        prefs.edit { putBoolean("shuffle_enabled", isShuffleEnabled) }
                    },
                    onReshuffle = { reshuffleQueue() },
                    onPlayPause = {
                        if (isPlaying) player?.pause() else player?.play()
                    },
                    onNext = {
                        if (isShuffleEnabled) {
                            shufflePosition = (shufflePosition + 1) % activePlaybackQueue.size
                        }
                        player?.seekToNext()
                    },
                    onPrevious = {
                        if (isShuffleEnabled) {
                            shufflePosition = (shufflePosition - 1 + activePlaybackQueue.size) % activePlaybackQueue.size
                        }
                        player?.seekToPrevious()
                    },
                    onSeek = { newPosition ->
                        player?.seekTo(newPosition)
                        miniPlayerState.value = miniPlayerState.value.copy(position = newPosition)
                    }
                )
            }
        }
    }

    private fun playSong(song: Song, playlist: List<Song>) {
        if (playlist.isEmpty()) return

        if (originalPlaylist != playlist || activePlaybackQueue.isEmpty()) {
            originalPlaylist = playlist
            activePlaybackQueue = generatePlaybackQueue(playlist, song)
            player?.setMediaItems(buildMediaItems(activePlaybackQueue))
        }

        val index = activePlaybackQueue.indexOfFirst { it.id == song.id }
        if (index != -1) {
            player?.seekTo(index, 0)
            player?.prepare()
            player?.play()
        }
    }

    private fun toggleShuffleMode() {
        if (originalPlaylist.isEmpty()) {
            isShuffleEnabled = !isShuffleEnabled
            return
        }

        val wasPlaying = isPlaying
        val currentPositionMs = player?.currentPosition ?: 0L
        val anchorSong = currentSong

        isShuffleEnabled = !isShuffleEnabled

        if (isShuffleEnabled) {
            val anchorIndex = anchorSong?.let { originalPlaylist.indexOf(it) } ?: 0
            shuffleOrder = listOf(anchorIndex) + (0 until originalPlaylist.size)
                .filter { it != anchorIndex }
                .shuffled()
            shufflePosition = 0
            activePlaybackQueue = shuffleOrder.map { originalPlaylist[it] }
        } else {
            activePlaybackQueue = originalPlaylist
        }

        player?.setMediaItems(buildMediaItems(activePlaybackQueue))
        val newIndex = activePlaybackQueue.indexOfFirst { it.id == anchorSong?.id }
        player?.seekTo(if (newIndex >= 0) newIndex else 0, currentPositionMs)
        player?.prepare()

        if (wasPlaying) player?.play()
    }

    private fun generatePlaybackQueue(playlist: List<Song>, anchorSong: Song?): List<Song> {
        if (!isShuffleEnabled) return playlist
        if (playlist.size <= 1) return playlist

        val anchorIndex = anchorSong?.let { playlist.indexOf(it) } ?: -1

        shuffleOrder = if (anchorIndex >= 0) {
            listOf(anchorIndex) + (0 until playlist.size)
                .filter { it != anchorIndex }
                .shuffled()
        } else {
            (0 until playlist.size).shuffled()
        }

        shufflePosition = 0
        return shuffleOrder.map { playlist[it] }
    }

    private fun reshuffleQueue() {
        if (!isShuffleEnabled || originalPlaylist.isEmpty()) return

        val wasPlaying = isPlaying
        val currentPositionMs = player?.currentPosition ?: 0L

        val playedIndices = shuffleOrder.take(shufflePosition + 1)
        val remainingIndices = shuffleOrder.drop(shufflePosition + 1)

        if (remainingIndices.isNotEmpty()) {
            val newRemaining = remainingIndices.shuffled()
            shuffleOrder = playedIndices + newRemaining
            activePlaybackQueue = shuffleOrder.map { originalPlaylist[it] }

            player?.setMediaItems(buildMediaItems(activePlaybackQueue))
            player?.seekTo(shufflePosition, currentPositionMs)
            player?.prepare()

            if (wasPlaying) player?.play()
        }
    }

    private fun buildMediaItems(queue: List<Song>): List<MediaItem> {
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

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
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
    playbackPosition: Long,
    shufflePosition: Int,
    queueSize: Int,
    onSongSelected: (Song, List<Song>) -> Unit,
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
    var songs by remember { mutableStateOf(emptyList<Song>()) }
    var isLoading by remember { mutableStateOf(false) }
    var hasPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    var favoriteIds by remember {
        mutableStateOf(prefs.getStringSet("favorite_ids", emptySet()) ?: emptySet())
    }

    // FIXED: Use mutableStateListOf for playlists
    val playlists = remember { mutableStateListOf<Playlist>().apply { addAll(loadPlaylists(prefs)) } }
    var isCreatingPlaylist by remember { mutableStateOf(false) }

    val toggleFavorite: (Long) -> Unit = { songId: Long ->
        val newFavorites = favoriteIds.toMutableSet()
        val idStr = songId.toString()
        if (newFavorites.contains(idStr)) {
            newFavorites.remove(idStr)
        } else {
            newFavorites.add(idStr)
        }
        favoriteIds = newFavorites
        prefs.edit { putStringSet("favorite_ids", newFavorites) }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }

    val onFindSongs: () -> Unit = {
        if (hasPermission) {
            isLoading = true
            scope.launch {
                try {
                    songs = withContext(Dispatchers.IO) {
                        fetchSongs(context)
                    }
                    prefs.edit {putBoolean("songs_loaded", true)}
                } finally {
                    isLoading = false
                }
            }
        } else {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            launcher.launch(permission)
        }
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Songs", "Favorites", "Album", "Playlists", "Artist", "Folder")

    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var selectedAlbum by remember { mutableStateOf<String?>(null) }
    var selectedArtist by remember { mutableStateOf<String?>(null) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }

    val folders by remember {
        derivedStateOf {
            if (selectedTabIndex == 5) {
                songs.groupBy { it.folder }
                    .mapValues { it.value.size }
                    .toList()
                    .sortedBy { it.first }
            } else emptyList()
        }
    }

    val albums by remember {
        derivedStateOf {
            if (selectedTabIndex == 2) {
                songs.groupBy { it.album }
                    .mapValues { it.value.size }
                    .toList()
                    .sortedBy { it.first }
            } else emptyList()
        }
    }

    val artists by remember {
        derivedStateOf {
            if (selectedTabIndex == 4) {
                songs.groupBy { it.artist }
                    .mapValues { it.value.size }
                    .toList()
                    .sortedBy { it.first }
            } else emptyList()
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
                val newPlaylist = Playlist(name, selectedIds.toList())
                playlists.add(newPlaylist)
                savePlaylists(prefs, playlists.toList())
                isCreatingPlaylist = false
            }
        )
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Music Player Deck", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            bottomBar = {
                if (currentSong != null) {
                    MiniPlayer(
                        song = currentSong!!,
                        isPlaying = isPlaying,
                        playbackPosition = playbackPosition,
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
                    Spacer(modifier = Modifier.height(16.dp))

                    ScrollableTabRow(
                        selectedTabIndex = selectedTabIndex,
                        edgePadding = 0.dp,
                        containerColor = Color.Transparent,
                        divider = {},
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
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
                                        title,
                                        color = if (selectedTabIndex == index) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                        fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        when (selectedTabIndex) {
                            0 -> {
                                Column {
                                    if (songs.isNotEmpty()) {
                                        EnhancedShuffleToggle(
                                            isShuffleEnabled = isShuffleEnabled,
                                            onShuffleToggle = onShuffleToggle,
                                            onReshuffle = onReshuffle
                                        )
                                    }
                                    if (isLoading) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)
                                        }
                                    } else if (songs.isEmpty()) {
                                        FindSongsCTA(isLoading, onFindSongs)
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                            contentPadding = PaddingValues(bottom = 16.dp)
                                        ) {
                                            items(
                                                items = songs,
                                                key = { it.id }
                                            ) { song ->
                                                SongItem(
                                                    song = song,
                                                    isFavorite = favoriteIds.contains(song.id.toString()),
                                                    onFavoriteToggle = { toggleFavorite(song.id) },
                                                    onClick = { onSongSelected(song, songs) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            1 -> {
                                Column {
                                    val favoriteSongs = remember(songs, favoriteIds) {
                                        songs.filter { favoriteIds.contains(it.id.toString()) }
                                    }
                                    if (favoriteSongs.isNotEmpty()) {
                                        EnhancedShuffleToggle(
                                            isShuffleEnabled = isShuffleEnabled,
                                            onShuffleToggle = onShuffleToggle,
                                            onReshuffle = onReshuffle
                                        )
                                    }
                                    if (favoriteSongs.isEmpty()) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text(
                                                "No favorites yet.",
                                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                            contentPadding = PaddingValues(bottom = 16.dp)
                                        ) {
                                            items(
                                                items = favoriteSongs,
                                                key = { it.id }
                                            ) { song ->
                                                SongItem(
                                                    song = song,
                                                    isFavorite = true,
                                                    onFavoriteToggle = { toggleFavorite(song.id) },
                                                    onClick = { onSongSelected(song, favoriteSongs) }
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
                                    playlists = playlists.toList(), // FIXED: Convert to List for composable
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
                                        // FIXED: Use removeAll instead of filter + assignment
                                        playlists.removeAll { it.name == playlist.name }
                                        savePlaylists(prefs, playlists.toList())
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
                "Your library is empty",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onFindSongs,
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
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
    items: List<Pair<String, Int>>,
    isLoading: Boolean,
    icon: ImageVector,
    selectedItem: String?,
    onItemClick: (String) -> Unit,
    onBackClick: () -> Unit,
    songs: List<Song>,
    filterPredicate: (Song) -> Boolean,
    favoriteIds: Set<String>,
    isShuffleEnabled: Boolean,
    onShuffleToggle: () -> Unit,
    onReshuffle: () -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onSongSelected: (Song, List<Song>) -> Unit,
    onFindSongs: () -> Unit
) {
    if (selectedItem == null) {
        Column {
            if (items.isNotEmpty()) {
                EnhancedShuffleToggle(
                    isShuffleEnabled = isShuffleEnabled,
                    onShuffleToggle = onShuffleToggle,
                    onReshuffle = onReshuffle
                )
            }
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)
                }
            } else if (items.isEmpty()) {
                FindSongsCTA(isLoading, onFindSongs)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(items) { (name, count) ->
                        GroupItem(name = name, count = count, icon = icon, onClick = { onItemClick(name) })
                    }
                }
            }
        }
    } else {
        val groupSongs = remember(songs, selectedItem) { songs.filter(filterPredicate) }
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).clickable { onBackClick() }.padding(4.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = selectedItem,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                if (isShuffleEnabled) {
                    IconButton(onClick = onReshuffle, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Reshuffle",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                IconButton(onClick = onShuffleToggle, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(
                    items = groupSongs,
                    key = { it.id }
                ) { song ->
                    SongItem(
                        song = song,
                        isFavorite = favoriteIds.contains(song.id.toString()),
                        onFavoriteToggle = { onToggleFavorite(song.id) },
                        onClick = { onSongSelected(song, groupSongs) }
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistsTab(
    playlists: List<Playlist>,
    songs: List<Song>,
    selectedPlaylist: Playlist?,
    onPlaylistClick: (Playlist) -> Unit,
    onBackClick: () -> Unit,
    onCreateClick: () -> Unit,
    favoriteIds: Set<String>,
    isShuffleEnabled: Boolean,
    onShuffleToggle: () -> Unit,
    onReshuffle: () -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onSongSelected: (Song, List<Song>) -> Unit,
    onDeletePlaylist: (Playlist) -> Unit
) {
    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }
    if (playlistToDelete != null) {
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text("Delete Playlist") },
            text = { Text("Are you sure you want to delete this playlist?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        playlistToDelete?.let { onDeletePlaylist(it) }
                        playlistToDelete = null
                    }
                ) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (selectedPlaylist == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onCreateClick,
                    modifier = Modifier.weight(1f).padding(bottom = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create Playlist")
                }
                if (playlists.isNotEmpty()) {
                    if (isShuffleEnabled) {
                        IconButton(onClick = onReshuffle, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp).size(32.dp)) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Reshuffle",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    IconButton(onClick = onShuffleToggle, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp).size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (isShuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (playlists.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("No playlists yet.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
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
        val playlistSongs = remember(songs, selectedPlaylist) {
            selectedPlaylist.songIds.mapNotNull { id -> songs.find { it.id == id } }
        }
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).clickable { onBackClick() }.padding(4.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = selectedPlaylist.name,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                if (isShuffleEnabled) {
                    IconButton(onClick = onReshuffle, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Reshuffle",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                IconButton(onClick = onShuffleToggle, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(
                    items = playlistSongs,
                    key = { it.id }
                ) { song ->
                    SongItem(
                        song = song,
                        isFavorite = favoriteIds.contains(song.id.toString()),
                        onFavoriteToggle = { onToggleFavorite(song.id) },
                        onClick = { onSongSelected(song, playlistSongs) }
                    )
                }
            }
        }
    }
}

@Composable
fun CreatePlaylistScreen(
    allSongs: List<Song>,
    folders: List<Pair<String, Int>>,
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
        if (debouncedQuery.isBlank()) {
            allSongs
        } else {
            allSongs.filter {
                it.title.contains(debouncedQuery, ignoreCase = true) ||
                        it.artist.contains(debouncedQuery, ignoreCase = true)
            }
        }
    }

    val gradient = if (isSystemInDarkTheme()) DarkMintGradient else MintGradient

    BackHandler { onDismiss() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent
    ) {
        Box(modifier = Modifier.fillMaxSize().background(gradient)) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    Text("Create Playlist", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                }

                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text("Playlist Name") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search Songs") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )

                Text("Selection Options", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onBackground)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    var showFolderDialog by remember { mutableStateOf(false) }
                    Button(
                        onClick = { showFolderDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                    ) {
                        Text("Select Folder")
                    }

                    if (showFolderDialog) {
                        AlertDialog(
                            onDismissRequest = { showFolderDialog = false },
                            title = { Text("Select Folder") },
                            text = {
                                LazyColumn {
                                    items(folders) { (folderName, count) ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                val folderSongs = allSongs.filter { it.folder == folderName }
                                                selectedSongs = selectedSongs + folderSongs.map { it.id }.toSet()
                                                showFolderDialog = false
                                            }.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Folder, contentDescription = null)
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text("$folderName ($count songs)")
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showFolderDialog = false }) { Text("Cancel") }
                            }
                        )
                    }

                    Button(
                        onClick = { selectedSongs = emptySet() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f))
                    ) {
                        Text("Clear All")
                    }
                }

                Text("Songs (${selectedSongs.size} selected)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filteredSongs) { song ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                selectedSongs = if (selectedSongs.contains(song.id)) {
                                    selectedSongs - song.id
                                } else {
                                    selectedSongs + song.id
                                }
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedSongs.contains(song.id),
                                onCheckedChange = { checked ->
                                    selectedSongs = if (checked == true) {
                                        selectedSongs + song.id
                                    } else {
                                        selectedSongs - song.id
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(song.title, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
                                Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                            }
                        }
                    }
                }

                Button(
                    onClick = { if (playlistName.isNotBlank()) onSave(playlistName, selectedSongs) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    enabled = playlistName.isNotBlank() && selectedSongs.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Save Playlist")
                }
            }
        }
    }
}

fun savePlaylists(prefs: SharedPreferences, playlists: List<Playlist>) {
    val names = playlists.map { it.name }.toSet()
    prefs.edit().putStringSet("playlist_names", names).apply()
    for (playlist in playlists) {
        val ids = playlist.songIds.map { it.toString() }.toSet()
        prefs.edit {putStringSet("playlist_ids_${playlist.name}", ids)}
    }
}

fun loadPlaylists(prefs: SharedPreferences): List<Playlist> {
    val names = prefs.getStringSet("playlist_names", emptySet()) ?: emptySet()
    return names.map { name ->
        val idsStr = prefs.getStringSet("playlist_ids_$name", emptySet()) ?: emptySet()
        val ids = idsStr.mapNotNull { it.toLongOrNull() }
        Playlist(name, ids)
    }
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
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$count songs",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            if (onDeleteClick != null) {
                IconButton(onClick = onDeleteClick, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    playbackPosition: Long,
    isShuffleEnabled: Boolean,
    shufflePosition: Int,
    queueSize: Int,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth()
        ) {
            if (isShuffleEnabled && queueSize > 0) {
                Text(
                    text = "Shuffle: ${shufflePosition + 1}/$queueSize",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            key(song.id) {
                Text(
                    text = song.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee(
                            iterations = Int.MAX_VALUE,
                            repeatDelayMillis = 2000
                        ),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(if (isDragging) dragPosition.toLong() else playbackPosition),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Text(
                    text = formatTime(song.duration.toLong()),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Slider(
                value = if (isDragging) dragPosition else playbackPosition.toFloat(),
                onValueChange = { newPosition ->
                    isDragging = true
                    dragPosition = newPosition
                },
                onValueChangeFinished = {
                    onSeek(dragPosition.toLong())
                    isDragging = false
                },
                valueRange = 0f..song.duration.toFloat(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = onPrevious) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onPlayPause) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onNext) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = song.artist,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Card(
                        modifier = Modifier.size(48.dp),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(song.albumArtUri)
                                    .crossfade(true)
                                    .size(96)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
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
    isFavorite: Boolean,
    onFavoriteToggle: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                modifier = Modifier.size(36.dp),
                shape = MaterialTheme.shapes.small
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(song.albumArtUri)
                            .crossfade(true)
                            .size(72)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${song.artist} • ${song.album}",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onFavoriteToggle, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Toggle Favorite",
                    tint = if (isFavorite) Color.Red.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

fun fetchSongs(context: Context): List<Song> {
    val songs = mutableListOf<Song>()

    try {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATA
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            null
        )?.use { cursor ->
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

                    val folder = try {
                        if (path.isNotEmpty()) File(path).parentFile?.name ?: "Unknown" else "Unknown"
                    } catch (_: Exception) {
                        "Unknown"
                    }

                    val contentUri: Uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    val albumArtUri = try {
                        if (albumId > 0) {
                            ContentUris.withAppendedId(
                                "content://media/external/audio/albumart".toUri(),
                                albumId
                            )
                        } else null
                    } catch (_: Exception) {
                        null
                    }

                    songs.add(Song(id, title, artist, album, duration, contentUri, albumArtUri, folder))
                } catch (_: Exception) {
                    // Skip problematic song
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return emptyList()
    }

    return songs
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    MusicPlayerDeckTheme {
        MainScreen(
            currentSong = null,
            isPlaying = false,
            isShuffleEnabled = false,
            playbackPosition = 0L,
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