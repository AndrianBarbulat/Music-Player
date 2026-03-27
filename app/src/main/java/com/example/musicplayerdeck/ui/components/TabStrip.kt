package com.example.musicplayerdeck.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicplayerdeck.ui.theme.AppCard
import com.example.musicplayerdeck.ui.theme.TealPrimary
import com.example.musicplayerdeck.ui.theme.TextMuted
import kotlinx.collections.immutable.ImmutableList

@Composable
fun TabStrip(
    tabs: ImmutableList<String>,
    tabCounts: ImmutableList<Int>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(tabs) { i, tab ->
            val selected = selectedIndex == i
            val bgColor by animateColorAsState(
                targetValue = if (selected) AppCard else Color.Transparent,
                animationSpec = tween(durationMillis = 200),
                label = "tabBg",
            )
            val textColor by animateColorAsState(
                targetValue = if (selected) TealPrimary else TextMuted,
                animationSpec = tween(durationMillis = 200),
                label = "tabText",
            )
            Column(
                Modifier
                    .background(bgColor, RoundedCornerShape(10.dp))
                    .clickable { onTabSelected(i) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(tab, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = textColor)
                val count = tabCounts.getOrElse(i) { 0 }
                Text(
                    count.toString(),
                    fontSize = 9.sp,
                    color = textColor.copy(alpha = if (selected) 1f else 0.6f),
                    lineHeight = 11.sp,
                )
            }
        }
    }
}
