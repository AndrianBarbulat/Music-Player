package com.example.musicplayerdeck.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

@Composable
fun AnimatedEqualizer(
    modifier: Modifier = Modifier,
    isPlaying: Boolean,
    barColor: Color = MaterialTheme.colorScheme.primary
) {
    val transition = rememberInfiniteTransition(label = "eq")

    val bar0 = transition.animateFloat(
        initialValue = 0.15f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "bar0"
    )
    val bar1 = transition.animateFloat(
        initialValue = 0.15f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 470, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "bar1"
    )
    val bar2 = transition.animateFloat(
        initialValue = 0.15f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 590, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "bar2"
    )

    val bars = listOf(bar0, bar1, bar2)

    Canvas(modifier = modifier) {
        val bw = size.width / 5f
        val gap = bw * 0.6f
        val totalW = 3 * bw + 2 * gap
        val startX = (size.width - totalW) / 2f

        bars.forEachIndexed { i, anim ->
            val h = if (isPlaying) anim.value * size.height else size.height * 0.25f
            drawRoundRect(
                color = barColor,
                topLeft = Offset(startX + i * (bw + gap), size.height - h),
                size = Size(bw, h),
                cornerRadius = CornerRadius(bw / 2f)
            )
        }
    }
}