package com.example.musicplayerdeck.util

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberDominantColor(uri: Uri?, defaultColor: Color): Color {
    var color by remember { mutableStateOf(defaultColor) }
    val ctx = LocalContext.current

    LaunchedEffect(uri) {
        if (uri == null) {
            color = defaultColor
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                val loader = ImageLoader(ctx)
                val req = ImageRequest.Builder(ctx)
                    .data(uri)
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