package com.example.musicplayerdeck.ui.screens.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.musicplayerdeck.data.model.Song
import com.example.musicplayerdeck.ui.components.EmptyState
import com.example.musicplayerdeck.ui.components.EnhancedShuffleToggle
import com.example.musicplayerdeck.ui.components.SwipeableSongItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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