package com.example.musicplayerdeck.data.model

import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Int,
    val uri: Uri,
    val albumArtUri: Uri?,
    val folder: String,
    val dateAdded: Long = 0L,
    val filePath: String = ""
)