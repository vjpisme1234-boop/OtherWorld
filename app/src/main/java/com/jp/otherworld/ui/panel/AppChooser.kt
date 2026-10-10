package com.jp.otherworld.ui.panel

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jp.otherworld.data.AppInfo
import com.jp.otherworld.data.IconStyle
import com.jp.otherworld.ui.common.*

/** Pick one app (for a dock slot). */
@Composable
fun AppChooser(title: String, apps: List<AppInfo>, style: IconStyle, onPick: (AppInfo) -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = remember(query, apps) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }
    Column(Modifier.fillMaxSize().background(Color(0xF0020814)).systemBarsPadding().imePadding().padding(16.dp)) {
        GlassLabel(title, 24.sp, weight = FontWeight.Light)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).padding(horizontal = 18.dp, vertical = 12.dp)) {
            if (query.isEmpty()) GlassLabel("Search apps", color = GlassDim)
            BasicTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                textStyle = TextStyle(color = GlassText, fontSize = 16.sp), cursorBrush = SolidColor(GlassCyan),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(shown, key = { it.packageName }) { app ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(app) }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(app.iconFor(style).asImageBitmap(), null, Modifier.size(44.dp))
                    Spacer(Modifier.width(14.dp))
                    GlassLabel(app.label, 16.sp)
                }
            }
        }
    }
}
