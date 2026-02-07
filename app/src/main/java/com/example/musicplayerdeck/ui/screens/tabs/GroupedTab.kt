package com.example.musicplayerdeck.ui.screens.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicplayerdeck.data.model.Song
import com.example.musicplayerdeck.ui.components.EmptyState
import com.example.musicplayerdeck.ui.components.EnhancedShuffleToggle
import com.example.musicplayerdeck.ui.components.GroupItem
import com.example.musicplayerdeck.ui.components.SwipeableSongItem
import com.example.musicplayerdeck.util.formatTotalDuration
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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
                            name = name, count = count, icon = icon,
                            onClick = { onItemClick(name) }
                        )
                    }
                }
            }
        }
    } else {
        val groupSongs: ImmutableList<Song> by remember(songs, selectedItem) {
            derivedStateOf { songs.filter(filterPredicate).toImmutableList() }
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(selectedItem, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground)
                        if (groupSongs.isNotEmpty()) {
                            Text("${groupSongs.size} songs • ${formatTotalDuration(groupSongs)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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