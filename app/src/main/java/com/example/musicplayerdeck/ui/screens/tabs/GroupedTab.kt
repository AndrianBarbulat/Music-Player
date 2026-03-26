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
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.musicplayerdeck.ui.components.SortDropdown
import com.example.musicplayerdeck.ui.components.SortOption
import com.example.musicplayerdeck.ui.components.SwipeableSongItem
import com.example.musicplayerdeck.ui.components.sortSongs
import com.example.musicplayerdeck.util.formatTotalDuration
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

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
    playCounts: Map<Long, Int> = emptyMap(),
    onBatchAddToPlaylist: ((Set<Long>) -> Unit)? = null,
    onSongMoreClick: ((Song) -> Unit)? = null
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
        var sortOption by remember { mutableStateOf(SortOption.NAME_ASC) }
        var isBatchMode by remember { mutableStateOf(false) }
        var selectedIds by remember { mutableStateOf(setOf<Long>()) }

        val groupSongs: ImmutableList<Song> by remember(songs, selectedItem, sortOption, playCounts) {
            derivedStateOf {
                val filtered = songs.filter(filterPredicate)
                sortSongs(filtered, sortOption, playCounts).toImmutableList()
            }
        }

        Column(Modifier.fillMaxSize()) {
            // Header
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
                        Icons.AutoMirrored.Filled.ArrowBack, "Back",
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

                // Sort + Batch row
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SortDropdown(
                        currentSort = sortOption,
                        showPlayCount = playCounts.isNotEmpty()
                    ) { sortOption = it }

                    if (isBatchMode) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = {
                                selectedIds = if (selectedIds.size == groupSongs.size) emptySet()
                                else groupSongs.map { it.id }.toSet()
                            }) {
                                Icon(Icons.Default.SelectAll, null, Modifier.padding(end = 4.dp))
                                Text(
                                    if (selectedIds.size == groupSongs.size) "Deselect" else "All",
                                    fontSize = 12.sp
                                )
                            }
                            if (selectedIds.isNotEmpty() && onBatchAddToPlaylist != null) {
                                IconButton(onClick = {
                                    onBatchAddToPlaylist(selectedIds)
                                    isBatchMode = false
                                    selectedIds = emptySet()
                                }) {
                                    Icon(
                                        Icons.Default.PlaylistAdd, "Add to playlist",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            IconButton(onClick = { isBatchMode = false; selectedIds = emptySet() }) {
                                Icon(Icons.Default.Close, "Cancel")
                            }
                        }
                    } else if (onBatchAddToPlaylist != null) {
                        IconButton(onClick = { isBatchMode = true }) {
                            Icon(
                                Icons.Default.Checklist, "Batch select",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                        isBatchMode = isBatchMode,
                        isSelected = selectedIds.contains(song.id),
                        onBatchToggle = { id ->
                            selectedIds = if (selectedIds.contains(id)) selectedIds - id
                            else selectedIds + id
                        },
                        onMoreClick = if (onSongMoreClick != null) { { onSongMoreClick(song) } } else null
                    )
                }
            }
        }
    }
}