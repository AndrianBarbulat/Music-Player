package com.example.musicplayerdeck.data.model

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList

@Stable
data class Playlist(
    val name: String,
    val songIds: ImmutableList<Long>
)