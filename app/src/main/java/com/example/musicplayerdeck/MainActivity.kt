@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.musicplayerdeck

import android.Manifest
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.musicplayerdeck.ui.theme.DarkMintGradient
import com.example.musicplayerdeck.ui.theme.MintGradient
import com.example.musicplayerdeck.ui.theme.MusicPlayerDeckTheme
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// ═══════════════════════════════════════════════
// Data Classes
// ═══════════════════════════════════════════════

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

// ═══════════════════════════════════════════════
// Utilities
// ═══════════════════════════════════════════════

fun formatDuration(ms: Int): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}

fun formatDurationLong(ms: Long): String = formatDuration(ms.toInt())

fun formatTotalDuration(songs: List<Song>): String {
    val totalMs = songs.sumOf { it.duration.toLong() }
    val totalMin = totalMs / 60000
    return if (totalMin >= 60) {
        val h = totalMin / 60
        val m = totalMin % 60
        "${h}hr ${m}min"
    } else {
        "${totalMin}min"
    }
}

// ═══════════════════════════════════════════════
// Dominant Color Extraction
// ═══════════════════════════════════════════════

@Composable
fun rememberDominantColor(uri: Uri?, defaultColor: Color): Color {
    var color by remember { mutableStateOf(defaultColor) }
    val ctx = LocalContext.current

    LaunchedEffect(uri) {
        if (uri == null) {
            color = defaultColor
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                val loader = ImageLoader(ctx)
                val req = ImageRequest.Builder(ctx)
                    .data(uri)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(req)
                if (result is SuccessResult) {
                    val drawable = result.drawable
                    if (drawable is BitmapDrawable) {
                        val bmp: Bitmap = drawable.bitmap
                        val palette = androidx.palette.graphics.Palette.from(bmp).generate()
                        val swatch = palette.dominantSwatch
                        if (swatch != null) {
                            color = Color(swatch.rgb)
                        }
                    }
                }
            } catch (_: Exception) {
                color = defaultColor
            }
        }
    }
    return color
}

// ═══════════════════════════════════════════════
// Animated Equalizer
// ═══════════════════════════════════════════════

@Composable
fun AnimatedEqualizer(
    modifier: Modifier = Modifier,
    isPlaying: Boolean,
    barColor: Color = MaterialTheme.colorScheme.primary
) {
    val transition = rememberInfiniteTransition(label = "eq")

    val bar0 = transition.animateFloat(
        initialValue = 0.15f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "bar0"
    )
    val bar1 = transition.animateFloat(
        initialValue = 0.15f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 470, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "bar1"
    )
    val bar2 = transition.animateFloat(
        initialValue = 0.15f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 590, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "bar2"
    )

    val bars = listOf(bar0, bar1, bar2)

    Canvas(modifier = modifier) {
        val bw = size.width / 5f
        val gap = bw * 0.6f
        val totalW = 3 * bw + 2 * gap
        val startX = (size.width - totalW) / 2f

        bars.forEachIndexed { i, anim ->
            val h = if (isPlaying) anim.value * size.height else size.height * 0.25f
            drawRoundRect(
                color = barColor,
                topLeft = Offset(startX + i * (bw + gap), size.height - h),
                size = Size(bw, h),
                cornerRadius = CornerRadius(bw / 2f)
            )
        }
    }
}

// ═══════════════════════════════════════════════
// ViewModel
// ═══════════════════════════════════════════════

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

// ═══════════════════════════════════════════════
// Activity
// ═══════════════════════════════════════════════

