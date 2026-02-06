@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.musicplayerdeck.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicplayerdeck.data.model.Song
import com.example.musicplayerdeck.ui.theme.DarkMintGradient
import com.example.musicplayerdeck.ui.theme.MintGradient
import com.example.musicplayerdeck.util.formatDuration
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay

@Composable
fun CreatePlaylistScreen(
    allSongs: ImmutableList<Song>,
    folders: ImmutableList<Pair<String, Int>>,
    onDismiss: () -> Unit,
    onSave: (String, Set<Long>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var dq by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<Long>()) }

    LaunchedEffect(query) { delay(300); dq = query }

    val filtered: ImmutableList<Song> = remember(allSongs, dq) {
        if (dq.isBlank()) allSongs
        else allSongs.filter {
            it.title.contains(dq, ignoreCase = true) ||
                    it.artist.contains(dq, ignoreCase = true)
        }.toImmutableList()
    }

    val gradient = if (isSystemInDarkTheme()) DarkMintGradient else MintGradient

    BackHandler { onDismiss() }

    Surface(Modifier.fillMaxSize(), color = Color.Transparent) {
        Box(
            Modifier
                .fillMaxSize()
                .background(gradient)
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Text(
                        "Create New Playlist",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist Name") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary
                    )
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search Songs to Add") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary
                    )
                )

                Spacer(Modifier.height(16.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Songs (${selected.size} selected)",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 16.sp
                    )

                    var showFD by remember { mutableStateOf(false) }
                    TextButton(onClick = { showFD = true }) {
                        Text(
                            "Add by Folder",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (showFD) {
                        AlertDialog(
                            onDismissRequest = { showFD = false },
                            title = { Text("Select Folder", fontWeight = FontWeight.Bold) },
                            text = {
                                LazyColumn {
                                    items(items = folders) { (fn, c) ->
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    val folderSongIds = allSongs
                                                        .filter { it.folder == fn }
                                                        .map { it.id }
                                                        .toSet()
                                                    selected = selected + folderSongIds
                                                    showFD = false
                                                }
                                                .padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Folder, null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(Modifier.width(16.dp))
                                            Text(
                                                "$fn ($c songs)",
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showFD = false }) {
                                    Text("Cancel", fontWeight = FontWeight.Bold)
                                }
                            }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                LazyColumn(Modifier.weight(1f)) {
                    items(items = filtered) { song ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    selected = if (selected.contains(song.id)) {
                                        selected - song.id
                                    } else {
                                        selected + song.id
                                    }
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selected.contains(song.id),
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    song.title,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    song.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                formatDuration(song.duration),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = { if (name.isNotBlank()) onSave(name, selected) },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    enabled = name.isNotBlank() && selected.isNotEmpty()
                ) {
                    Text("Save Playlist", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}