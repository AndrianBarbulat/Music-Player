package com.example.musicplayerdeck.ui.screens.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicplayerdeck.data.model.Song
import com.example.musicplayerdeck.ui.components.EmptyState
import com.example.musicplayerdeck.ui.components.EnhancedShuffleToggle
import com.example.musicplayerdeck.ui.components.SortDropdown
import com.example.musicplayerdeck.ui.components.SortOption
import com.example.musicplayerdeck.ui.components.SwipeableSongItem
import com.example.musicplayerdeck.ui.components.sortSongs
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

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
    playCounts: Map<Long, Int> = emptyMap(),
    onBatchAddToPlaylist: ((Set<Long>) -> Unit)? = null,
    onSongMoreClick: ((Song) -> Unit)? = null
) {
    var sortOption by remember { mutableStateOf(SortOption.NAME_ASC) }
    var isBatchMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }

    val sortedSongs = remember(songs, sortOption, playCounts) {
        sortSongs(songs, sortOption, playCounts).toImmutableList()
    }

    Column(Modifier.fillMaxSize()) {
        if (songs.isNotEmpty()) {
            EnhancedShuffleToggle(isShuffleEnabled, onShuffleToggle, onReshuffle)

            // Sort + Batch row
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SortDropdown(currentSort = sortOption, showPlayCount = playCounts.isNotEmpty()) { sortOption = it }

                if (isBatchMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            selectedIds = if (selectedIds.size == songs.size) emptySet()
                            else songs.map { it.id }.toSet()
                        }) {
                            Icon(Icons.Default.SelectAll, null, Modifier.padding(end = 4.dp))
                            Text(if (selectedIds.size == songs.size) "Deselect" else "All", fontSize = 12.sp)
                        }
                        if (selectedIds.isNotEmpty() && onBatchAddToPlaylist != null) {
                            IconButton(onClick = { onBatchAddToPlaylist(selectedIds); isBatchMode = false; selectedIds = emptySet() }) {
                                Icon(Icons.Default.PlaylistAdd, "Add to playlist", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        IconButton(onClick = { isBatchMode = false; selectedIds = emptySet() }) {
                            Icon(Icons.Default.Close, "Cancel")
                        }
                    }
                } else {
                    IconButton(onClick = { isBatchMode = true }) {
                        Icon(Icons.Default.Checklist, "Batch select", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (isBatchMode && selectedIds.isNotEmpty()) {
                Text(
                    "${selectedIds.size} selected",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
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
                items(items = sortedSongs, key = { it.id }) { song ->
                    SwipeableSongItem(
                        song = song,
                        isPlaying = currentSong?.id == song.id,
                        currentList = sortedSongs,
                        isFavorite = favoriteIds.contains(song.id.toString()),
                        onFavoriteToggle = toggleFav,
                        onSongClick = onSongSelected,
                        isBatchMode = isBatchMode,
                        isSelected = selectedIds.contains(song.id),
                        onBatchToggle = { id ->
                            selectedIds = if (selectedIds.contains(id)) selectedIds - id else selectedIds + id
                        },
                        onMoreClick = if (onSongMoreClick != null) { { onSongMoreClick(song) } } else null
                    )
                }
            }
        }
    }
}