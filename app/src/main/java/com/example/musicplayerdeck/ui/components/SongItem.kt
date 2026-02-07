package com.example.musicplayerdeck.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.musicplayerdeck.data.model.Song
import com.example.musicplayerdeck.util.formatDuration
import kotlinx.collections.immutable.ImmutableList

@OptIn(ExperimentalMaterial3Api::class)
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
                            null, tint = Color.Red
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isFavorite) "Unfavorite" else "Favorite",
                            fontWeight = FontWeight.Bold, color = Color.Red, fontSize = 14.sp
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
                            color = MaterialTheme.colorScheme.primary, fontSize = 14.sp
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
                        model = song.albumArtUri, contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    fontWeight = if (isPlaying) FontWeight.ExtraBold else FontWeight.Bold,
                    fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (isPlaying) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${song.artist} • ${song.album} • ${formatDuration(song.duration)}",
                    style = MaterialTheme.typography.bodySmall, fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
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