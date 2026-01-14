@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.shufflewiseplayer

import android.Manifest
import android.content.ContentUris
import android.content.Context
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.example.shufflewiseplayer.ui.theme.ShuffleWisePlayerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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

class MainActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private var currentSong by mutableStateOf<Song?>(null)
    private var isPlaying by mutableStateOf(false)
    private var fullPlaylist by mutableStateOf<List<Song>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        player = ExoPlayer.Builder(this).build().apply {
            addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    this@MainActivity.currentSong = fullPlaylist.find { it.id.toString() == mediaItem?.mediaId }
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    this@MainActivity.isPlaying = playing
                }
            })
        }

        enableEdgeToEdge()
        setContent {
            ShuffleWisePlayerTheme {
                var showSplash by remember { mutableStateOf(true) }

                if (showSplash) {
                    SplashScreen(onTimeout = { showSplash = false })
                } else {
                    MainScreen(
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        onSongSelected = { song, playlist ->
                            playSong(song, playlist)
                        },
                        onPlayPause = {
                            if (isPlaying) player?.pause() else player?.play()
                        },
                        onNext = { player?.seekToNext() },
                        onPrevious = { player?.seekToPrevious() }
                    )
                }
            }
        }
    }

    private fun playSong(song: Song, playlist: List<Song>) {
        if (fullPlaylist != playlist) {
            fullPlaylist = playlist
            val mediaItems = playlist.map { s ->
                MediaItem.Builder()
                    .setUri(s.uri)
                    .setMediaId(s.id.toString())
                    .setMediaMetadata(MediaMetadata.Builder()
                        .setTitle(s.title)
                        .setArtist(s.artist)
                        .setArtworkUri(s.albumArtUri)
                        .build())
                    .build()
            }
            player?.setMediaItems(mediaItems)
        }

        val index = playlist.indexOf(song)
        if (index != -1) {
            player?.seekTo(index, 0)
            player?.prepare()
            player?.play()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(2000)
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Shuffle Wise Player",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "By Andrian",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun MainScreen(
    currentSong: Song?,
    isPlaying: Boolean,
    onSongSelected: (Song, List<Song>) -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("ShuffleWisePrefs", Context.MODE_PRIVATE) }
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
    var showFavoritesOnly by remember { mutableStateOf(false) }

    var playlists by remember { mutableStateOf(loadPlaylists(prefs)) }
    var isCreatingPlaylist by remember { mutableStateOf(false) }

    val toggleFavorite = { songId: Long ->
        val newFavorites = favoriteIds.toMutableSet()
        val idStr = songId.toString()
        if (newFavorites.contains(idStr)) {
            newFavorites.remove(idStr)
        } else {
            newFavorites.add(idStr)
        }
        favoriteIds = newFavorites
        prefs.edit().putStringSet("favorite_ids", newFavorites).apply()
    }

    val filteredSongs = remember(songs, favoriteIds, showFavoritesOnly) {
        if (showFavoritesOnly) {
            songs.filter { favoriteIds.contains(it.id.toString()) }
        } else {
            songs
        }
    }

    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Songs", "Album", "Playlists", "Artist", "Folder")

    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var selectedAlbum by remember { mutableStateOf<String?>(null) }
    var selectedArtist by remember { mutableStateOf<String?>(null) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }

    val folders = remember(songs) {
        songs.groupBy { it.folder }.mapValues { it.value.size }.toList().sortedBy { it.first }
    }
    val albums = remember(songs) {
        songs.groupBy { it.album }.mapValues { it.value.size }.toList().sortedBy { it.first }
    }
    val artists = remember(songs) {
        songs.groupBy { it.artist }.mapValues { it.value.size }.toList().sortedBy { it.first }
    }

    // Reset sub-navigation when switching tabs
    LaunchedEffect(selectedTabIndex) {
        selectedFolder = null
        selectedAlbum = null
        selectedArtist = null
        selectedPlaylist = null
    }

    // Auto-load songs if previously loaded and permission is granted
    LaunchedEffect(hasPermission) {
        if (hasPermission && prefs.getBoolean("songs_loaded", false)) {
            isLoading = true
            songs = withContext(Dispatchers.IO) { fetchSongs(context) }
            isLoading = false
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            isLoading = true
            scope.launch {
                songs = withContext(Dispatchers.IO) { fetchSongs(context) }
                prefs.edit().putBoolean("songs_loaded", true).apply()
                isLoading = false
            }
        }
    }

    if (isCreatingPlaylist) {
        CreatePlaylistScreen(
            allSongs = songs,
            folders = folders,
            onDismiss = { isCreatingPlaylist = false },
            onSave = { name, selectedIds ->
                val newPlaylist = Playlist(name, selectedIds.toList())
                playlists = playlists + newPlaylist
                savePlaylists(prefs, playlists)
                isCreatingPlaylist = false
            }
        )
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Shuffle Wise Player", fontWeight = FontWeight.Bold) }
                )
            },
            bottomBar = {
                if (currentSong != null) {
                    MiniPlayer(
                        song = currentSong,
                        isPlaying = isPlaying,
                        onPlayPause = onPlayPause,
                        onNext = onNext,
                        onPrevious = onPrevious
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (hasPermission) {
                                isLoading = true
                                scope.launch {
                                    songs = withContext(Dispatchers.IO) { fetchSongs(context) }
                                    prefs.edit().putBoolean("songs_loaded", true).apply()
                                    isLoading = false
                                }
                            } else {
                                val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    Manifest.permission.READ_MEDIA_AUDIO
                                } else {
                                    Manifest.permission.READ_EXTERNAL_STORAGE
                                }
                                launcher.launch(permission)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Find All Songs")
                    }

                    Button(
                        onClick = { showFavoritesOnly = !showFavoritesOnly },
                        modifier = Modifier.weight(1f),
                        colors = if (showFavoritesOnly) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary) else ButtonDefaults.buttonColors()
                    ) {
                        Icon(if (showFavoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Favorites")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                ScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    edgePadding = 0.dp,
                    containerColor = Color.Transparent,
                    divider = {}
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTabIndex) {
                        0 -> { // Songs tab
                            if (isLoading) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            } else if (filteredSongs.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        if (showFavoritesOnly) "No favorites yet." else "No songs found. Click the button above to search.",
                                        color = Color.Gray,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(bottom = 16.dp)
                                ) {
                                    items(filteredSongs) { song ->
                                        SongItem(
                                            song = song,
                                            isFavorite = favoriteIds.contains(song.id.toString()),
                                            onFavoriteToggle = { toggleFavorite(song.id) },
                                            onClick = { onSongSelected(song, filteredSongs) }
                                        )
                                    }
                                }
                            }
                        }
                        1 -> { // Album tab
                            GroupedTab(
                                title = "Albums",
                                items = albums,
                                isLoading = isLoading,
                                icon = Icons.Default.Album,
                                selectedItem = selectedAlbum,
                                onItemClick = { selectedAlbum = it },
                                onBackClick = { selectedAlbum = null },
                                songs = songs,
                                filterPredicate = { it.album == selectedAlbum },
                                favoriteIds = favoriteIds,
                                onToggleFavorite = toggleFavorite,
                                onSongSelected = onSongSelected
                            )
                        }
                        2 -> { // Playlists tab
                            PlaylistsTab(
                                playlists = playlists,
                                songs = songs,
                                selectedPlaylist = selectedPlaylist,
                                onPlaylistClick = { selectedPlaylist = it },
                                onBackClick = { selectedPlaylist = null },
                                onCreateClick = { isCreatingPlaylist = true },
                                favoriteIds = favoriteIds,
                                onToggleFavorite = toggleFavorite,
                                onSongSelected = onSongSelected
                            )
                        }
                        3 -> { // Artist tab
                            GroupedTab(
                                title = "Artists",
                                items = artists,
                                isLoading = isLoading,
                                icon = Icons.Default.Person,
                                selectedItem = selectedArtist,
                                onItemClick = { selectedArtist = it },
                                onBackClick = { selectedArtist = null },
                                songs = songs,
                                filterPredicate = { it.artist == selectedArtist },
                                favoriteIds = favoriteIds,
                                onToggleFavorite = toggleFavorite,
                                onSongSelected = onSongSelected
                            )
                        }
                        4 -> { // Folder tab
                            GroupedTab(
                                title = "Folders",
                                items = folders,
                                isLoading = isLoading,
                                icon = Icons.Default.Folder,
                                selectedItem = selectedFolder,
                                onItemClick = { selectedFolder = it },
                                onBackClick = { selectedFolder = null },
                                songs = songs,
                                filterPredicate = { it.folder == selectedFolder },
                                favoriteIds = favoriteIds,
                                onToggleFavorite = toggleFavorite,
                                onSongSelected = onSongSelected
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TabPlaceholder(name: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "$name content coming soon", color = Color.Gray)
    }
}

@Composable
fun GroupedTab(
    title: String,
    items: List<Pair<String, Int>>,
    isLoading: Boolean,
    icon: ImageVector,
    selectedItem: String?,
    onItemClick: (String) -> Unit,
    onBackClick: () -> Unit,
    songs: List<Song>,
    filterPredicate: (Song) -> Boolean,
    favoriteIds: Set<String>,
    onToggleFavorite: (Long) -> Unit,
    onSongSelected: (Song, List<Song>) -> Unit
) {
    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (selectedItem == null) {
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No $title found.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(items) { (name, count) ->
                    GroupItem(name = name, count = count, icon = icon, onClick = { onItemClick(name) })
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
                    .clickable { onBackClick() }
                    .padding(8.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = selectedItem, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(groupSongs) { song ->
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
    onToggleFavorite: (Long) -> Unit,
    onSongSelected: (Song, List<Song>) -> Unit
) {
    if (selectedPlaylist == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            Button(
                onClick = onCreateClick,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create Playlist")
            }

            if (playlists.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("No playlists yet.", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(playlists) { playlist ->
                        GroupItem(
                            name = playlist.name,
                            count = playlist.songIds.size,
                            icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                            onClick = { onPlaylistClick(playlist) }
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
                    .clickable { onBackClick() }
                    .padding(8.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = selectedPlaylist.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(playlistSongs) { song ->
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
    var selectedSongs by remember { mutableStateOf(setOf<Long>()) }

    val filteredSongs = remember(allSongs, searchQuery) {
        allSongs.filter { it.title.contains(searchQuery, ignoreCase = true) || it.artist.contains(searchQuery, ignoreCase = true) }
    }

    BackHandler { onDismiss() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Create Playlist", style = MaterialTheme.typography.headlineSmall)
            }

            OutlinedTextField(
                value = playlistName,
                onValueChange = { playlistName = it },
                label = { Text("Playlist Name") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search Songs") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )

            Text("Selection Options", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                var showFolderDialog by remember { mutableStateOf(false) }
                Button(onClick = { showFolderDialog = true }) {
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

                Button(onClick = { selectedSongs = emptySet() }) {
                    Text("Clear All")
                }
            }

            Text("Songs (${selectedSongs.size} selected)", fontWeight = FontWeight.Bold)

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
                            Text(song.title, fontWeight = FontWeight.Medium)
                            Text(song.artist, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Button(
                onClick = { if (playlistName.isNotBlank()) onSave(playlistName, selectedSongs) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                enabled = playlistName.isNotBlank() && selectedSongs.isNotEmpty()
            ) {
                Text("Save Playlist")
            }
        }
    }
}

fun savePlaylists(prefs: android.content.SharedPreferences, playlists: List<Playlist>) {
    val names = playlists.map { it.name }.toSet()
    prefs.edit().putStringSet("playlist_names", names).apply()
    for (playlist in playlists) {
        val ids = playlist.songIds.map { it.toString() }.toSet()
        prefs.edit().putStringSet("playlist_ids_${playlist.name}", ids).apply()
    }
}

fun loadPlaylists(prefs: android.content.SharedPreferences): List<Playlist> {
    val names = prefs.getStringSet("playlist_names", emptySet()) ?: emptySet()
    return names.map { name ->
        val idsStr = prefs.getStringSet("playlist_ids_$name", emptySet()) ?: emptySet()
        val ids = idsStr.mapNotNull { it.toLongOrNull() }
        Playlist(name, ids)
    }
}

@Composable
fun GroupItem(name: String, count: Int, icon: ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = name,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$count songs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            Card(
                modifier = Modifier.size(56.dp),
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
                        modifier = Modifier.size(32.dp),
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

            Spacer(modifier = Modifier.width(12.dp))

            // Song Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Controls
            IconButton(onClick = onPrevious) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous")
            }
            IconButton(onClick = onPlayPause) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play"
                )
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next")
            }
        }
    }
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
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                modifier = Modifier.size(40.dp),
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${song.artist} • ${song.album}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onFavoriteToggle) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Toggle Favorite",
                    tint = if (isFavorite) Color.Red else Color.Gray
                )
            }
        }
    }
}

fun fetchSongs(context: Context): List<Song> {
    val songs = mutableListOf<Song>()
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
        "${MediaStore.Audio.Media.TITLE} ASC"
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val title = cursor.getString(titleColumn) ?: "Unknown"
            val artist = cursor.getString(artistColumn) ?: "Unknown"
            val album = cursor.getString(albumColumn) ?: "Unknown"
            val duration = cursor.getInt(durationColumn)
            val albumId = cursor.getLong(albumIdColumn)
            val path = cursor.getString(dataColumn)
            val folder = if (path != null) File(path).parentFile?.name ?: "Unknown" else "Unknown"

            val contentUri: Uri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                id
            )

            val albumArtUri = if (albumId > 0) {
                ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    albumId
                )
            } else null

            songs.add(Song(id, title, artist, album, duration, contentUri, albumArtUri, folder))
        }
    }
    return songs
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    ShuffleWisePlayerTheme {
        MainScreen(
            currentSong = null,
            isPlaying = false,
            onSongSelected = { _, _ -> },
            onPlayPause = {},
            onNext = {},
            onPrevious = {}
        )
    }
}