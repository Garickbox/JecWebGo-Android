package com.garickbox.jecwebgo.ui.common.win95

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garickbox.jecwebgo.ui.theme.Win95Colors

data class Win95SubTab(
    val id: String,
    val label: String
)

@Composable
fun Win95TabsStrip(
    tabs: List<Win95SubTab>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Win95Colors.Face)
            .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 0.dp)
    ) {
        tabs.forEach { tab ->
            val isSel = tab.id == selectedId
            Box(
                Modifier
                    .background(if (isSel) Win95Colors.White else Win95Colors.Face)
                    .win95Bevel(raised = true, thickness = 2.dp)
                    .padding(horizontal = 10.dp, vertical = 3.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(tab.id) }
            ) {
                Text(
                    tab.label,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                    color = Win95Colors.Black
                )
            }
        }
    }
}
