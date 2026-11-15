package com.garickbox.jecwebgo.ui.common.win95

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garickbox.jecwebgo.ui.theme.Win95Colors

@Composable
fun Win95TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    enabled: Boolean = true
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .background(Win95Colors.White)
            .win95Bevel(raised = false, thickness = 1.dp)
            .padding(horizontal = 4.dp, vertical = 3.dp),
        textStyle = TextStyle(
            color = Win95Colors.Text,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        ),
        singleLine = singleLine,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
    )
}

@Composable
fun Win95Radio(
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .size(13.dp)
            .background(Win95Colors.White, CircleShape)
            .border(1.dp, Win95Colors.Gray, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSelect
            ),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(Modifier.size(6.dp).background(Win95Colors.Black, CircleShape))
        }
    }
}
