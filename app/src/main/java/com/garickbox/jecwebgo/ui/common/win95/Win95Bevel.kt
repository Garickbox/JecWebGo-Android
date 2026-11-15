package com.garickbox.jecwebgo.ui.common.win95

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garickbox.jecwebgo.ui.theme.Win95Colors
import androidx.compose.foundation.layout.padding

/**
 * Win95 double-bevel modifier (2 слоя: outer + inner).
 * raised = кнопка "выпуклая" (top-left белый, bottom-right чёрный)
 * sunken = кнопка "вдавленная" (наоборот) — для textfield, progress, active button
 */
fun Modifier.win95Bevel(raised: Boolean = true, thickness: Dp = 2.dp): Modifier = this.drawBehind {
    val s = thickness.toPx()
    val w = size.width
    val h = size.height
    val outerTL = if (raised) Win95Colors.White else Win95Colors.Black
    val outerBR = if (raised) Win95Colors.Black else Win95Colors.White
    val innerTL = if (raised) Win95Colors.GrayLight else Win95Colors.Gray
    val innerBR = if (raised) Win95Colors.Gray else Win95Colors.GrayLight

    // Outer bevel (top-left)
    drawLine(outerTL, Offset(0f, 0f), Offset(w, 0f), strokeWidth = s)
    drawLine(outerTL, Offset(0f, 0f), Offset(0f, h), strokeWidth = s)
    // Outer bevel (bottom-right)
    drawLine(outerBR, Offset(0f, h), Offset(w, h), strokeWidth = s)
    drawLine(outerBR, Offset(w, 0f), Offset(w, h), strokeWidth = s)

    // Inner bevel (top-left)
    drawLine(innerTL, Offset(s, s), Offset(w - s, s), strokeWidth = s)
    drawLine(innerTL, Offset(s, s), Offset(s, h - s), strokeWidth = s)
    // Inner bevel (bottom-right)
    drawLine(innerBR, Offset(s, h - s), Offset(w - s, h - s), strokeWidth = s)
    drawLine(innerBR, Offset(w - s, s), Offset(w - s, h - s), strokeWidth = s)
}

/** Простой 1px border (для list-row, folder) */
fun Modifier.win95Border(raised: Boolean = true): Modifier = this.drawBehind {
    val s = 1.dp.toPx()
    val w = size.width
    val h = size.height
    val tl = if (raised) Win95Colors.White else Win95Colors.Gray
    val br = if (raised) Win95Colors.Gray else Win95Colors.White
    drawLine(tl, Offset(0f, 0f), Offset(w, 0f), strokeWidth = s)
    drawLine(tl, Offset(0f, 0f), Offset(0f, h), strokeWidth = s)
    drawLine(br, Offset(0f, h), Offset(w, h), strokeWidth = s)
    drawLine(br, Offset(w, 0f), Offset(w, h), strokeWidth = s)
}

/** Фон окна с двойным bevel (всё окно Win95) */
@Composable
fun Win95Window(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .background(Win95Colors.Face)
            .win95Bevel(raised = true, thickness = 2.dp)
    ) {
        Box(Modifier.fillMaxSize().padding(2.dp)) { content() }
    }
}

