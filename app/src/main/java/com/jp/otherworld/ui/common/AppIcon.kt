package com.jp.otherworld.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import com.jp.otherworld.data.AppInfo
import com.jp.otherworld.data.IconStyle
import com.jp.otherworld.data.LauncherStore
import com.jp.otherworld.data.NotificationHub

/** True when any of these apps has unread notifications and dots are on. */
@Composable
fun hasDot(vararg packages: String): Boolean {
    val counts by NotificationHub.counts.collectAsState()
    val state by LauncherStore.state.collectAsState()
    return state.showDots && packages.any { (counts[it] ?: 0) > 0 }
}

/** A small glowing cyan dot, drawn in the top-right corner of an icon. */
@Composable
fun GlowDot(iconSize: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(iconSize * 0.34f)) {
        val c = Offset(size.width / 2, size.height / 2)
        drawCircle(Brush.radialGradient(listOf(Color(0xCC7FE9FF), Color(0x007FE9FF)), c, size.minDimension / 2), size.minDimension / 2, c)
        drawCircle(Color(0xFFE6FCFF), size.minDimension * 0.17f, c)
        drawCircle(GlassCyan, size.minDimension * 0.17f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(size.minDimension * 0.05f))
    }
}

/** An app's icon (in the chosen style) with its unread dot. */
@Composable
fun AppIcon(app: AppInfo, style: IconStyle, size: Dp, modifier: Modifier = Modifier) {
    val bmp = remember(app, style) { app.iconFor(style).asImageBitmap() }
    Box(modifier.size(size)) {
        Image(bmp, app.label, Modifier.fillMaxSize())
        if (hasDot(app.packageName)) GlowDot(size, Modifier.align(Alignment.TopEnd))
    }
}