class MainActivity : ComponentActivity() {
    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private val vm: MusicPlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("MusicPlayerDeckPrefs", MODE_PRIVATE)
        vm.initialize(prefs)
        enableEdgeToEdge()
        setContent {
            MusicPlayerDeckTheme {
                val onSong: (Song, ImmutableList<Song>) -> Unit = remember {
                    { s: Song, p: ImmutableList<Song> -> vm.playSong(s, p) }
                }
                val onShuffle: () -> Unit = remember { { vm.toggleShuffleMode(prefs) } }
                val onReshuffle: () -> Unit = remember { { vm.reshuffleQueue() } }
                val onPP: () -> Unit = remember { { vm.togglePlayPause() } }
                val onNext: () -> Unit = remember { { vm.playNext() } }
                val onPrev: () -> Unit = remember { { vm.playPrevious() } }
                val onSeek: (Long) -> Unit = remember { { pos: Long -> vm.seekTo(pos) } }
                val onQueue: (Song) -> Unit = remember { { s: Song -> vm.addToQueue(s) } }

                MainScreen(
                    currentSong = vm.currentSong,
                    isPlaying = vm.isPlaying,
                    isShuffleEnabled = vm.isShuffleEnabled,
                    playbackPositionProvider = { vm.playbackPosition },
                    shufflePosition = vm.shufflePosition,
                    queueSize = vm.activePlaybackQueue.size,
                    onSongSelected = onSong,
                    onShuffleToggle = onShuffle,
                    onReshuffle = onReshuffle,
                    onPlayPause = onPP,
                    onNext = onNext,
                    onPrevious = onPrev,
                    onSeek = onSeek,
                    onAddToQueue = onQueue
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val st = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, st).buildAsync()
        controllerFuture.addListener(
            { vm.setController(controllerFuture.get()) },
            ContextCompat.getMainExecutor(this)
        )
    }

    override fun onStop() {
        MediaController.releaseFuture(controllerFuture)
        super.onStop()
    }
}

// ═══════════════════════════════════════════════
// Playback Service
// ═══════════════════════════════════════════════

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

    override fun onDestroy() {
        session?.player?.release()
        session?.release()
        super.onDestroy()
    }
}

// ═══════════════════════════════════════════════
// Shuffle Toggle
// ═══════════════════════════════════════════════

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
            Icon(Icons.Default.Shuffle, "Toggle Shuffle", Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isShuffleEnabled) "Shuffle: ON" else "Shuffle: OFF",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1
            )
        }
        if (isShuffleEnabled) {
            Spacer(Modifier.width(12.dp))
            FilledIconButton(
                onClick = onReshuffle,
                shape = CircleShape,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(Icons.Default.Refresh, "Reshuffle", Modifier.size(24.dp))
            }
        }
    }
}

// ═══════════════════════════════════════════════
// Main Screen
// ═══════════════════════════════════════════════

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
                                0 -> SongsTab(songs, isLoading, isShuffleEnabled, currentSong, favoriteIds, onShuffleToggle, onReshuffle, toggleFav, onSongSelected, onFindSongs, onAddToQueue, snackbarHostState, scope)
                                1 -> PlaylistsTab(playlists, songs, currentSong, selectedPlaylist, { selectedPlaylist = it }, { selectedPlaylist = null }, { isCreatingPlaylist = true }, favoriteIds, isShuffleEnabled, onShuffleToggle, onReshuffle, toggleFav, onSongSelected, { p -> playlists = playlists.filter { it.name != p.name }.toImmutableList(); savePlaylists(prefs, playlists) }, onAddToQueue, snackbarHostState, scope)
                                2 -> GroupedTab(folders, isLoading, Icons.Default.Folder, selectedFolder, { selectedFolder = it }, { selectedFolder = null }, songs, { it.folder == selectedFolder }, favoriteIds, isShuffleEnabled, onShuffleToggle, onReshuffle, toggleFav, onSongSelected, onFindSongs, currentSong, onAddToQueue, snackbarHostState, scope)
                                3 -> FavoritesTab(songs, favoriteIds, isShuffleEnabled, currentSong, onShuffleToggle, onReshuffle, toggleFav, onSongSelected, onAddToQueue, snackbarHostState, scope)
                                4 -> GroupedTab(albums, isLoading, Icons.Default.Album, selectedAlbum, { selectedAlbum = it }, { selectedAlbum = null }, songs, { it.album == selectedAlbum }, favoriteIds, isShuffleEnabled, onShuffleToggle, onReshuffle, toggleFav, onSongSelected, onFindSongs, currentSong, onAddToQueue, snackbarHostState, scope)
                                5 -> GroupedTab(artists, isLoading, Icons.Default.Person, selectedArtist, { selectedArtist = it }, { selectedArtist = null }, songs, { it.artist == selectedArtist }, favoriteIds, isShuffleEnabled, onShuffleToggle, onReshuffle, toggleFav, onSongSelected, onFindSongs, currentSong, onAddToQueue, snackbarHostState, scope)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════
