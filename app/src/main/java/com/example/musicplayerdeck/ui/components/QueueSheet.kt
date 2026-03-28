@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.musicplayerdeck.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import com.example.musicplayerdeck.ui.theme.AppCard
import com.example.musicplayerdeck.ui.theme.AppElevated
import com.example.musicplayerdeck.ui.theme.DividerColor
import com.example.musicplayerdeck.ui.theme.DividerLightColor
import com.example.musicplayerdeck.ui.theme.TealPrimary
import com.example.musicplayerdeck.ui.theme.TealPrimaryDark
import com.example.musicplayerdeck.ui.theme.TealPrimaryFaint
import com.example.musicplayerdeck.ui.theme.TextMuted
import com.example.musicplayerdeck.ui.theme.TextPrimary
import kotlinx.collections.immutable.ImmutableList

@Composable
fun QueueSheet(
    queue: ImmutableList<Song>,
    currentSong: Song,
    onSkipToIndex: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val currentIndex = remember(queue, currentSong) {
        queue.indexOfFirst { it.id == currentSong.id }
    }
    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) listState.scrollToItem(currentIndex)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = AppCard,
        scrimColor = Color.Black.copy(alpha = 0.5f),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 32.dp, height = 3.dp)
                    .background(DividerLightColor, RoundedCornerShape(2.dp))
            )
        }
    ) {
        Column(Modifier.fillMaxHeight(0.6f)) {
            // Header
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Playing Queue",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${queue.size} songs",
                    fontSize = 13.sp,
                    color = TextMuted
                )
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(DividerColor)
            )

            LazyColumn(state = listState) {
                itemsIndexed(queue) { index, song ->
                    val isCurrentSong = index == currentIndex
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(if (isCurrentSong) TealPrimaryFaint else Color.Transparent)
                            .clickable { onSkipToIndex(index) }
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AppElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.MusicNote, null, Modifier.size(18.dp), tint = TextMuted)
                                AsyncImage(
                                    model = song.albumArtUri,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                if (isCurrentSong) {
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.Black.copy(alpha = 0.45f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AnimatedEqualizer(
                                            modifier = Modifier.size(18.dp),
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
                                    fontSize = 14.sp,
                                    color = if (isCurrentSong) TealPrimary else TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    song.artist,
                                    fontSize = 12.sp,
                                    color = if (isCurrentSong) TealPrimaryDark else TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Box(
                            Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(start = 68.dp)
                                .height(0.5.dp)
                                .background(DividerColor)
                        )
                    }
                }
                item { Spacer(Modifier.navigationBarsPadding()) }
            }
        }
    }
}
