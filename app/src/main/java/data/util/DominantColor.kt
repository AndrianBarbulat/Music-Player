package com.example.musicplayerdeck.util

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.musicplayerdeck.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberDominantColor(song: Song?, defaultColor: Color): Color {
    var color by remember { mutableStateOf(defaultColor) }
    val ctx = LocalContext.current

    LaunchedEffect(song?.uri) {
        if (song == null) {
            color = defaultColor
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                val loader = ctx.imageLoader
                val req = ImageRequest.Builder(ctx)
                    .data(song)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(req)
                if (result is SuccessResult) {
                    val drawable = result.drawable
                    if (drawable is BitmapDrawable) {
                        val bmp: Bitmap = drawable.bitmap
                        val palette = androidx.palette.graphics.Palette.from(bmp).generate()
                        val swatch = palette.dominantSwatch
                        if (swatch != null) {
                            color = Color(swatch.rgb)
                        }
                    }
                }
            } catch (_: Exception) {
                color = defaultColor
            }
        }
    }
    return color
}