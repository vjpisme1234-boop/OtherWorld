package com.jp.otherworld.ui.home

import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jp.otherworld.data.drawableToBitmap
import com.jp.otherworld.ui.common.GlassDim
import com.jp.otherworld.ui.common.GlassLabel
import com.jp.otherworld.ui.common.glass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class WidgetChoice(val info: AppWidgetProviderInfo, val app: String, val label: String, val preview: ImageBitmap?)

/** Every widget installed apps offer, grouped by app, with their preview pictures. */
@Composable
fun WidgetPicker(onPickGlass: (String) -> Unit, onPick: (AppWidgetProviderInfo) -> Unit) {
    val context = LocalContext.current
    var choices by remember { mutableStateOf<List<WidgetChoice>?>(null) }
    LaunchedEffect(Unit) {
        choices = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val dpi = context.resources.displayMetrics.densityDpi
            WidgetHost.manager.installedProviders.map { info ->
                val app = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString() }
                    .getOrDefault(info.provider.packageName)
                val preview = (info.loadPreviewImage(context, dpi) ?: info.loadIcon(context, dpi))?.let {
                    val w = it.intrinsicWidth.coerceIn(1, 600); val h = it.intrinsicHeight.coerceIn(1, 600)
                    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                    it.setBounds(0, 0, w, h); it.draw(android.graphics.Canvas(bmp)); bmp.asImageBitmap()
                }
                WidgetChoice(info, app, info.loadLabel(pm), preview)
            }.sortedWith(compareBy({ it.app.lowercase() }, { it.label.lowercase() }))
        }
    }
    Column(Modifier.fillMaxSize().background(Color(0xF0020814)).systemBarsPadding().padding(16.dp)) {
        GlassLabel("Widgets", 26.sp, weight = FontWeight.Light)
        Spacer(Modifier.height(12.dp))
        val list = choices
        if (list == null) GlassLabel("Loading…", color = GlassDim)
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Other World's own glass widgets first.
            item { GlassLabel("Other World", 15.sp, com.jp.otherworld.ui.common.GlassCyan) }
            items(GLASS_WIDGETS, key = { it.first }) { (kind, name, span) ->
                Row(
                    Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).clickable { onPickGlass(kind) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Drawn at a real widget size, then shrunk to fit the preview box.
                    Box(Modifier.size(96.dp, 72.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.requiredSize(if (span.first > 2) 240.dp else 150.dp, 150.dp).graphicsLayer { scaleX = 0.44f; scaleY = 0.44f }) { GlassWidget(kind) {} }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        GlassLabel(name, 16.sp)
                        GlassLabel("${span.first} × ${span.second}", 13.sp, GlassDim)
                    }
                }
            }
            item { GlassLabel("Your apps", 15.sp, com.jp.otherworld.ui.common.GlassCyan) }
            items(list, key = { it.info.provider.flattenToString() }) { c ->
                Row(
                    Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).clickable { onPick(c.info) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(96.dp, 72.dp), contentAlignment = Alignment.Center) {
                        c.preview?.let { Image(it, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        GlassLabel(c.label, 16.sp)
                        GlassLabel(c.app, 13.sp, GlassDim)
                    }
                }
            }
        }
    }
}
