package com.example.musicplayerdeck.data.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.LruCache
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.example.musicplayerdeck.data.model.Song
import okio.Buffer
import java.io.File

/**
 * Coil [Fetcher] that extracts album art directly from audio file metadata using
 * [MediaMetadataRetriever.getEmbeddedPicture], bypassing MediaStore's thumbnail cache.
 *
 * Resolution order:
 *  1. In-memory LRU cache (keyed by content URI string)
 *  2. Embedded picture from ID3/Vorbis/MP4 metadata via [MediaMetadataRetriever]
 *  3. cover.jpg / folder.jpg / album.jpg (+ .png variants) in the same directory
 *  4. Throws [NoSuchElementException] → Coil shows the error/placeholder drawable
 */
class EmbeddedArtFetcher(
    private val song: Song,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val cacheKey = song.uri.toString()

        // 1. In-memory cache hit
        ArtCache.get(cacheKey)?.let { return bytesToResult(it) }

        // 2. Embedded metadata
        val embedded = extractEmbeddedArt(options.context, song)
        if (embedded != null) {
            ArtCache.put(cacheKey, embedded)
            return bytesToResult(embedded)
        }

        // 3. Directory cover art file
        val dirCover = findDirectoryCoverArt(song.filePath)
        if (dirCover != null) {
            val bytes = dirCover.readBytes()
            ArtCache.put(cacheKey, bytes)
            return bytesToResult(bytes, DataSource.DISK)
        }

        // 4. Nothing found — Coil will apply the error/fallback placeholder
        throw NoSuchElementException("No cover art found for: ${song.title}")
    }

    // -------------------------------------------------------------------------

    private fun bytesToResult(
        bytes: ByteArray,
        source: DataSource = DataSource.MEMORY
    ): SourceResult = SourceResult(
        source = ImageSource(source = Buffer().write(bytes), context = options.context),
        mimeType = "image/jpeg",
        dataSource = source
    )

    private fun extractEmbeddedArt(context: Context, song: Song): ByteArray? {
        val mmr = MediaMetadataRetriever()
        return try {
            // Prefer file path — avoids an IPC round-trip through the content resolver
            if (song.filePath.isNotEmpty()) {
                mmr.setDataSource(song.filePath)
            } else {
                mmr.setDataSource(context, song.uri)
            }
            mmr.embeddedPicture
        } catch (_: Exception) {
            null
        } finally {
            mmr.release()
        }
    }

    private fun findDirectoryCoverArt(filePath: String): File? {
        if (filePath.isEmpty()) return null
        val dir = File(filePath).parentFile ?: return null
        return listOf("cover.jpg", "folder.jpg", "album.jpg", "cover.png", "folder.png")
            .map { File(dir, it) }
            .firstOrNull { it.exists() }
    }

    // -------------------------------------------------------------------------
    // Singleton in-memory LRU cache — 20 MB, shared across all fetcher instances
    // -------------------------------------------------------------------------

    companion object ArtCache {
        private const val MAX_BYTES = 20 * 1024 * 1024 // 20 MB

        private val cache = object : LruCache<String, ByteArray>(MAX_BYTES) {
            override fun sizeOf(key: String, value: ByteArray) = value.size
        }

        fun get(key: String): ByteArray? = cache.get(key)
        fun put(key: String, value: ByteArray) { cache.put(key, value) }
    }

    // -------------------------------------------------------------------------
    // Coil FetcherFactory — registered once in MusicPlayerApplication
    // -------------------------------------------------------------------------

    class Factory : Fetcher.Factory<Song> {
        override fun create(data: Song, options: Options, imageLoader: ImageLoader): Fetcher =
            EmbeddedArtFetcher(data, options)
    }
}
