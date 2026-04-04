package com.example.musicplayerdeck.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
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
import com.example.musicplayerdeck.ui.theme.AppSurface
import com.example.musicplayerdeck.ui.theme.DividerColor
import com.example.musicplayerdeck.ui.theme.TealPrimary
import com.example.musicplayerdeck.ui.theme.TextMuted
import com.example.musicplayerdeck.ui.theme.TextPrimary

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
    val safeDur = song.duration.toFloat().coerceAtLeast(1000f)
    val progress = (pos.toFloat() / safeDur).coerceIn(0f, 1f)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppSurface,
        tonalElevation = 0.dp
    ) {
        Column {
            // Top divider
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(DividerColor)
            )
            // Thin progress bar at the very top edge
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(AppElevated)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction = progress)
                        .height(2.dp)
                        .background(TealPrimary)
                )
            }

            // Main row
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onTap)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album art
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AppElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.MusicNote, null, Modifier.size(18.dp), tint = TextMuted)
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee(iterations = Int.MAX_VALUE, repeatDelayMillis = 2000),
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        song.artist,
                        fontSize = 12.sp,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onPrevious, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.SkipPrevious, "Previous", Modifier.size(24.dp), tint = TextMuted)
                }
                IconButton(onClick = onPlayPause, modifier = Modifier.size(44.dp)) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (isPlaying) "Pause" else "Play",
                        Modifier.size(32.dp),
                        tint = TextPrimary
                    )
                }
                IconButton(onClick = onNext, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.SkipNext, "Next", Modifier.size(24.dp), tint = TextMuted)
                }
            }
        }
    }
}
