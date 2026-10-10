package com.jp.otherworld.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Colours taken from the wheel art's glass: navy body, cyan edge light.
val GlassText = Color(0xFFEAF6FF)
val GlassDim = Color(0xFF8FB4D6)
val GlassCyan = Color(0xFF7FE9FF)

/** Clear navy glass with a bright cyan rim, like the slabs in the art. */
fun Modifier.glass(shape: Shape = RoundedCornerShape(22.dp), strong: Boolean = false): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            if (strong) listOf(Color(0xE6143A66), Color(0xF00A1D38))
            else listOf(Color(0x8C143A66), Color(0xB30A1D38))
        )
    )
    .border(
        1.2.dp,
        Brush.verticalGradient(listOf(Color(0xFFBFF4FF), Color(0x6649A6FF), Color(0xCC7FE9FF))),
        shape
    )

@Composable
fun GlassLabel(text: String, size: TextUnit = 16.sp, color: Color = GlassText, weight: FontWeight = FontWeight.Normal, modifier: Modifier = Modifier) {
    BasicText(text, modifier = modifier, style = TextStyle(color = color, fontSize = size, fontWeight = weight))
}

@Composable
fun GlassButton(text: String, modifier: Modifier = Modifier, selected: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier
            .glass(RoundedCornerShape(18.dp), strong = selected)
            .then(if (selected) Modifier.border(2.dp, GlassCyan, RoundedCornerShape(18.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) { GlassLabel(text, 15.sp, if (selected) GlassText else GlassDim, if (selected) FontWeight.Medium else FontWeight.Normal) }
}

/** A dimmed backdrop with a glass card; tapping outside dismisses. */
@Composable
fun GlassSheet(onDismiss: () -> Unit, width: Dp = 360.dp, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .widthIn(max = width)
                .fillMaxWidth(0.9f)
                .glass(RoundedCornerShape(28.dp), strong = true)
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
fun GlassMenuItem(text: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp)
    ) { GlassLabel(text, 17.sp) }
}
