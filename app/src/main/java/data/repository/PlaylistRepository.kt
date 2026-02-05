package com.example.musicplayerdeck.data.repository

import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.musicplayerdeck.data.model.Playlist
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
            result.add(Playlist(o.getString("name"), idList.toImmutableList()))
        }
        result.toImmutableList()
    } catch (_: Exception) {
        persistentListOf()
    }
}