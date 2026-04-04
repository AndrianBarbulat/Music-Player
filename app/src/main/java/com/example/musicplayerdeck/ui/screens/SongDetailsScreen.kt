package com.example.musicplayerdeck.ui.screens

import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.musicplayerdeck.data.model.Song
import com.example.musicplayerdeck.ui.theme.AppBackground
import com.example.musicplayerdeck.ui.theme.AppCard
import com.example.musicplayerdeck.ui.theme.AppElevated
import com.example.musicplayerdeck.ui.theme.DividerColor
import com.example.musicplayerdeck.ui.theme.TealPrimary
import com.example.musicplayerdeck.ui.theme.TextMuted
import com.example.musicplayerdeck.ui.theme.TextPrimary
import com.example.musicplayerdeck.ui.theme.TextSecondary
import com.example.musicplayerdeck.util.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SongDetailsScreen(
    song: Song,
    playCount: Int,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current

    var filePath by remember { mutableStateOf("") }
    var fileSize by remember { mutableStateOf("") }
    var mimeType by remember { mutableStateOf("") }
    var bitrate by remember { mutableStateOf("") }

    LaunchedEffect(song.id) {
        withContext(Dispatchers.IO) {
            val proj = arrayOf(
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.MIME_TYPE
            )
            try {
                ctx.contentResolver.query(song.uri, proj, null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        filePath = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)) ?: ""
                        val sizeBytes = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE))
                        fileSize = formatFileSize(sizeBytes)
                        mimeType = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)) ?: ""
                    }
                }
            } catch (_: Exception) {}

            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(ctx, song.uri)
                val raw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                if (!raw.isNullOrEmpty()) {
                    val kbps = (raw.toLongOrNull() ?: 0L) / 1000L
                    bitrate = "$kbps kbps"
                }
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    BackHandler(onBack = onDismiss)

    Box(
        Modifier
            .fillMaxSize()
            .background(AppBackground)
            .statusBarsPadding()
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Top bar
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack, "Back",
                        tint = TextSecondary
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    "Song Info",
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    color = TextPrimary
                )
            }

            // Header: album art + song info
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.MusicNote, null, Modifier.size(32.dp), tint = TextMuted)
                    AsyncImage(
                        model = song,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        song.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        song.artist,
                        fontSize = 14.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        song.album,
                        fontSize = 13.sp,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Playback info card
            DetailCard(label = "Playback") {
                DetailRow("Duration", formatDuration(song.duration))
                DetailRow(
                    "Play count",
                    if (playCount == 0) "Never played" else "$playCount ${if (playCount == 1) "play" else "plays"}"
                )
                DetailRow("Date added", formatDateAdded(song.dateAdded))
                DetailRow("Folder", song.folder, isLast = true)
            }

            Spacer(Modifier.height(12.dp))

            // File info card
            DetailCard(label = "File") {
                DetailRow("Format", mimeTypeToFormat(mimeType).ifEmpty { "—" })
                DetailRow("Bitrate", bitrate.ifEmpty { "—" })
                DetailRow("File size", fileSize.ifEmpty { "—" }, isLast = true)
            }

            if (filePath.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                DetailCard(label = "Location") {
                    DetailRow("File path", filePath, isLast = true)
                }
            }
        }
    }
}

@Composable
private fun DetailCard(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppCard)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Section label: uppercase, teal, 11sp, letter-spacing
        Text(
            label.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = TealPrimary,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        content()
    }
}

@Composable
private fun DetailRow(label: String, value: String, isLast: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = TextMuted,
            modifier = Modifier.weight(0.38f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            value,
            fontSize = 13.sp,
            color = TextPrimary,
            modifier = Modifier.weight(0.62f),
            softWrap = true,
            overflow = TextOverflow.Visible
        )
    }
    if (!isLast) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(DividerColor)
        )
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}

private fun mimeTypeToFormat(mimeType: String): String = when (mimeType.lowercase()) {
    "audio/mpeg", "audio/mp3" -> "MP3"
    "audio/flac", "audio/x-flac" -> "FLAC"
    "audio/ogg", "audio/vorbis" -> "OGG"
    "audio/aac", "audio/x-aac" -> "AAC"
    "audio/wav", "audio/x-wav" -> "WAV"
    "audio/opus" -> "OPUS"
    "audio/mp4", "audio/m4a", "audio/x-m4a" -> "M4A"
    "audio/amr" -> "AMR"
    "audio/3gpp" -> "3GP"
    else -> mimeType.substringAfterLast('/').uppercase().ifEmpty { "Unknown" }
}

private fun formatDateAdded(timestamp: Long): String {
    if (timestamp == 0L) return "—"
    return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp * 1000L))
}
