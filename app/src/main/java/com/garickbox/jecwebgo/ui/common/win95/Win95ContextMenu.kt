package com.garickbox.jecwebgo.ui.common.win95

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.garickbox.jecwebgo.ui.theme.Win95Colors

data class Win95ContextMenuItem(
    val icon: String = "",
    val label: String = "",
    val shortcut: String = "",
    val disabled: Boolean = false,
    val danger: Boolean = false,
    val separator: Boolean = false,
    val onClick: () -> Unit = {}
) {
    companion object {
        fun sep() = Win95ContextMenuItem(separator = true)
    }
}

@Composable
fun Win95ContextMenu(
    items: List<Win95ContextMenuItem>,
    onDismiss: () -> Unit,
    anchor: IntOffset = IntOffset.Zero,
    modifier: Modifier = Modifier
) {
    Popup(
        alignment = Alignment.TopStart,
        offset = anchor,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Column(
            modifier
                .background(Win95Colors.Face)
                .win95Bevel(raised = true, thickness = 2.dp)
                .padding(2.dp)
                .widthIn(min = 210.dp)
        ) {
            items.forEach { item ->
                if (item.separator) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp)
                            .height(2.dp)
                            .background(Win95Colors.Gray)
                    )
                } else {
                    val textColor = when {
                        item.disabled -> Win95Colors.TextDisabled
                        item.danger -> Win95Colors.Red
                        else -> Win95Colors.Text
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                enabled = !item.disabled
                            ) {
                                onDismiss()
                                item.onClick()
                            }
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.width(18.dp)) {
                            if (item.icon.isNotBlank()) {
                                Text(
                                    item.icon,
                                    fontSize = 11.sp,
                                    color = textColor
                                )
                            }
                        }
                        Text(
                            item.label,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = textColor,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (item.shortcut.isNotBlank()) {
                            Text(
                                item.shortcut,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Win95Colors.TextDim
                            )
                        }
                    }
                }
            }
        }
    }
}
