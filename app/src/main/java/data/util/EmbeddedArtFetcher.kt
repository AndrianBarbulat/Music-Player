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
 *  2a. Embedded picture via [MediaMetadataRetriever.setDataSource] with file path (fast, no IPC)
 *  2b. Same retriever retried with the content URI — fixes EMUI scoped-storage bug where
 *      file-path access silently returns null even for files with embedded art
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
        // Attempt 1: file path (faster, no IPC) — silently returns null on EMUI due to
        // scoped-storage enforcement even when the path is valid, so we always try the
        // content URI fallback when this yields nothing.
        if (song.filePath.isNotEmpty()) {
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(song.filePath)
                val bytes = mmr.embeddedPicture
                if (bytes != null) return bytes
            } catch (_: Exception) {
                // path inaccessible — fall through to URI attempt
            } finally {
                mmr.release()
            }
        }

        // Attempt 2: content URI — brokers access through Android's content resolver,
        // bypassing EMUI's file-path restrictions on Android 10+.
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(context, song.uri)
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
