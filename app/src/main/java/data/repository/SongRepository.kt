package com.example.musicplayerdeck.data.repository

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.core.net.toUri
import com.example.musicplayerdeck.data.model.Song
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import java.io.File

fun fetchSongs(ctx: Context): ImmutableList<Song> {
    val songs = mutableListOf<Song>()
    try {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val proj = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATA
        )
        val hasRP = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        if (hasRP) proj.add(MediaStore.Audio.Media.RELATIVE_PATH)

        val sel = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sort = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        ctx.contentResolver.query(collection, proj.toTypedArray(), sel, null, sort)?.use { c ->
            val idC = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albIdC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val dataC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val rpC = if (hasRP) c.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH) else -1

            while (c.moveToNext()) {
                try {
                    val id = c.getLong(idC)
                    val title = c.getString(titleC) ?: "Unknown Track"
                    val artist = c.getString(artistC) ?: "Unknown Artist"
                    val album = c.getString(albumC) ?: "Unknown Album"
                    val dur = c.getInt(durC)
                    val albId = c.getLong(albIdC)
                    val path = c.getString(dataC) ?: ""

                    val folder = if (rpC >= 0) {
                        val rp = c.getString(rpC) ?: ""
                        rp.trimEnd('/').substringAfterLast('/').ifEmpty { "Unknown" }
                    } else {
                        try {
                            if (path.isNotEmpty()) File(path).parentFile?.name ?: "Unknown"
                            else "Unknown"
                        } catch (_: Exception) {
                            "Unknown"
                        }
                    }

                    val uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                    )
                    val artUri = try {
                        if (albId > 0) ContentUris.withAppendedId(
                            "content://media/external/audio/albumart".toUri(), albId
                        ) else null
                    } catch (_: Exception) {
                        null
                    }

                    songs.add(Song(id, title, artist, album, dur, uri, artUri, folder))
                } catch (_: Exception) {
                    // skip
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return persistentListOf()
    }
    return songs.toImmutableList()
}