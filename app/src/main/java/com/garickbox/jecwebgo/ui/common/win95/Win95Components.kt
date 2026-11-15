package com.garickbox.jecwebgo.ui.common.win95

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garickbox.jecwebgo.ui.theme.Win95Colors
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawBehind

/** Win95 кнопка с pressed state и offset при нажатии */
@Composable
fun Win95Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    raised: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val effectiveRaised = raised && !pressed

    Box(
        modifier = modifier
            .background(Win95Colors.Face)
            .win95Bevel(raised = effectiveRaised, thickness = 2.dp)
            .clip(RoundedCornerShape(0.dp))
            .then(
                if (enabled) {
                    Modifier.androidxClickable(interaction) { onClick() }
                } else Modifier
            )
            .padding(
                start = if (pressed) 4.dp else 2.dp,
                top = if (pressed) 4.dp else 2.dp,
                end = 2.dp,
                bottom = if (pressed) 2.dp else 4.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

/** Вспомогательный clickable без ripple (Win95 не имел ripple) */
private fun Modifier.androidxClickable(
    interaction: MutableInteractionSource,
    onClick: () -> Unit
): Modifier = this.clickable(
    interactionSource = interaction,
    indication = null,
    onClick = onClick
)

/** Win95 checkbox — 13×13 белый квадрат с вдавленным bevel и галочкой */
@Composable
fun Win95Checkbox(
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(13.dp)
            .background(Win95Colors.White)
            .win95Bevel(raised = false, thickness = 1.dp)
            .androidxClickable(remember { MutableInteractionSource() }) { onToggle() },
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Text(
                "✓",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Win95Colors.Black,
                fontFamily = FontFamily.Monospace,
                lineHeight = 10.sp
            )
        }
    }
}

/** Win95 progress bar — repeating striped pattern (как в HTML) */
@Composable
fun Win95Progress(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 16.dp
) {
    val pct = progress.coerceIn(0f, 1f)
    val periodDp = 10.dp
    val navyFraction = 0.8f
    Box(
        modifier = modifier
            .height(height)
            .background(Win95Colors.White)
            .win95Bevel(raised = false, thickness = 1.dp)
            .padding(1.dp)
            .drawBehind {
                if (pct <= 0f) return@drawBehind
                val fillWidth = size.width * pct
                val periodPx = periodDp.toPx()
                val navyPx = periodPx * navyFraction
                var x = 0f
                while (x < fillWidth) {
                    val w = minOf(navyPx, fillWidth - x)
                    drawRect(
                        color = Win95Colors.Navy,
                        topLeft = androidx.compose.ui.geometry.Offset(x, 0f),
                        size = androidx.compose.ui.geometry.Size(w, size.height)
                    )
                    x += periodPx
                }
            }
    )
}

/** Win95 title bar — gradient Navy → NavyLight */
@Composable
fun Win95TitleBar(
    title: String,
    icon: String = "",
    onClose: (() -> Unit)? = null,
    onMinimize: (() -> Unit)? = null,
    onMaximize: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .background(
                Brush.horizontalGradient(listOf(Win95Colors.Navy, Win95Colors.NavyLight))
            )
            .padding(horizontal = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon.isNotBlank()) {
            Text(icon, fontSize = 12.sp, color = Win95Colors.TextLight)
            Spacer(Modifier.width(4.dp))
        }
        Text(
            title,
            color = Win95Colors.TextLight,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        Win95TitleButton("_", onMinimize)
        Spacer(Modifier.width(2.dp))
        Win95TitleButton("□", onMaximize)
        Spacer(Modifier.width(2.dp))
        Win95TitleButton("×", onClose)
    }
}

@Composable
private fun Win95TitleButton(label: String, onClick: (() -> Unit)?) {
    Box(
        modifier = Modifier
            .size(16.dp, 14.dp)
            .background(Win95Colors.Face)
            .win95Bevel(raised = true, thickness = 1.dp)
            .androidxClickable(remember { MutableInteractionSource() }) { onClick?.invoke() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Win95Colors.Black)
    }
}

/** Win95 section header (кликабельный заголовок секции) */
@Composable
fun Win95SectionHeader(
    title: String,
    count: String = "",
    extra: String = "",
    collapsed: Boolean = false,
    onClick: () -> Unit,
    onExtraClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .androidxClickable(remember { MutableInteractionSource() }) { onClick() }
            .padding(vertical = 3.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (collapsed) "▶" else "▼",
            fontSize = 9.sp,
            color = Win95Colors.Black,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(12.dp)
        )
        Text(
            title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Win95Colors.Black
        )
        if (count.isNotBlank()) {
            Spacer(Modifier.width(4.dp))
            Text(count, fontSize = 10.sp, color = Win95Colors.Navy, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.weight(1f))
        if (extra.isNotBlank()) {
            Text(
                extra,
                fontSize = 10.sp,
                color = Win95Colors.Navy,
                fontFamily = FontFamily.Monospace,
                modifier = if (onExtraClick != null) {
                    Modifier.androidxClickable(remember { MutableInteractionSource() }) { onExtraClick() }
                } else Modifier
            )
        }
    }
}

/** Win95 text (mono, для консистентности) */
@Composable
fun Win95Text(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: Int = 11,
    color: Color = Win95Colors.Text,
    bold: Boolean = false,
    maxLines: Int = 1
) {
    Text(
        text,
        modifier = modifier,
        fontSize = fontSize.sp,
        color = color,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        fontFamily = FontFamily.Monospace,
        maxLines = maxLines,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
    )
}
