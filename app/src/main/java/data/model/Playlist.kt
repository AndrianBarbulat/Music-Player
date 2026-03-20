package com.example.musicplayerdeck.data.model

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class Playlist(
    val name: String,
    val songIds: ImmutableList<Long>,
    val sourceFolders: ImmutableList<String> = persistentListOf()
)