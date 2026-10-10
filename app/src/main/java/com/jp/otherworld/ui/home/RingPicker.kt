package com.jp.otherworld.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jp.otherworld.data.*
import com.jp.otherworld.ui.common.*

/** "Move to ring…": pick one of the rings, or make a new one. */
@Composable
fun RingPicker(app: AppInfo, state: LauncherState, apps: List<AppInfo>, onDone: () -> Unit) {
    var newName by remember { mutableStateOf("") }
    val current = state.ringOf(app)
    val choices = (BUILT_IN_RINGS + state.customRings).distinct()
    GlassSheet(onDone) {
        GlassLabel("Move ${app.label} to…", 20.sp)
        LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(choices) { r ->
                GlassMenuItem(if (r == current) "$r  ✓" else r) {
                    LauncherStore.update { it.moveToRing(app, r) }
                    onDone()
                }
            }
        }
        GlassLabel("New ring", 15.sp, GlassDim)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f).glass(RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
                BasicTextField(
                    newName, { newName = it.take(20) }, singleLine = true,
                    textStyle = TextStyle(color = GlassText, fontSize = 16.sp), cursorBrush = SolidColor(GlassCyan),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            GlassButton("Add", selected = newName.isNotBlank()) {
                if (newName.isNotBlank()) { LauncherStore.update { it.moveToRing(app, newName) }; onDone() }
            }
        }
    }
}
