package com.example.musicplayerdeck.ui.screens.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.musicplayerdeck.data.model.Song
import com.example.musicplayerdeck.ui.components.EmptyState
import com.example.musicplayerdeck.ui.components.EnhancedShuffleToggle
import com.example.musicplayerdeck.ui.components.SwipeableSongItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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