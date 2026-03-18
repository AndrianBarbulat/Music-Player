package com.example.musicplayerdeck.data.repository

import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

data class PlayRecord(
    val songId: Long,
    val timestamp: Long
)

fun recordPlay(prefs: SharedPreferences, songId: Long) {
    // Save to recently played
    val history = loadRecentlyPlayed(prefs).toMutableList()
    history.removeAll { it.songId == songId }
    history.add(0, PlayRecord(songId, System.currentTimeMillis()))
    if (history.size > 200) history.subList(200, history.size).clear()

    val ja = JSONArray()
    for (r in history) {
        val o = JSONObject()
        o.put("songId", r.songId)
        o.put("timestamp", r.timestamp)
        ja.put(o)
    }
    prefs.edit { putString("recently_played", ja.toString()) }

    // Increment play count
    val counts = loadPlayCounts(prefs).toMutableMap()
    counts[songId] = (counts[songId] ?: 0) + 1
    val ca = JSONArray()
    for ((id, count) in counts) {
        val o = JSONObject()
        o.put("songId", id)
        o.put("count", count)
        ca.put(o)
    }
    prefs.edit { putString("play_counts", ca.toString()) }
}

fun loadRecentlyPlayed(prefs: SharedPreferences): List<PlayRecord> {
    val json = prefs.getString("recently_played", null) ?: return emptyList()
    return try {
        val ja = JSONArray(json)
        (0 until ja.length()).map { i ->
            val o = ja.getJSONObject(i)
            PlayRecord(o.getLong("songId"), o.getLong("timestamp"))
        }
    } catch (_: Exception) {
        emptyList()
    }
}

fun loadPlayCounts(prefs: SharedPreferences): Map<Long, Int> {
    val json = prefs.getString("play_counts", null) ?: return emptyMap()
    return try {
        val ja = JSONArray(json)
        val map = mutableMapOf<Long, Int>()
        for (i in 0 until ja.length()) {
            val o = ja.getJSONObject(i)
            map[o.getLong("songId")] = o.getInt("count")
        }
        map
    } catch (_: Exception) {
        emptyMap()
    }
}