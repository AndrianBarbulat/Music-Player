package com.example.musicplayerdeck.util

import com.example.musicplayerdeck.data.model.Song

fun formatDuration(ms: Int): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}

fun formatDurationLong(ms: Long): String = formatDuration(ms.toInt())

fun formatTotalDuration(songs: List<Song>): String {
    val totalMs = songs.sumOf { it.duration.toLong() }
    val totalMin = totalMs / 60000
    return if (totalMin >= 60) {
        val h = totalMin / 60
        val m = totalMin % 60
        "${h}hr ${m}min"
    } else {
        "${totalMin}min"
    }
}