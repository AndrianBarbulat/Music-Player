@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.musicplayerdeck.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.example.musicplayerdeck.data.model.Playlist
import com.example.musicplayerdeck.data.model.Song
import com.example.musicplayerdeck.data.repository.loadPlayCounts
import com.example.musicplayerdeck.data.repository.loadPlaylists
import com.example.musicplayerdeck.data.repository.loadRecentlyPlayed
import com.example.musicplayerdeck.data.repository.savePlaylists
import com.example.musicplayerdeck.data.repository.syncPlaylistsWithFolders
import com.example.musicplayerdeck.ui.components.InlineSearchBar
import com.example.musicplayerdeck.ui.components.MiniPlayer
import com.example.musicplayerdeck.ui.components.SongOptionsSheet
import com.example.musicplayerdeck.ui.components.SwipeableSongItem
import com.example.musicplayerdeck.ui.components.TabStrip
import com.example.musicplayerdeck.ui.screens.tabs.FavoritesTab
import com.example.musicplayerdeck.ui.screens.tabs.GroupedTab
import com.example.musicplayerdeck.ui.screens.tabs.PlaylistsTab
import com.example.musicplayerdeck.ui.screens.tabs.SongsTab
import com.example.musicplayerdeck.ui.theme.AppBackground
import com.example.musicplayerdeck.ui.theme.TealPrimary
import com.example.musicplayerdeck.ui.theme.TextMuted
import com.example.musicplayerdeck.ui.theme.TextPrimary
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Top-level constant — avoids allocating a new List object on every recomposition.
private val MAIN_TABS = persistentListOf(
    "Songs", "Playlists", "Folder", "Favorites", "Album", "Artist", "Recent", "Top"
)