// Songs Tab
// ═══════════════════════════════════════════════

@Composable
fun SongsTab(
    songs: ImmutableList<Song>,
    isLoading: Boolean,
    isShuffleEnabled: Boolean,
    currentSong: Song?,
    favoriteIds: Set<String>,
    onShuffleToggle: () -> Unit,
    onReshuffle: () -> Unit,
    toggleFav: (Long) -> Unit,
    onSongSelected: (Song, ImmutableList<Song>) -> Unit,
    onFindSongs: () -> Unit,
    onAddToQueue: (Song) -> Unit,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope
) {
    Column(Modifier.fillMaxSize()) {
        if (songs.isNotEmpty()) {
            EnhancedShuffleToggle(isShuffleEnabled, onShuffleToggle, onReshuffle)
        }
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (songs.isEmpty()) {
            EmptyState(
                icon = Icons.Default.LibraryMusic,
                title = "Your library is empty",
                subtitle = "Tap below to scan your device for music",
                actionLabel = "Scan Device for Music",
                onAction = onFindSongs
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(items = songs, key = { it.id }) { song ->
                    SwipeableSongItem(
                        song = song,
                        isPlaying = currentSong?.id == song.id,
                        currentList = songs,
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
    }
}

// ═══════════════════════════════════════════════
// Favorites Tab
// ═══════════════════════════════════════════════

@Composable
fun FavoritesTab(
    songs: ImmutableList<Song>,
    favoriteIds: Set<String>,
    isShuffleEnabled: Boolean,
    currentSong: Song?,
    onShuffleToggle: () -> Unit,
    onReshuffle: () -> Unit,
    toggleFav: (Long) -> Unit,
    onSongSelected: (Song, ImmutableList<Song>) -> Unit,
    onAddToQueue: (Song) -> Unit,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope
) {
    val favSongs: ImmutableList<Song> by remember(songs, favoriteIds) {
        derivedStateOf {
            songs.filter { favoriteIds.contains(it.id.toString()) }.toImmutableList()
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (favSongs.isNotEmpty()) {
            EnhancedShuffleToggle(isShuffleEnabled, onShuffleToggle, onReshuffle)
        }
        if (favSongs.isEmpty()) {
            EmptyState(
                icon = Icons.Default.FavoriteBorder,
                title = "No favorites yet",
                subtitle = "Heart some songs to see them here"
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(items = favSongs, key = { it.id }) { song ->
                    SwipeableSongItem(
                        song = song,
                        isPlaying = currentSong?.id == song.id,
                        currentList = favSongs,
                        isFavorite = true,
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
    }
}

// ═══════════════════════════════════════════════
// Empty State
// ═══════════════════════════════════════════════

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon, null, Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onAction,
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Search, null)
                    Spacer(Modifier.width(8.dp))
                    Text(actionLabel, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════
// Grouped Tab (Folder / Album / Artist) — with stats
// ═══════════════════════════════════════════════

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
    currentSong: Song?,
    onAddToQueue: (Song) -> Unit,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope
) {
    if (selectedItem == null) {
        Column(Modifier.fillMaxSize()) {
            if (items.isNotEmpty()) {
                EnhancedShuffleToggle(isShuffleEnabled, onShuffleToggle, onReshuffle)
            }
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (items.isEmpty()) {
                EmptyState(
                    icon = icon,
                    title = "Nothing here yet",
                    subtitle = "Scan your device to find music",
                    actionLabel = "Scan Device for Music",
                    onAction = onFindSongs
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(items = items) { (name, count) ->
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
        val groupSongs: ImmutableList<Song> by remember(songs, selectedItem) {
            derivedStateOf {
                songs.filter(filterPredicate).toImmutableList()
            }
        }

        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onBackClick() }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            selectedItem,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (groupSongs.isNotEmpty()) {
                            Text(
                                "${groupSongs.size} songs • ${formatTotalDuration(groupSongs)}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (groupSongs.isNotEmpty()) {
                EnhancedShuffleToggle(isShuffleEnabled, onShuffleToggle, onReshuffle)
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(items = groupSongs, key = { it.id }) { song ->
                    SwipeableSongItem(
                        song = song,
                        isPlaying = currentSong?.id == song.id,
                        currentList = groupSongs,
                        isFavorite = favoriteIds.contains(song.id.toString()),
                        onFavoriteToggle = onToggleFavorite,
                        onSongClick = onSongSelected,
                        onAddToQueue = { qSong ->
                            onAddToQueue(qSong)
                            scope.launch { snackbarHostState.showSnackbar("Added to queue") }
                        }
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════
// Playlists Tab
// ═══════════════════════════════════════════════

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
    onDeletePlaylist: (Playlist) -> Unit,
    onAddToQueue: (Song) -> Unit,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope
) {
    var toDelete by remember { mutableStateOf<Playlist?>(null) }

    val deleteTarget = toDelete
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete Playlist", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete this playlist?") },
            confirmButton = {
                TextButton(onClick = {
                    onDeletePlaylist(deleteTarget)
                    toDelete = null
                }) {
                    Text("Delete", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) {
                    Text(
                        "Cancel",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }

    if (selectedPlaylist == null) {
        Column(Modifier.fillMaxSize()) {
            Button(
                onClick = onCreateClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Create New Playlist", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            if (playlists.isEmpty()) {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                    title = "No playlists yet",
                    subtitle = "Create a playlist to organize your music"
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(items = playlists) { pl ->
                        GroupItem(
                            name = pl.name,
                            count = pl.songIds.size,
                            icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                            onClick = { onPlaylistClick(pl) },
                            onDeleteClick = { toDelete = pl }
                        )
                    }
                }
            }
        }
    } else {
        val plSongs: ImmutableList<Song> by remember(songs, selectedPlaylist) {
            derivedStateOf {
                selectedPlaylist.songIds
                    .mapNotNull { id -> songs.find { it.id == id } }
                    .toImmutableList()
            }
        }

        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onBackClick() }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            selectedPlaylist.name,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (plSongs.isNotEmpty()) {
                            Text(
                                "${plSongs.size} songs • ${formatTotalDuration(plSongs)}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (plSongs.isNotEmpty()) {
                EnhancedShuffleToggle(isShuffleEnabled, onShuffleToggle, onReshuffle)
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(items = plSongs, key = { it.id }) { song ->
                    SwipeableSongItem(
                        song = song,
                        isPlaying = currentSong?.id == song.id,
                        currentList = plSongs,
                        isFavorite = favoriteIds.contains(song.id.toString()),
                        onFavoriteToggle = onToggleFavorite,
                        onSongClick = onSongSelected,
                        onAddToQueue = { qSong ->
                            onAddToQueue(qSong)
                            scope.launch { snackbarHostState.showSnackbar("Added to queue") }
                        }
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════
// Create Playlist Screen
// ═══════════════════════════════════════════════

@Composable
fun CreatePlaylistScreen(
    allSongs: ImmutableList<Song>,
    folders: ImmutableList<Pair<String, Int>>,
    onDismiss: () -> Unit,
    onSave: (String, Set<Long>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var dq by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<Long>()) }

    LaunchedEffect(query) { delay(300); dq = query }

    val filtered: ImmutableList<Song> = remember(allSongs, dq) {
        if (dq.isBlank()) allSongs
        else allSongs.filter {
            it.title.contains(dq, ignoreCase = true) ||
                    it.artist.contains(dq, ignoreCase = true)
        }.toImmutableList()
    }

    val gradient = if (isSystemInDarkTheme()) DarkMintGradient else MintGradient

    BackHandler { onDismiss() }

    Surface(Modifier.fillMaxSize(), color = Color.Transparent) {
        Box(
            Modifier
                .fillMaxSize()
                .background(gradient)
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Text(
                        "Create New Playlist",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist Name") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary
                    )
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search Songs to Add") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary
                    )
                )

                Spacer(Modifier.height(16.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Songs (${selected.size} selected)",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 16.sp
                    )

                    var showFD by remember { mutableStateOf(false) }
                    TextButton(onClick = { showFD = true }) {
                        Text(
                            "Add by Folder",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (showFD) {
                        AlertDialog(
                            onDismissRequest = { showFD = false },
                            title = { Text("Select Folder", fontWeight = FontWeight.Bold) },
                            text = {
                                LazyColumn {
                                    items(items = folders) { (fn, c) ->
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    val folderSongIds = allSongs
                                                        .filter { it.folder == fn }
                                                        .map { it.id }
                                                        .toSet()
                                                    selected = selected + folderSongIds
                                                    showFD = false
                                                }
                                                .padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Folder, null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(Modifier.width(16.dp))
                                            Text(
                                                "$fn ($c songs)",
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showFD = false }) {
                                    Text("Cancel", fontWeight = FontWeight.Bold)
                                }
                            }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                LazyColumn(Modifier.weight(1f)) {
                    items(items = filtered) { song ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    selected = if (selected.contains(song.id)) {
                                        selected - song.id
                                    } else {
                                        selected + song.id
                                    }
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selected.contains(song.id),
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    song.title,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    song.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                formatDuration(song.duration),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = { if (name.isNotBlank()) onSave(name, selected) },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    enabled = name.isNotBlank() && selected.isNotEmpty()
                ) {
                    Text("Save Playlist", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════
// Playlist Persistence (JSON-based)
// ═══════════════════════════════════════════════

fun savePlaylists(prefs: SharedPreferences, pls: ImmutableList<Playlist>) {
    val ja = JSONArray()
    for (p in pls) {
        val o = JSONObject()
        o.put("name", p.name)
        val ids = JSONArray()
        for (id in p.songIds) ids.put(id)
        o.put("songIds", ids)
        ja.put(o)
    }
    prefs.edit {
        putString("playlists_json", ja.toString())
        remove("playlist_names")
    }
}

fun loadPlaylists(prefs: SharedPreferences): ImmutableList<Playlist> {
    val json = prefs.getString("playlists_json", null)
    if (json == null) {
        val ln = prefs.getStringSet("playlist_names", null) ?: return persistentListOf()
        val m = ln.map { n ->
            val idSet = prefs.getStringSet("playlist_ids_$n", emptySet()) ?: emptySet()
            Playlist(n, idSet.mapNotNull { it.toLongOrNull() }.toImmutableList())
        }.toImmutableList()
        savePlaylists(prefs, m)
        return m
    }
    return try {
        val ja = JSONArray(json)
        val result = mutableListOf<Playlist>()
        for (i in 0 until ja.length()) {
            val o = ja.getJSONObject(i)
            val ids = o.getJSONArray("songIds")
            val idList = mutableListOf<Long>()
            for (j in 0 until ids.length()) {
                idList.add(ids.getLong(j))
            }
            result.add(Playlist(o.getString("name"), idList.toImmutableList()))
        }
        result.toImmutableList()
    } catch (_: Exception) {
        persistentListOf()
    }
}

// ═══════════════════════════════════════════════
// Group Item
// ═══════════════════════════════════════════════

@Composable
fun GroupItem(
    name: String,
    count: Int,
    icon: ImageVector,
    onClick: () -> Unit,
    onDeleteClick: (() -> Unit)? = null
) {
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "$count songs",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (onDeleteClick != null) {
                IconButton(onClick = onDeleteClick, Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════
// Now Playing (Full Screen)
// ═══════════════════════════════════════════════

@Composable
fun NowPlayingScreen(
    song: Song,
    isPlaying: Boolean,
    isShuffleEnabled: Boolean,
    playbackPositionProvider: () -> Long,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }

    val dominantColor = rememberDominantColor(
        uri = song.albumArtUri,
        defaultColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    )
    val bgGradient = Brush.verticalGradient(
        listOf(dominantColor.copy(alpha = 0.8f), MaterialTheme.colorScheme.background)
    )

    val pos = playbackPositionProvider()
    var isDragging by remember { mutableStateOf(false) }
    var dragPos by remember { mutableFloatStateOf(0f) }
    val safeDur = song.duration.toFloat().coerceAtLeast(1000f)
    val safePos = (if (isDragging) dragPos else pos.toFloat()).coerceIn(0f, safeDur)

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            Modifier
                .fillMaxSize()
                .background(bgGradient)
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top bar
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            "Close",
                            Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Text(
                        "Now Playing",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (isShuffleEnabled) {
                        Icon(
                            Icons.Default.Shuffle, null, Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Spacer(Modifier.size(24.dp))
                    }
                }

                Spacer(Modifier.height(32.dp))

                // Large album art
                Card(
                    Modifier
                        .fillMaxWidth(0.75f)
                        .aspectRatio(1f),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(12.dp)
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.MusicNote, null, Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                        AsyncImage(
                            model = song.albumArtUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))

                // Song info
                Text(
                    song.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(
                        iterations = Int.MAX_VALUE,
                        repeatDelayMillis = 2000
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${song.artist} — ${song.album}",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(28.dp))

                // Slider
                Slider(
                    value = safePos,
                    onValueChange = { isDragging = true; dragPos = it },
                    onValueChangeFinished = {
                        onSeek(dragPos.toLong())
                        isDragging = false
                    },
                    valueRange = 0f..safeDur,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        formatDurationLong(safePos.toLong()),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        formatDuration(song.duration),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Controls
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onPrevious, Modifier.size(64.dp)) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            "Previous",
                            Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    FilledIconButton(
                        onClick = onPlayPause,
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            null,
                            Modifier.size(40.dp)
                        )
                    }
                    IconButton(onClick = onNext, Modifier.size(64.dp)) {
                        Icon(
                            Icons.Default.SkipNext,
                            "Next",
                            Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════
// Mini Player
// ═══════════════════════════════════════════════

@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    playbackPositionProvider: () -> Long,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onTap: () -> Unit
) {
    val pos = playbackPositionProvider()
    var isDragging by remember { mutableStateOf(false) }
    var dragPos by remember { mutableFloatStateOf(0f) }
    val safeDur = song.duration.toFloat().coerceAtLeast(1000f)
    val safePos = (if (isDragging) dragPos else pos.toFloat()).coerceIn(0f, safeDur)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 16.dp
    ) {
        Column(
            Modifier
                .padding(top = 12.dp, bottom = 16.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth()
        ) {
            Slider(
                value = safePos,
                onValueChange = { isDragging = true; dragPos = it },
                onValueChangeFinished = {
                    onSeek(dragPos.toLong())
                    isDragging = false
                },
                valueRange = 0f..safeDur,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    formatDurationLong(safePos.toLong()),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    formatDuration(song.duration),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Card(
                    Modifier.size(54.dp),
                    shape = MaterialTheme.shapes.small,
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.MusicNote, null, Modifier.size(24.dp),
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

                Spacer(Modifier.width(16.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        song.title,
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
                    Spacer(Modifier.height(4.dp))
                    Text(
                        song.artist,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onPrevious) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            "Previous",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    IconButton(onClick = onPlayPause) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            if (isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(42.dp)
                        )
                    }
                    IconButton(onClick = onNext) {
                        Icon(
                            Icons.Default.SkipNext,
                            "Next",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════
// Swipeable Song Item
// ═══════════════════════════════════════════════

@Composable
fun SwipeableSongItem(
    song: Song,
    isPlaying: Boolean,
    currentList: ImmutableList<Song>,
    isFavorite: Boolean,
    onFavoriteToggle: (Long) -> Unit,
    onSongClick: (Song, ImmutableList<Song>) -> Unit,
    onAddToQueue: (Song) -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value: SwipeToDismissBoxValue ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onFavoriteToggle(song.id)
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onAddToQueue(song)
                    false
                }
                else -> false
            }
        }
    )

    val bgColor: Color by animateColorAsState(
        targetValue = when (dismissState.targetValue) {
            SwipeToDismissBoxValue.StartToEnd -> Color.Red.copy(alpha = 0.15f)
            SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            else -> Color.Transparent
        },
        label = "swipeBg"
    )

    val isSwipingStart = dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd ||
            dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
    val isSwipingEnd = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart ||
            dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(bgColor)
                    .padding(horizontal = 20.dp)
            ) {
                if (isSwipingStart) {
                    Row(
                        Modifier.align(Alignment.CenterStart),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isFavorite) Icons.Default.HeartBroken else Icons.Default.Favorite,
                            null,
                            tint = Color.Red
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isFavorite) "Unfavorite" else "Favorite",
                            fontWeight = FontWeight.Bold,
                            color = Color.Red,
                            fontSize = 14.sp
                        )
                    }
                }
                if (isSwipingEnd) {
                    Row(
                        Modifier.align(Alignment.CenterEnd),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Add to Queue",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Default.QueueMusic, null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true
    ) {
        SongItem(song, isPlaying, currentList, isFavorite, onFavoriteToggle, onSongClick)
    }
}

// ═══════════════════════════════════════════════
// Song Item — with equalizer + duration
// ═══════════════════════════════════════════════

@Composable
fun SongItem(
    song: Song,
    isPlaying: Boolean,
    currentList: ImmutableList<Song>,
    isFavorite: Boolean,
    onFavoriteToggle: (Long) -> Unit,
    onSongClick: (Song, ImmutableList<Song>) -> Unit
) {
    Card(
        Modifier
            .fillMaxWidth()
            .clickable { onSongClick(song, currentList) },
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (isPlaying) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(Modifier.size(44.dp), shape = MaterialTheme.shapes.small) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.MusicNote, null, Modifier.size(22.dp),
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

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    fontWeight = if (isPlaying) FontWeight.ExtraBold else FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isPlaying) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${song.artist} • ${song.album} • ${formatDuration(song.duration)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isPlaying) {
                AnimatedEqualizer(
                    modifier = Modifier
                        .size(24.dp)
                        .padding(end = 8.dp),
                    isPlaying = true,
                    barColor = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(
                onClick = { onFavoriteToggle(song.id) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    "Toggle Favorite",
                    tint = if (isFavorite) Color.Red
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════
// Fetch Songs
// ═══════════════════════════════════════════════

fun fetchSongs(ctx: Context): ImmutableList<Song> {
    val songs = mutableListOf<Song>()
    try {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val proj = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATA
        )
        val hasRP = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        if (hasRP) proj.add(MediaStore.Audio.Media.RELATIVE_PATH)

        val sel = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sort = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        ctx.contentResolver.query(collection, proj.toTypedArray(), sel, null, sort)?.use { c ->
            val idC = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albIdC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val dataC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val rpC = if (hasRP) c.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH) else -1

            while (c.moveToNext()) {
                try {
                    val id = c.getLong(idC)
                    val title = c.getString(titleC) ?: "Unknown Track"
                    val artist = c.getString(artistC) ?: "Unknown Artist"
                    val album = c.getString(albumC) ?: "Unknown Album"
                    val dur = c.getInt(durC)
                    val albId = c.getLong(albIdC)
                    val path = c.getString(dataC) ?: ""

                    val folder = if (rpC >= 0) {
                        val rp = c.getString(rpC) ?: ""
                        rp.trimEnd('/').substringAfterLast('/').ifEmpty { "Unknown" }
                    } else {
                        try {
                            if (path.isNotEmpty()) File(path).parentFile?.name ?: "Unknown"
                            else "Unknown"
                        } catch (_: Exception) {
                            "Unknown"
                        }
                    }

                    val uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                    )
                    val artUri = try {
                        if (albId > 0) ContentUris.withAppendedId(
                            "content://media/external/audio/albumart".toUri(), albId
                        ) else null
                    } catch (_: Exception) {
                        null
                    }

                    songs.add(Song(id, title, artist, album, dur, uri, artUri, folder))
                } catch (_: Exception) {
                    // skip
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return persistentListOf()
    }
    return songs.toImmutableList()
}

// ═══════════════════════════════════════════════
// Preview
// ═══════════════════════════════════════════════

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
            onSeek = {},
            onAddToQueue = {}
        )
    }
}