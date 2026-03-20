package com.example.musicplayerdeck.data.repository

import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.musicplayerdeck.data.model.Playlist
import com.example.musicplayerdeck.data.model.Song
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.json.JSONArray
import org.json.JSONObject

fun savePlaylists(prefs: SharedPreferences, pls: ImmutableList<Playlist>) {
    val ja = JSONArray()
    for (p in pls) {
        val o = JSONObject()
        o.put("name", p.name)
        val ids = JSONArray()
        for (id in p.songIds) ids.put(id)
        o.put("songIds", ids)
        if (p.sourceFolders.isNotEmpty()) {
            val folders = JSONArray()
            for (f in p.sourceFolders) folders.put(f)
            o.put("sourceFolders", folders)
        }
        ja.put(o)
    }
    prefs.edit {
        putString("playlists_json", ja.toString())
        remove("playlist_names")
    }
}

fun loadPlaylists(prefs: SharedPreferences): ImmutableList<Playlist> {
    val json = prefs.getString("playlists_json", null)
    if (json == null) {
        val ln = prefs.getStringSet("playlist_names", null) ?: return persistentListOf()
        val m = ln.map { n ->
            val idSet = prefs.getStringSet("playlist_ids_$n", emptySet()) ?: emptySet()
            Playlist(n, idSet.mapNotNull { it.toLongOrNull() }.toImmutableList())
        }.toImmutableList()
        savePlaylists(prefs, m)
        return m
    }
    return try {
        val ja = JSONArray(json)
        val result = mutableListOf<Playlist>()
        for (i in 0 until ja.length()) {
            val o = ja.getJSONObject(i)
            val ids = o.getJSONArray("songIds")
            val idList = mutableListOf<Long>()
            for (j in 0 until ids.length()) {
                idList.add(ids.getLong(j))
            }
            val sourceFolders = if (o.has("sourceFolders")) {
                val fa = o.getJSONArray("sourceFolders")
                val fl = mutableListOf<String>()
                for (j in 0 until fa.length()) {
                    fl.add(fa.getString(j))
                }
                fl.toImmutableList()
            } else {
                persistentListOf()
            }
            result.add(
                Playlist(
                    name = o.getString("name"),
                    songIds = idList.toImmutableList(),
                    sourceFolders = sourceFolders
                )
            )
        }
        result.toImmutableList()
    } catch (_: Exception) {
        persistentListOf()
    }
}

fun syncPlaylistsWithFolders(
    playlists: ImmutableList<Playlist>,
    songs: ImmutableList<Song>
): Pair<ImmutableList<Playlist>, Int> {
    var totalAdded = 0
    val updated = playlists.map { playlist ->
        if (playlist.sourceFolders.isEmpty()) {
            playlist
        } else {
            val folderSongIds = songs
                .filter { it.folder in playlist.sourceFolders }
                .map { it.id }
                .toSet()
            val existingIds = playlist.songIds.toSet()
            val newIds = folderSongIds - existingIds
            if (newIds.isNotEmpty()) {
                totalAdded += newIds.size
                playlist.copy(
                    songIds = (playlist.songIds + newIds.toList()).toImmutableList()
                )
            } else {
                playlist
            }
        }
    }.toImmutableList()
    return Pair(updated, totalAdded)
}