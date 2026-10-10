package com.jp.otherworld.ui.home

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.jp.otherworld.data.*
import com.jp.otherworld.ui.common.*
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** A folder on home: the clear glass tile with up to four of its apps inside, and a dot if any has news. */
@Composable
fun FolderIcon(folder: HomeItem.Folder, apps: Map<String, AppInfo>, style: IconStyle, size: Dp) {
    val tile = remember { GlassIcons.tile.asImageBitmap() }
    val inside = folder.apps.mapNotNull { apps[it] }.take(4)
    Box(Modifier.size(size)) {
        Image(tile, folder.name, Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().padding(size * 0.2f), verticalArrangement = Arrangement.SpaceEvenly) {
            inside.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { app -> Image(remember(app, style) { app.iconFor(style).asImageBitmap() }, null, Modifier.size(size * 0.27f)) }
                }
            }
        }
        if (hasDot(*folder.apps.toTypedArray())) GlowDot(size, Modifier.align(Alignment.TopEnd))
    }
}

/** An open folder (see [MiniWheelSheet]). */
@Composable
fun FolderSheet(
    folder: HomeItem.Folder, page: Int, apps: Map<String, AppInfo>, style: IconStyle,
    onDismiss: () -> Unit, onAppMenu: (AppMenuTarget) -> Unit, onRename: () -> Unit
) = MiniWheelSheet(
    folder.name, folder.apps.mapNotNull { apps[it] }, style, onDismiss,
    onAppMenu = { app -> onAppMenu(AppMenuTarget(app, AppSource.Folder(page, folder.id))) }, onRename = onRename
)

/**
 * A small spinning glass wheel of apps (open folders, Recent). Drag sideways to spin; it snaps with one
 * app in front. One tap opens an app; hold one for its menu (take out of folder, etc.).
 */
@Composable
fun MiniWheelSheet(
    title: String, list: List<AppInfo>, style: IconStyle,
    onDismiss: () -> Unit, onAppMenu: (AppInfo) -> Unit, onRename: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val view = LocalView.current
    val n = list.size
    val wraps = n >= 7
    // Positions around the ring: a full circle once there are enough apps, else an arc.
    val step = 2 * PI / maxOf(n, 7)
    val pos = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val radius = 120.dp
    val radiusPx = with(density) { radius.toPx() }
    var lastTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { snapshotFlow { pos.value.roundToInt() }.collect { if (it != lastTick) { lastTick = it; view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) } } }

    fun settle(target: Float) {
        val t = if (wraps) target.roundToInt().toFloat() else target.roundToInt().coerceIn(0, n - 1).toFloat()
        scope.launch { pos.animateTo(t, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = 260f)) }
    }

    GlassSheet(onDismiss, width = 420.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassLabel(title, 22.sp, weight = FontWeight.Light, modifier = Modifier.weight(1f))
            if (onRename != null) GlassButton("Rename", onClick = onRename)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .pointerInput(n) {
                    val tracker = VelocityTracker()
                    detectHorizontalDragGestures(
                        onDragStart = { tracker.resetTracking() },
                        onDragEnd = {
                            val v = tracker.calculateVelocity().x
                            scope.launch {
                                // Fling: coast, then snap to the nearest app.
                                val coast = exponentialDecay<Float>(frictionMultiplier = 2.2f)
                                val target = coast.calculateTargetValue(pos.value, -v / (radiusPx * step.toFloat()))
                                settle(target)
                            }
                        }
                    ) { change, dx ->
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        scope.launch {
                            var next = pos.value - dx / (radiusPx * step.toFloat())
                            if (!wraps) next = next.coerceIn(-0.4f, n - 0.6f)
                            pos.snapTo(next)
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            list.forEachIndexed { i, app ->
                var rel = i - pos.value
                if (wraps) { rel = ((rel % n) + n) % n; if (rel > n / 2f) rel -= n }
                val a = rel * step
                val depth = cos(a).toFloat()          // 1 = front, -1 = back
                if (depth < -0.2f && !wraps) return@forEachIndexed
                val x = sin(a).toFloat() * radiusPx
                val scale = 0.55f + 0.45f * (depth + 1f) / 2f
                Box(
                    Modifier
                        .offset { IntOffset(x.roundToInt(), (-(depth - 1f) * 18f).roundToInt()) }
                        .zIndex(depth)
                        .graphicsLayer {
                            scaleX = scale; scaleY = scale
                            alpha = (0.35f + 0.65f * (depth + 1f) / 2f).coerceIn(0f, 1f)
                            rotationY = -(a * 180 / PI).toFloat() * 0.6f
                        }
                        .size(78.dp)
                        .glass(RoundedCornerShape(20.dp))
                        .pointerInput(app.packageName) {
                            detectTapGestures(
                                onTap = { launchApp(context, app.packageName); onDismiss() },
                                onLongPress = { onAppMenu(app) }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) { AppIcon(app, style, 56.dp) }
            }
        }
        val front = if (n == 0) null else list[((pos.value.roundToInt() % n) + n) % n]
        GlassLabel(front?.label ?: "", 16.sp, GlassCyan, modifier = Modifier.align(Alignment.CenterHorizontally))
        GlassLabel("Spin to browse · tap to open · hold for options", 12.sp, GlassDim, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

/** Rename a folder. */
@Composable
fun RenameFolder(folder: HomeItem.Folder, page: Int, onDone: () -> Unit) {
    var name by remember { mutableStateOf(folder.name) }
    fun save() {
        val n = name.trim().ifEmpty { "Folder" }
        LauncherStore.update { s -> s.withPage(page) { items -> items.map { if (it is HomeItem.Folder && it.id == folder.id) it.copy(name = n) else it } } }
        onDone()
    }
    GlassSheet(onDone) {
        GlassLabel("Folder name", 20.sp)
        Box(Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 14.dp)) {
            BasicTextField(
                name, { name = it.take(30) }, singleLine = true,
                textStyle = TextStyle(color = GlassText, fontSize = 17.sp), cursorBrush = SolidColor(GlassCyan),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton("Save", selected = true) { save() }
            GlassButton("Cancel", onClick = onDone)
        }
    }
}

/** Hold a folder: rename, or ungroup (its apps go back onto the page), or remove it from home. */
@Composable
fun FolderMenu(folder: HomeItem.Folder, page: Int, onDismiss: () -> Unit, onRename: () -> Unit) {
    GlassSheet(onDismiss) {
        GlassLabel(folder.name, 20.sp)
        GlassMenuItem("Rename") { onRename() }
        GlassMenuItem("Ungroup") {
            LauncherStore.update { s ->
                var next = s.withPage(page) { it - folder }
                // The first app takes the folder's spot; the rest go to free cells.
                folder.apps.forEachIndexed { i, pkg ->
                    next = if (i == 0) next.withPage(page) { it + HomeItem.App(pkg, folder.col, folder.row) } else next.addApp(pkg, page)
                }
                next
            }
            onDismiss()
        }
        GlassMenuItem("Remove from home") {
            LauncherStore.update { s -> s.withPage(page) { it - folder } }
            onDismiss()
        }
    }
}
