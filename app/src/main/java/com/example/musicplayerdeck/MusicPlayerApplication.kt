package com.example.musicplayerdeck

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.musicplayerdeck.data.util.EmbeddedArtFetcher

/**
 * Application entry point.
 *
 * Registers a singleton Coil [ImageLoader] that uses [EmbeddedArtFetcher] for every
 * [Song] model passed to [AsyncImage]. All other data types (URLs, resource IDs, etc.)
 * are handled by Coil's built-in fetchers as normal.
 */
class MusicPlayerApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                add(EmbeddedArtFetcher.Factory())
            }
            .build()
}
