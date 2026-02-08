package com.example.musicplayerdeck.ui.screens.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicplayerdeck.data.model.Playlist
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