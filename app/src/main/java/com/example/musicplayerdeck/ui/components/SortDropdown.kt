package com.example.musicplayerdeck.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicplayerdeck.data.model.Song

enum class SortOption(val label: String) {
    NAME_ASC("Name A-Z"),
    NAME_DESC("Name Z-A"),
    ARTIST_ASC("Artist A-Z"),
    ARTIST_DESC("Artist Z-A"),
    DURATION_SHORT("Shortest first"),
    DURATION_LONG("Longest first"),
    RECENTLY_ADDED("Recently added"),
    PLAY_COUNT("Most played")
}

@Composable
fun SortDropdown(
    currentSort: SortOption,
    showPlayCount: Boolean = false,
    onSortSelected: (SortOption) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        TextButton(onClick = { expanded = true }) {
            Icon(
                Icons.Default.SortByAlpha, null,
                Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(4.dp))
            Text(
                currentSort.label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = com.example.musicplayerdeck.ui.theme.AppCard
        ) {
            SortOption.entries.forEach { option ->
                if (option == SortOption.PLAY_COUNT && !showPlayCount) return@forEach
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                option.label,
                                fontWeight = if (option == currentSort) FontWeight.Bold else FontWeight.Normal,
                                color = if (option == currentSort) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    onClick = {
                        onSortSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

fun sortSongs(
    songs: List<Song>,
    sortOption: SortOption,
    playCounts: Map<Long, Int> = emptyMap()
): List<Song> {
    return when (sortOption) {
        SortOption.NAME_ASC -> songs.sortedBy { it.title.lowercase() }
        SortOption.NAME_DESC -> songs.sortedByDescending { it.title.lowercase() }
        SortOption.ARTIST_ASC -> songs.sortedBy { it.artist.lowercase() }
        SortOption.ARTIST_DESC -> songs.sortedByDescending { it.artist.lowercase() }
        SortOption.DURATION_SHORT -> songs.sortedBy { it.duration }
        SortOption.DURATION_LONG -> songs.sortedByDescending { it.duration }
        SortOption.RECENTLY_ADDED -> songs.sortedByDescending { it.dateAdded }
        SortOption.PLAY_COUNT -> songs.sortedByDescending { playCounts[it.id] ?: 0 }
    }
}