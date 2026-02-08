package com.example.musicplayerdeck

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.musicplayerdeck.data.model.Song
import com.example.musicplayerdeck.service.PlaybackService
import com.example.musicplayerdeck.ui.screens.MainScreen
import com.example.musicplayerdeck.ui.theme.MusicPlayerDeckTheme
import com.example.musicplayerdeck.viewmodel.MusicPlayerViewModel
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.collections.immutable.ImmutableList

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