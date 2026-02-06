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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.example.musicplayerdeck.data.model.Playlist
import com.example.musicplayerdeck.data.model.Song
import com.example.musicplayerdeck.data.repository.fetchSongs
import com.example.musicplayerdeck.data.repository.loadPlaylists
import com.example.musicplayerdeck.data.repository.savePlaylists
import com.example.musicplayerdeck.ui.components.MiniPlayer
import com.example.musicplayerdeck.ui.components.SwipeableSongItem
import com.example.musicplayerdeck.ui.screens.tabs.FavoritesTab
import com.example.musicplayerdeck.ui.screens.tabs.GroupedTab
import com.example.musicplayerdeck.ui.screens.tabs.PlaylistsTab
import com.example.musicplayerdeck.ui.screens.tabs.SongsTab
import com.example.musicplayerdeck.ui.theme.DarkMintGradient
import com.example.musicplayerdeck.ui.theme.MintGradient
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    onSeek: (Long) -> Unit,
    onAddToQueue: (Song) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ctx.getSharedPreferences("MusicPlayerDeckPrefs", Context.MODE_PRIVATE) }
    val snackbarHostState = remember { SnackbarHostState() }

    var songs by remember { mutableStateOf<ImmutableList<Song>>(persistentListOf()) }
    var isLoading by remember { mutableStateOf(false) }
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

    // Global search
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
        if (granted) {
            isLoading = true
            scope.launch {
                songs = withContext(Dispatchers.IO) { fetchSongs(ctx) }
                prefs.edit { putBoolean("songs_loaded", true) }
                isLoading = false
            }
        }
    }

    val onFindSongs: () -> Unit = {
        if (hasPermission) {
            isLoading = true
            scope.launch {
                songs = withContext(Dispatchers.IO) { fetchSongs(ctx) }
                prefs.edit { putBoolean("songs_loaded", true) }
                isLoading = false
            }
        } else {
            val permsToReq = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            launcher.launch(permsToReq)
        }
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Songs", "Playlists", "Folder", "Favorites", "Album", "Artist")
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var selectedAlbum by remember { mutableStateOf<String?>(null) }
    var selectedArtist by remember { mutableStateOf<String?>(null) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }

    val folders: ImmutableList<Pair<String, Int>> by remember {
        derivedStateOf {
            songs.groupBy { it.folder }
                .mapValues { entry -> entry.value.size }
                .toList()
                .sortedBy { it.first }
                .toImmutableList()
        }
    }
    val albums: ImmutableList<Pair<String, Int>> by remember {
        derivedStateOf {
            songs.groupBy { it.album }
                .mapValues { entry -> entry.value.size }
                .toList()
                .sortedBy { it.first }
                .toImmutableList()
        }
    }
    val artists: ImmutableList<Pair<String, Int>> by remember {
        derivedStateOf {
            songs.groupBy { it.artist }
                .mapValues { entry -> entry.value.size }
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
            songs = withContext(Dispatchers.IO) { fetchSongs(ctx) }
            isLoading = false
        }
    }

    val gradient = if (isSystemInDarkTheme()) DarkMintGradient else MintGradient

    // Full Now Playing overlay
    AnimatedVisibility(
        visible = isNowPlayingOpen && currentSong != null,
        enter = slideInVertically(initialOffsetY = { fullHeight -> fullHeight }),
        exit = slideOutVertically(targetOffsetY = { fullHeight -> fullHeight })
    ) {
        val nowSong = currentSong
        if (nowSong != null) {
            NowPlayingScreen(
                song = nowSong,
                isPlaying = isPlaying,
                isShuffleEnabled = isShuffleEnabled,
                playbackPositionProvider = playbackPositionProvider,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onSeek = onSeek,
                onDismiss = { isNowPlayingOpen = false }
            )
        }
    }

    if (isCreatingPlaylist) {
        CreatePlaylistScreen(
            allSongs = songs,
            folders = folders,
            onDismiss = { isCreatingPlaylist = false },
            onSave = { name, ids ->
                val newPlaylist = Playlist(name, ids.toList().toImmutableList())
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
                    SearchBar(
                        inputField = {
                            SearchBarDefaults.InputField(
                                query = searchQuery,
                                onQueryChange = { searchQuery = it },
                                onSearch = {},
                                expanded = false,
                                onExpandedChange = {},
                                placeholder = { Text("Search songs, artists, albums...") },
                                leadingIcon = {
                                    IconButton(onClick = {
                                        isSearchActive = false
                                        searchQuery = ""
                                    }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                                    }
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Close, "Clear")
                                        }
                                    }
                                }
                            )
                        },
                        expanded = false,
                        onExpandedChange = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    ) {}
                } else {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                "Music Player Deck",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        },
                        actions = {
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(
                                    Icons.Default.Search,
                                    "Search",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                }
            },
            bottomBar = {
                val song = currentSong
                if (song != null) {
                    MiniPlayer(
                        song = song,
                        isPlaying = isPlaying,
                        playbackPositionProvider = playbackPositionProvider,
                        onPlayPause = onPlayPause,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        onSeek = onSeek,
                        onTap = { isNowPlayingOpen = true }
                    )
                }
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(gradient)
                    .padding(innerPadding)
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isSearchActive && debouncedSearch.isNotBlank()) {
                        // Search results view
                        Spacer(Modifier.height(8.dp))
                        if (searchResults.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.SearchOff, null, Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        "No results for \"$debouncedSearch\"",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        } else {
                            Text(
                                "${searchResults.size} results",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            LazyColumn(
                                Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
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
                                        onAddToQueue = { qSong ->
                                            onAddToQueue(qSong)
                                            scope.launch { snackbarHostState.showSnackbar("Added to queue") }
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        // Normal tab content
                        Spacer(Modifier.height(8.dp))
                        ScrollableTabRow(
                            selectedTabIndex = selectedTabIndex,
                            edgePadding = 0.dp,
                            containerColor = Color.Transparent,
                            divider = {},
                            indicator = { tp ->
                                TabRowDefaults.SecondaryIndicator(
                                    Modifier.tabIndicatorOffset(tp[selectedTabIndex]),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        ) {
                            tabs.forEachIndexed { i, t ->
                                Tab(
                                    selected = selectedTabIndex == i,
                                    onClick = { selectedTabIndex = i },
                                    text = {
                                        Text(
                                            t,
                                            color = if (selectedTabIndex == i) MaterialTheme.colorScheme.onBackground
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = if (selectedTabIndex == i) FontWeight.Bold
                                            else FontWeight.Medium
                                        )
                                    }
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Box(Modifier.weight(1f)) {
                            when (selectedTabIndex) {
                                0 -> SongsTab(
                                    songs, isLoading, isShuffleEnabled, currentSong, favoriteIds,
                                    onShuffleToggle, onReshuffle, toggleFav, onSongSelected,
                                    onFindSongs, onAddToQueue, snackbarHostState, scope
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
                                    onAddToQueue, snackbarHostState, scope
                                )
                                2 -> GroupedTab(
                                    folders, isLoading, Icons.Default.Folder, selectedFolder,
                                    { selectedFolder = it }, { selectedFolder = null }, songs,
                                    { it.folder == selectedFolder }, favoriteIds, isShuffleEnabled,
                                    onShuffleToggle, onReshuffle, toggleFav, onSongSelected,
                                    onFindSongs, currentSong, onAddToQueue, snackbarHostState, scope
                                )
                                3 -> FavoritesTab(
                                    songs, favoriteIds, isShuffleEnabled, currentSong,
                                    onShuffleToggle, onReshuffle, toggleFav, onSongSelected,
                                    onAddToQueue, snackbarHostState, scope
                                )
                                4 -> GroupedTab(
                                    albums, isLoading, Icons.Default.Album, selectedAlbum,
                                    { selectedAlbum = it }, { selectedAlbum = null }, songs,
                                    { it.album == selectedAlbum }, favoriteIds, isShuffleEnabled,
                                    onShuffleToggle, onReshuffle, toggleFav, onSongSelected,
                                    onFindSongs, currentSong, onAddToQueue, snackbarHostState, scope
                                )
                                5 -> GroupedTab(
                                    artists, isLoading, Icons.Default.Person, selectedArtist,
                                    { selectedArtist = it }, { selectedArtist = null }, songs,
                                    { it.artist == selectedArtist }, favoriteIds, isShuffleEnabled,
                                    onShuffleToggle, onReshuffle, toggleFav, onSongSelected,
                                    onFindSongs, currentSong, onAddToQueue, snackbarHostState, scope
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}