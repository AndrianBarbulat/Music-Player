package com.example.musicplayerdeck.data.repository

import android.content.SharedPreferences
import androidx.core.content.edit

private const val KEY_QUEUE_IDS = "pb_queue_ids"
private const val KEY_QUEUE_INDEX = "pb_queue_index"
private const val KEY_POSITION_MS = "pb_position_ms"
private const val KEY_SONG_ID = "pb_song_id"

data class SavedPlaybackState(
    val queueIds: List<Long>,
    val queueIndex: Int,
    val positionMs: Long,
    val songId: Long,
)

fun savePlaybackState(
    prefs: SharedPreferences,
    queueIds: List<Long>,
    queueIndex: Int,
    positionMs: Long,
    songId: Long,
) {
    prefs.edit {
        putString(KEY_QUEUE_IDS, queueIds.joinToString(","))
        putInt(KEY_QUEUE_INDEX, queueIndex)
        putLong(KEY_POSITION_MS, positionMs)
        putLong(KEY_SONG_ID, songId)
    }
}

fun loadSavedPlaybackState(prefs: SharedPreferences): SavedPlaybackState? {
    val idsStr = prefs.getString(KEY_QUEUE_IDS, null) ?: return null
    val queueIds = idsStr.split(",").mapNotNull { it.toLongOrNull() }
    if (queueIds.isEmpty()) return null
    return SavedPlaybackState(
        queueIds = queueIds,
        queueIndex = prefs.getInt(KEY_QUEUE_INDEX, 0),
        positionMs = prefs.getLong(KEY_POSITION_MS, 0L),
        songId = prefs.getLong(KEY_SONG_ID, -1L),
    )
}

fun clearSavedPlaybackState(prefs: SharedPreferences) {
    prefs.edit {
        remove(KEY_QUEUE_IDS)
        remove(KEY_QUEUE_INDEX)
        remove(KEY_POSITION_MS)
        remove(KEY_SONG_ID)
    }
}
