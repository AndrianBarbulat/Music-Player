@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.musicplayerdeck.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.musicplayerdeck.data.model.Song
import com.example.musicplayerdeck.ui.theme.AppBackground
import com.example.musicplayerdeck.ui.theme.AppElevated
import com.example.musicplayerdeck.ui.theme.TealPrimary
import com.example.musicplayerdeck.ui.theme.TextFaint
import com.example.musicplayerdeck.ui.theme.TextMuted
import com.example.musicplayerdeck.ui.theme.TextPrimary
import com.example.musicplayerdeck.ui.theme.TextSecondary
import com.example.musicplayerdeck.util.formatDuration
import com.example.musicplayerdeck.util.formatDurationLong

@Composable
fun NowPlayingScreen(
    song: Song,
    isPlaying: Boolean,
    isShuffleEnabled: Boolean,
    playbackPositionProvider: () -> Long,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onDismiss: () -> Unit,
    onMoreClick: (() -> Unit)? = null
) {
    BackHandler { onDismiss() }

    val pos = playbackPositionProvider()
    var isDragging by remember { mutableStateOf(false) }
    var dragPos by remember { mutableFloatStateOf(0f) }
    val safeDur = song.duration.toFloat().coerceAtLeast(1000f)
    val safePos = (if (isDragging) dragPos else pos.toFloat()).coerceIn(0f, safeDur)

    Box(
        Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp)
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))

            // Top bar
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                    Icon(
                        Icons.Default.KeyboardArrowDown, "Close",
                        Modifier.size(32.dp),
                        tint = TextSecondary
                    )
                }
                if (isShuffleEnabled) {
                    Icon(
                        Icons.Default.Shuffle, null,
                        Modifier.size(18.dp),
                        tint = TealPrimary
                    )
                } else {
                    Spacer(Modifier.size(18.dp))
                }
                if (onMoreClick != null) {
                    IconButton(onClick = onMoreClick, modifier = Modifier.size(44.dp)) {
                        Icon(
                            Icons.Default.MoreVert, "More options",
                            Modifier.size(24.dp),
                            tint = TextSecondary
                        )
                    }
                } else {
                    Spacer(Modifier.size(44.dp))
                }
            }

            Spacer(Modifier.height(32.dp))

            // Large album art
            Box(
                Modifier
                    .fillMaxWidth(0.82f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.MusicNote, null,
                    Modifier.size(80.dp),
                    tint = TextMuted.copy(alpha = 0.4f)
                )
                AsyncImage(
                    model = song.albumArtUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(Modifier.height(32.dp))

            // Song info
            Text(
                song.title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee(iterations = Int.MAX_VALUE, repeatDelayMillis = 2000)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                song.artist,
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(32.dp))

            // Progress bar with custom track (3dp) and thumb (12dp)
            Slider(
                value = safePos,
                onValueChange = { isDragging = true; dragPos = it },
                onValueChangeFinished = { onSeek(dragPos.toLong()); isDragging = false },
                valueRange = 0f..safeDur,
                modifier = Modifier.fillMaxWidth(),
                thumb = {
                    Box(
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(TealPrimary)
                    )
                },
                track = { sliderState: SliderState ->
                    val fraction = (sliderState.value / safeDur).coerceIn(0f, 1f)
                    Box(Modifier.fillMaxWidth().height(3.dp)) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(AppElevated)
                        )
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .fillMaxSize()
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(TealPrimary)
                        )
                    }
                }
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatDurationLong(safePos.toLong()), fontSize = 11.sp, color = TextFaint)
                Text(formatDuration(song.duration), fontSize = 11.sp, color = TextFaint)
            }

            Spacer(Modifier.height(32.dp))

            // Controls row
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevious, Modifier.size(56.dp)) {
                    Icon(
                        Icons.Default.SkipPrevious, "Previous",
                        Modifier.size(28.dp),
                        tint = TextSecondary
                    )
                }
                // Play/pause: 52dp circle, Primary bg, Background icon
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(TealPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = onPlayPause, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            null,
                            Modifier.size(30.dp),
                            tint = AppBackground
                        )
                    }
                }
                IconButton(onClick = onNext, Modifier.size(56.dp)) {
                    Icon(
                        Icons.Default.SkipNext, "Next",
                        Modifier.size(28.dp),
                        tint = TextSecondary
                    )
                }
            }
        }
    }
}
