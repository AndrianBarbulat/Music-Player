package com.example.musicplayerdeck.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.musicplayerdeck.ui.theme.AppElevated
import com.example.musicplayerdeck.ui.theme.DividerColor
import com.example.musicplayerdeck.ui.theme.TealPrimary
import com.example.musicplayerdeck.ui.theme.TealPrimaryDark
import com.example.musicplayerdeck.ui.theme.TealPrimaryFaint
import com.example.musicplayerdeck.ui.theme.TextFaint
import com.example.musicplayerdeck.ui.theme.TextMuted
import com.example.musicplayerdeck.ui.theme.TextPrimary
import com.example.musicplayerdeck.util.formatDuration
import kotlinx.collections.immutable.ImmutableList

@Composable
fun SwipeableSongItem(
    song: Song,
    isPlaying: Boolean,
    currentList: ImmutableList<Song>,
    isFavorite: Boolean,
    onFavoriteToggle: (Long) -> Unit,
    onSongClick: (Song, ImmutableList<Song>) -> Unit,
    isBatchMode: Boolean = false,
    isSelected: Boolean = false,
    onBatchToggle: ((Long) -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null
) {
    if (isBatchMode) {
        SelectableSongItem(
            song = song,
            isPlaying = isPlaying,
            isSelected = isSelected,
            onToggle = { onBatchToggle?.invoke(song.id) }
        )
    } else {
        SongItem(song, isPlaying, currentList, isFavorite, onFavoriteToggle, onSongClick, onMoreClick)
    }
}

@Composable
fun SelectableSongItem(
    song: Song,
    isPlaying: Boolean,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(if (isSelected) TealPrimaryFaint else Color.Transparent)
            .clickable { onToggle() }
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = "Select",
                modifier = Modifier.size(22.dp),
                tint = if (isSelected) TealPrimary else TextMuted.copy(alpha = 0.6f)
            )
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MusicNote, null, Modifier.size(20.dp), tint = TextMuted)
                AsyncImage(
                    model = song,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isPlaying) TealPrimary else TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    song.artist,
                    fontSize = 12.sp,
                    color = if (isPlaying) TealPrimaryDark else TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                formatDuration(song.duration),
                fontSize = 12.sp,
                color = TextFaint,
                modifier = Modifier.padding(end = 4.dp)
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 72.dp)
                .height(0.5.dp)
                .background(DividerColor)
        )
    }
}

@Composable
fun SongItem(
    song: Song,
    isPlaying: Boolean,
    currentList: ImmutableList<Song>,
    isFavorite: Boolean,
    onFavoriteToggle: (Long) -> Unit,
    onSongClick: (Song, ImmutableList<Song>) -> Unit,
    onMoreClick: (() -> Unit)? = null
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(if (isPlaying) TealPrimaryFaint else Color.Transparent)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .clickable { onSongClick(song, currentList) }
                    .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album art with playing overlay
                Box(Modifier.size(44.dp)) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(AppElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.MusicNote, null, Modifier.size(20.dp), tint = TextMuted)
                        AsyncImage(
                            model = song,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    if (isPlaying) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedEqualizer(
                                modifier = Modifier.size(22.dp),
                                isPlaying = true,
                                barColor = TealPrimary
                            )
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        song.title,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isPlaying) TealPrimary else TextPrimary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        song.artist,
                        fontSize = 12.sp,
                        color = if (isPlaying) TealPrimaryDark else TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                formatDuration(song.duration),
                fontSize = 12.sp,
                color = TextFaint,
                modifier = Modifier.padding(end = 4.dp)
            )
            IconButton(
                onClick = { onFavoriteToggle(song.id) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Toggle Favorite",
                    modifier = Modifier.size(18.dp),
                    tint = if (isFavorite) TealPrimary else TextMuted.copy(alpha = 0.4f)
                )
            }
            if (onMoreClick != null) {
                IconButton(
                    onClick = onMoreClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "More options",
                        modifier = Modifier.size(20.dp),
                        tint = TextMuted.copy(alpha = 0.5f)
                    )
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 72.dp)
                .height(0.5.dp)
                .background(DividerColor)
        )
    }
}