@Composable
fun MainScreen(
    songs: ImmutableList<Song>,
    isLoading: Boolean,
    onLoadSongs: () -> Unit,
    currentSong: Song?,
    isPlaying: Boolean,
    isShuffleEnabled: Boolean,
    isRepeatOne: Boolean,
    onRepeatToggle: () -> Unit,
    playbackPositionProvider: () -> Long,
    shufflePosition: Int,
    queueSize: Int,
    activeQueue: ImmutableList<Song>,
    onSkipToQueueIndex: (Int) -> Unit,
    onSongSelected: (Song, ImmutableList<Song>) -> Unit,
    onShuffleToggle: () -> Unit,
    onReshuffle: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onAddToQueue: (Song) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ctx.getSharedPreferences("MusicPlayerDeckPrefs", Context.MODE_PRIVATE) }
    val snackbarHostState = remember { SnackbarHostState() }

    var hasPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
        )
    }
    var favoriteIds by remember {
        mutableStateOf(prefs.getStringSet("favorite_ids", emptySet()) ?: emptySet())
    }
    var playlists by remember { mutableStateOf(loadPlaylists(prefs)) }
    var isCreatingPlaylist by remember { mutableStateOf(false) }
    var isNowPlayingOpen by remember { mutableStateOf(false) }
    var songDetailsTarget by remember { mutableStateOf<Song?>(null) }
    var bottomSheetSong by remember { mutableStateOf<Song?>(null) }

    var playCounts by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    var recentlyPlayedIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    var showBatchPlaylistPicker by remember { mutableStateOf<Set<Long>?>(null) }

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var debouncedSearch by remember { mutableStateOf("") }

    LaunchedEffect(searchQuery) {
        delay(300)
        debouncedSearch = searchQuery
    }

    val searchResults: ImmutableList<Song> by remember(songs, debouncedSearch) {
        derivedStateOf {
            if (debouncedSearch.isBlank()) {
                persistentListOf()
            } else {
                songs.filter { song ->
                    song.title.contains(debouncedSearch, ignoreCase = true) ||
                            song.artist.contains(debouncedSearch, ignoreCase = true) ||
                            song.album.contains(debouncedSearch, ignoreCase = true)
                }.toImmutableList()
            }
        }
    }

    val toggleFav: (Long) -> Unit = remember {
        { id: Long ->
            val nf = favoriteIds.toMutableSet()
            val s = id.toString()
            if (nf.contains(s)) nf.remove(s) else nf.add(s)
            favoriteIds = nf
            prefs.edit { putStringSet("favorite_ids", nf) }
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.READ_MEDIA_AUDIO]
            ?: perms[Manifest.permission.READ_EXTERNAL_STORAGE]
            ?: false
        hasPermission = granted
        if (granted) onLoadSongs()
    }

    val onFindSongs: () -> Unit = remember(hasPermission) {
        if (hasPermission) {
            { onLoadSongs() }
        } else {
            val permsToReq = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            { launcher.launch(permsToReq) }
        }
    }

    var isRefreshPending by remember { mutableStateOf(false) }
    val onRefreshSongs: () -> Unit = remember(hasPermission) {
        { if (hasPermission) { isRefreshPending = true; onLoadSongs() } }
    }
    val onBatchAdd: (Set<Long>) -> Unit = remember { { ids -> showBatchPlaylistPicker = ids } }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var selectedAlbum by remember { mutableStateOf<String?>(null) }
    var selectedArtist by remember { mutableStateOf<String?>(null) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }

    val folders: ImmutableList<Pair<String, Int>> by remember(songs) {
        derivedStateOf {
            songs.groupBy { it.folder }
                .mapValues { e -> e.value.size }
                .toList().sortedBy { it.first }.toImmutableList()
        }
    }
    val albums: ImmutableList<Pair<String, Int>> by remember(songs) {
        derivedStateOf {
            songs.groupBy { it.album }
                .mapValues { e -> e.value.size }
                .toList().sortedBy { it.first }.toImmutableList()
        }
    }
    val artists: ImmutableList<Pair<String, Int>> by remember(songs) {
        derivedStateOf {
            songs.groupBy { it.artist }
                .mapValues { e -> e.value.size }
                .toList().sortedBy { it.first }.toImmutableList()
        }
    }

    // ImmutableList so TabStrip can skip recomposition when counts haven't changed.
    val tabCounts: ImmutableList<Int> by remember(songs, playlists, folders, albums, artists, favoriteIds, recentlyPlayedIds, playCounts) {
        derivedStateOf {
            persistentListOf(
                songs.size,
                playlists.size,
                folders.size,
                songs.count { favoriteIds.contains(it.id.toString()) },
                albums.size,
                artists.size,
                recentlyPlayedIds.count { id -> songs.any { it.id == id } },
                songs.count { (playCounts[it.id] ?: 0) > 0 }
            )
        }
    }

    LaunchedEffect(selectedTabIndex) {
        selectedFolder = null; selectedAlbum = null
        selectedArtist = null; selectedPlaylist = null
    }

    LaunchedEffect(songs, currentSong) {
        if (songs.isNotEmpty()) {
            playCounts = withContext(Dispatchers.IO) { loadPlayCounts(prefs) }
            recentlyPlayedIds = withContext(Dispatchers.IO) { loadRecentlyPlayed(prefs).map { it.songId } }
        }
    }

    LaunchedEffect(isLoading) {
        if (!isLoading && isRefreshPending && songs.isNotEmpty()) {
            isRefreshPending = false
            val (synced, addedCount) = syncPlaylistsWithFolders(playlists, songs)
            if (addedCount > 0) { playlists = synced; savePlaylists(prefs, playlists) }
            val syncMsg = if (addedCount > 0) " • $addedCount songs auto-added to playlists" else ""
            snackbarHostState.showSnackbar("Library refreshed — ${songs.size} songs$syncMsg")
        }
    }

    // ── Loading gate ─────────────────────────────────────────────────────────
    // While the initial library scan is in progress, render only a spinner.
    // This prevents Compose from measuring and laying out the full Scaffold +
    // tabs before any data is available, eliminating the massive frame skips
    // that come from JIT-compiling 4–7 MB of lambda code on the first frame.
    if (isLoading && songs.isEmpty()) {
        Box(
            Modifier.fillMaxSize().background(AppBackground),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = TealPrimary, strokeWidth = 2.dp)
        }
        return
    }

    // ── Dialogs / sheets (all rendered via composition, not navigation) ───────
    val batchIds = showBatchPlaylistPicker
    if (batchIds != null && playlists.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showBatchPlaylistPicker = null },
            title = { Text("Add ${batchIds.size} songs to...", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn {
                    items(items = playlists) { pl ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val newIds = (pl.songIds + batchIds.toList()).distinct().toImmutableList()
                                    playlists = playlists.map {
                                        if (it.name == pl.name) pl.copy(songIds = newIds) else it
                                    }.toImmutableList()
                                    savePlaylists(prefs, playlists)
                                    showBatchPlaylistPicker = null
                                    scope.launch { snackbarHostState.showSnackbar("Added to ${pl.name}") }
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = TealPrimary)
                            Spacer(Modifier.width(16.dp))
                            Text(pl.name, fontWeight = FontWeight.Medium, color = TextPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBatchPlaylistPicker = null }) {
                    Text("Cancel", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    val sheetSong = bottomSheetSong
    if (sheetSong != null) {
        SongOptionsSheet(
            song = sheetSong,
            onDismiss = { bottomSheetSong = null },
            onSongInfo = { songDetailsTarget = sheetSong; bottomSheetSong = null },
            onAddToPlaylist = { showBatchPlaylistPicker = setOf(sheetSong.id); bottomSheetSong = null }
        )
    }

    if (isCreatingPlaylist) {
        CreatePlaylistScreen(
            allSongs = songs,
            folders = folders,
            onDismiss = { isCreatingPlaylist = false },
            onSave = { name, ids, sourceFolders ->
                val newPlaylist = Playlist(
                    name = name,
                    songIds = ids.toList().toImmutableList(),
                    sourceFolders = sourceFolders.toList().toImmutableList()
                )
                playlists = (playlists + newPlaylist).toImmutableList()
                savePlaylists(prefs, playlists)
                isCreatingPlaylist = false
            }
        )
    } else if (!isNowPlayingOpen || currentSong == null) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (isSearchActive) {
                    InlineSearchBar(
                        searchQuery = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onDismiss = { isSearchActive = false; searchQuery = "" },
                    )
                } else {
                    TopAppBar(
                        title = { Text("Music Player Deck", fontWeight = FontWeight.Bold, color = TextPrimary) },
                        actions = {
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(Icons.Default.Search, "Search", tint = TextMuted)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground)
                    )
                }
            },
            bottomBar = {
                val song = currentSong
                if (song != null) {
                    Column(Modifier.navigationBarsPadding()) {
                        MiniPlayer(
                            song = song,
                            isPlaying = isPlaying,
                            playbackPositionProvider = playbackPositionProvider,
                            onPlayPause = onPlayPause,
                            onNext = onNext,
                            onPrevious = onPrevious,
                            onSeek = onSeek,
                            onTap = {
                                bottomSheetSong = null
                                songDetailsTarget = null
                                isNowPlayingOpen = true
                            }
                        )
                    }
                }
            },
            containerColor = AppBackground
        ) { innerPadding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(AppBackground)
                    .padding(innerPadding)
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isSearchActive && debouncedSearch.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        if (searchResults.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.SearchOff, null, Modifier.size(64.dp),
                                        tint = TextMuted.copy(alpha = 0.5f)
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Text("No results for \"$debouncedSearch\"", color = TextMuted, fontSize = 16.sp)
                                }
                            }
                        } else {
                            Text(
                                "${searchResults.size} results",
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                color = TextMuted,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            LazyColumn(
                                Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(0.dp),
                                contentPadding = PaddingValues(bottom = 24.dp)
                            ) {
                                items(items = searchResults, key = { it.id }) { song ->
                                    SwipeableSongItem(
                                        song = song,
                                        isPlaying = currentSong?.id == song.id,
                                        currentList = searchResults,
                                        isFavorite = favoriteIds.contains(song.id.toString()),
                                        onFavoriteToggle = toggleFav,
                                        onSongClick = onSongSelected,
                                        onMoreClick = { bottomSheetSong = song }
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(Modifier.height(4.dp))
                        TabStrip(
                            tabs = MAIN_TABS,
                            tabCounts = tabCounts,
                            selectedIndex = selectedTabIndex,
                            onTabSelected = { selectedTabIndex = it },
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.weight(1f)) {
                            when (selectedTabIndex) {
                                0 -> SongsTab(
                                    songs, isLoading, isShuffleEnabled, currentSong, favoriteIds,
                                    onShuffleToggle, onReshuffle, toggleFav, onSongSelected,
                                    onFindSongs,
                                    playCounts = playCounts,
                                    onBatchAddToPlaylist = onBatchAdd,
                                    onSongMoreClick = { bottomSheetSong = it },
                                    onRefreshLibrary = onRefreshSongs
                                )
                                1 -> PlaylistsTab(
                                    playlists, songs, currentSong, selectedPlaylist,
                                    { selectedPlaylist = it }, { selectedPlaylist = null },
                                    { isCreatingPlaylist = true }, favoriteIds, isShuffleEnabled,
                                    onShuffleToggle, onReshuffle, toggleFav, onSongSelected,
                                    { p ->
                                        playlists = playlists.filter { it.name != p.name }.toImmutableList()
                                        savePlaylists(prefs, playlists)
                                    },
                                    onUpdatePlaylist = { updated ->
                                        playlists = playlists.map {
                                            if (it.name == updated.name) updated else it
                                        }.toImmutableList()
                                        savePlaylists(prefs, playlists)
                                    },
                                    onRenamePlaylist = { playlist, newName ->
                                        playlists = playlists.map {
                                            if (it.name == playlist.name) it.copy(name = newName) else it
                                        }.toImmutableList()
                                        savePlaylists(prefs, playlists)
                                    },
                                    onSongMoreClick = { bottomSheetSong = it }
                                )
                                2 -> GroupedTab(
                                    folders, isLoading, Icons.Default.Folder, selectedFolder,
                                    { selectedFolder = it }, { selectedFolder = null }, songs,
                                    { it.folder == selectedFolder }, favoriteIds, isShuffleEnabled,
                                    onShuffleToggle, onReshuffle, toggleFav, onSongSelected,
                                    onFindSongs, currentSong,
                                    playCounts, onBatchAddToPlaylist = onBatchAdd,
                                    onSongMoreClick = { bottomSheetSong = it }
                                )
                                3 -> FavoritesTab(
                                    songs, favoriteIds, isShuffleEnabled, currentSong,
                                    onShuffleToggle, onReshuffle, toggleFav, onSongSelected,
                                    playCounts,
                                    onBatchAddToPlaylist = onBatchAdd,
                                    onSongMoreClick = { bottomSheetSong = it }
                                )
                                4 -> GroupedTab(
                                    albums, isLoading, Icons.Default.Album, selectedAlbum,
                                    { selectedAlbum = it }, { selectedAlbum = null }, songs,
                                    { it.album == selectedAlbum }, favoriteIds, isShuffleEnabled,
                                    onShuffleToggle, onReshuffle, toggleFav, onSongSelected,
                                    onFindSongs, currentSong,
                                    playCounts, onBatchAddToPlaylist = onBatchAdd,
                                    onSongMoreClick = { bottomSheetSong = it }
                                )
                                5 -> GroupedTab(
                                    artists, isLoading, Icons.Default.Person, selectedArtist,
                                    { selectedArtist = it }, { selectedArtist = null }, songs,
                                    { it.artist == selectedArtist }, favoriteIds, isShuffleEnabled,
                                    onShuffleToggle, onReshuffle, toggleFav, onSongSelected,
                                    onFindSongs, currentSong,
                                    playCounts, onBatchAddToPlaylist = onBatchAdd,
                                    onSongMoreClick = { bottomSheetSong = it }
                                )
                                6 -> {
                                    val recentSongs = remember(songs, recentlyPlayedIds) {
                                        recentlyPlayedIds.mapNotNull { id ->
                                            songs.find { it.id == id }
                                        }.toImmutableList()
                                    }
                                    SongsTab(
                                        recentSongs, isLoading, isShuffleEnabled, currentSong,
                                        favoriteIds, onShuffleToggle, onReshuffle, toggleFav,
                                        onSongSelected, {},
                                        playCounts = playCounts,
                                        onBatchAddToPlaylist = onBatchAdd,
                                        onSongMoreClick = { bottomSheetSong = it }
                                    )
                                }
                                7 -> {
                                    val topSongs = remember(songs, playCounts) {
                                        songs.filter { (playCounts[it.id] ?: 0) > 0 }
                                            .sortedByDescending { playCounts[it.id] ?: 0 }
                                            .toImmutableList()
                                    }
                                    SongsTab(
                                        topSongs, isLoading, isShuffleEnabled, currentSong,
                                        favoriteIds, onShuffleToggle, onReshuffle, toggleFav,
                                        onSongSelected, {},
                                        playCounts = playCounts,
                                        onBatchAddToPlaylist = onBatchAdd,
                                        onSongMoreClick = { bottomSheetSong = it }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Full Now Playing overlay — placed after Scaffold so it draws on top
    AnimatedVisibility(
        visible = isNowPlayingOpen && currentSong != null,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it })
    ) {
        val nowSong = currentSong
        if (nowSong != null) {
            NowPlayingScreen(
                song = nowSong,
                isPlaying = isPlaying,
                playbackPositionProvider = playbackPositionProvider,
                queue = activeQueue,
                onSkipToQueueIndex = onSkipToQueueIndex,
                isFavorite = nowSong.id.toString() in favoriteIds,
                onFavoriteToggle = { toggleFav(nowSong.id) },
                isRepeatOne = isRepeatOne,
                onRepeatToggle = onRepeatToggle,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onSeek = onSeek,
                onDismiss = { isNowPlayingOpen = false },
                onMoreClick = { bottomSheetSong = currentSong }
            )
        }
    }

    // Song details overlay — placed last so it draws on top of everything
    AnimatedVisibility(
        visible = songDetailsTarget != null,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it })
    ) {
        val detailsSong = songDetailsTarget
        if (detailsSong != null) {
            SongDetailsScreen(
                song = detailsSong,
                playCount = playCounts[detailsSong.id] ?: 0,
                onDismiss = { songDetailsTarget = null }
            )
        }
    }
}
