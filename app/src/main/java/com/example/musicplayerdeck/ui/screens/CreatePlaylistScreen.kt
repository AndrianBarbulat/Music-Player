@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.example.musicplayerdeck.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.musicplayerdeck.data.model.Song
import com.example.musicplayerdeck.ui.theme.AppBackground
import com.example.musicplayerdeck.util.formatDuration
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay

@Composable
fun CreatePlaylistScreen(
    allSongs: ImmutableList<Song>,
    folders: ImmutableList<Pair<String, Int>>,
    onDismiss: () -> Unit,
    onSave: (String, Set<Long>, Set<String>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var dq by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var selectedFolders by remember { mutableStateOf(setOf<String>()) }
    var showFolderDialog by remember { mutableStateOf(false) }
    var tempFolderSelection by remember { mutableStateOf(setOf<String>()) }
    var showAutoSyncInfo by remember { mutableStateOf(false) }

    LaunchedEffect(query) { delay(300); dq = query }
    LaunchedEffect(showFolderDialog) {
        if (showFolderDialog) tempFolderSelection = selectedFolders
    }

    val filtered: ImmutableList<Song> = remember(allSongs, dq) {
        if (dq.isBlank()) allSongs
        else allSongs.filter {
            it.title.contains(dq, ignoreCase = true) ||
                    it.artist.contains(dq, ignoreCase = true)
        }.toImmutableList()
    }

    val groupedSongs: Map<String, List<Song>> = remember(filtered) {
        filtered.groupBy { it.artist }.toSortedMap()
    }

    val previewSongs: List<Song> = remember(selected, allSongs) {
        selected.take(5).mapNotNull { id -> allSongs.find { it.id == id } }
    }

    BackHandler { onDismiss() }

    // ─── Folder multi-select dialog ─────────────────────────────────────────
    if (showFolderDialog) {
        AlertDialog(
            onDismissRequest = { showFolderDialog = false },
            shape = RoundedCornerShape(20.dp),
            title = {
                Column {
                    Text("Add by Folder", fontWeight = FontWeight.Bold)
                    Text(
                        "Songs from selected folders auto-sync when new files are added",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    // Select All row
                    item(key = "select_all") {
                        val allNames = folders.map { it.first }.toSet()
                        val allChecked = allNames.isNotEmpty() && allNames.all { tempFolderSelection.contains(it) }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    tempFolderSelection = if (allChecked) emptySet() else allNames
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = allChecked,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Default.SelectAll, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            Text("Select All", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                        Spacer(Modifier.height(4.dp))
                    }

                    // Individual folders
                    items(items = folders, key = { it.first }) { (fn, count) ->
                        val isChecked = tempFolderSelection.contains(fn)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    tempFolderSelection =
                                        if (isChecked) tempFolderSelection - fn else tempFolderSelection + fn
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Default.Folder, null,
                                Modifier.size(22.dp),
                                tint = if (isChecked) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    fn,
                                    fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isChecked) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                                Text(
                                    "$count songs",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isChecked) {
                                Icon(
                                    Icons.Default.CheckCircle, null,
                                    Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newIds = tempFolderSelection.flatMap { fn ->
                            allSongs.filter { it.folder == fn }.map { it.id }
                        }.toSet()
                        selected = selected + newIds
                        selectedFolders = tempFolderSelection
                        showFolderDialog = false
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        if (tempFolderSelection.isEmpty()) "Done"
                        else "Add ${tempFolderSelection.size} folder${if (tempFolderSelection.size != 1) "s" else ""}",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showFolderDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    // ─── Screen ─────────────────────────────────────────────────────────────
    Box(
        Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {

                // Header row
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Create Playlist",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                // Scrollable content
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                ) {

                    // ── Name card ──────────────────────────────────────────
                    item(key = "name_card") {
                        Spacer(Modifier.height(4.dp))
                        Card(
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                                Text(
                                    "PLAYLIST NAME",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.sp
                                )
                                Spacer(Modifier.height(8.dp))
                                Box {
                                    if (name.isEmpty()) {
                                        Text(
                                            "Untitled playlist…",
                                            style = MaterialTheme.typography.headlineSmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                        )
                                    }
                                    BasicTextField(
                                        value = name,
                                        onValueChange = { name = it },
                                        textStyle = MaterialTheme.typography.headlineSmall.copy(
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    // ── Search bar ─────────────────────────────────────────
                    item(key = "search_bar") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(28.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Search, null,
                                Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(10.dp))
                            Box(Modifier.weight(1f)) {
                                if (query.isEmpty()) {
                                    Text(
                                        "Search songs, artists…",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                                BasicTextField(
                                    value = query,
                                    onValueChange = { query = it },
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                                )
                            }
                            if (query.isNotEmpty()) {
                                Spacer(Modifier.width(4.dp))
                                IconButton(
                                    onClick = { query = "" },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close, "Clear search",
                                        Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    // ── Controls row: counter chip + Add by Folder ─────────
                    item(key = "controls_row") {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SuggestionChip(
                                onClick = {},
                                label = {
                                    Text(
                                        "${selected.size} / ${allSongs.size} songs",
                                        fontSize = 12.sp
                                    )
                                },
                                icon = {
                                    Icon(Icons.Default.MusicNote, null, Modifier.size(14.dp))
                                }
                            )
                            TextButton(onClick = { showFolderDialog = true }) {
                                Icon(
                                    Icons.Default.Folder, null,
                                    Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "Add by Folder",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    // ── Auto-sync folder chips ─────────────────────────────
                    if (selectedFolders.isNotEmpty()) {
                        item(key = "folder_chips") {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Auto-sync",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { showAutoSyncInfo = !showAutoSyncInfo },
                                        modifier = Modifier.size(22.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Info, "What is auto-sync?",
                                            Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (showAutoSyncInfo) {
                                    Text(
                                        "New songs added to these folders will be automatically included when you refresh the library.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 15.sp,
                                        modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
                                    )
                                }
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    items(selectedFolders.toList(), key = { it }) { folder ->
                                        InputChip(
                                            selected = true,
                                            onClick = {
                                                selectedFolders = selectedFolders - folder
                                            },
                                            label = {
                                                Text(
                                                    folder,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    fontSize = 12.sp
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.Folder, null, Modifier.size(14.dp))
                                            },
                                            trailingIcon = {
                                                Icon(Icons.Default.Close, "Remove folder", Modifier.size(12.dp))
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── Grouped song list with sticky headers ──────────────
                    groupedSongs.forEach { (artistName, songsInGroup) ->
                        stickyHeader(key = "header_$artistName") {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                                    .padding(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    artistName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        items(songsInGroup, key = { it.id }) { song ->
                            val isSelected = selected.contains(song.id)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clickable {
                                        selected =
                                            if (isSelected) selected - song.id
                                            else selected + song.id
                                    },
                                shape = RoundedCornerShape(12.dp),
                                elevation = CardDefaults.cardElevation(
                                    defaultElevation = if (isSelected) 3.dp else 1.dp
                                ),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected)
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                    else MaterialTheme.colorScheme.surface
                                ),
                                border = if (isSelected)
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                                else null
                            ) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Album art thumbnail
                                    Card(
                                        Modifier.size(44.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        elevation = CardDefaults.cardElevation(2.dp)
                                    ) {
                                        Box(
                                            Modifier
                                                .fillMaxSize()
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.MusicNote, null,
                                                Modifier.size(22.dp),
                                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                            )
                                            AsyncImage(
                                                model = song,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            song.title,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            formatDuration(song.duration),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        if (isSelected) Icons.Default.CheckCircle
                                        else Icons.Default.RadioButtonUnchecked,
                                        if (isSelected) "Selected" else "Select",
                                        Modifier.size(22.dp),
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        }
                    }

                    item(key = "bottom_spacer") { Spacer(Modifier.height(8.dp)) }
                }

                // ── Sticky bottom: preview + save ──────────────────────────
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f))
                        .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp)
                ) {
                    // Selected songs preview thumbnails
                    if (selected.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            items(previewSongs, key = { it.id }) { song ->
                                Card(
                                    Modifier.size(40.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    elevation = CardDefaults.cardElevation(2.dp)
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.MusicNote, null,
                                            Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                        )
                                        AsyncImage(
                                            model = song,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                            }
                            if (selected.size > 5) {
                                item(key = "overflow") {
                                    Box(
                                        Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "+${selected.size - 5}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            item(key = "count_label") {
                                Text(
                                    "${selected.size} song${if (selected.size != 1) "s" else ""}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (name.isNotBlank()) onSave(name.trim(), selected, selectedFolders)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        enabled = name.isNotBlank() && selected.isNotEmpty(),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when {
                                name.isBlank() -> "Enter a playlist name"
                                selected.isEmpty() -> "Select songs to save"
                                else -> "Save Playlist (${selected.size})"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
}
