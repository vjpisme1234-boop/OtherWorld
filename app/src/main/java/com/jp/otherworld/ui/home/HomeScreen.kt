package com.jp.otherworld.ui.home

import android.app.WallpaperManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.jp.otherworld.data.*
import com.jp.otherworld.ui.common.AppIcon
import com.jp.otherworld.ui.common.GlassButton
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import com.jp.otherworld.ui.common.GlassCyan
import com.jp.otherworld.ui.common.glass
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** What a long-pressed app belongs to, so the menu can offer the right actions. */
sealed class AppSource {
    data class Home(val page: Int, val item: HomeItem.App) : AppSource()
    data class Dock(val slot: Int) : AppSource()
    data class Folder(val page: Int, val folderId: String) : AppSource()
    data object Drawer : AppSource()
}

data class AppMenuTarget(val app: AppInfo, val source: AppSource)

/** Size of one home grid cell in px, measured by HomeScreen; used to size new widgets. */
object HomeGrid {
    var cellWpx = 0f
    var cellHpx = 0f
}

private val labelStyle = TextStyle(
    color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center,
    shadow = Shadow(Color(0xCC000000), Offset(0f, 1f), 4f)
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    state: LauncherState,
    apps: Map<String, AppInfo>,
    pager: PagerState,
    editingWidget: Int?,
    onEditWidget: (Int?) -> Unit,
    onOpenDrawer: () -> Unit,
    onHomeLongPress: () -> Unit,
    onAppMenu: (AppMenuTarget) -> Unit,
    onEmptyDockSlot: (Int) -> Unit,
    onOpenFolder: (HomeItem.Folder, Int) -> Unit,
    onFolderMenu: (HomeItem.Folder, Int) -> Unit,
    onSwipeDown: () -> Unit,
    onTwoFingerDown: () -> Unit
) {
    val density = LocalDensity.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    // Wallpaper follows the pages, like other launchers.
    LaunchedEffect(state.pages.size) {
        val wm = WallpaperManager.getInstance(view.context)
        val steps = if (state.pages.size > 1) 1f / (state.pages.size - 1) else 0f
        wm.setWallpaperOffsetSteps(steps, 1f)
        snapshotFlow { pager.currentPage + pager.currentPageOffsetFraction }.collect { p ->
            val x = if (state.pages.size > 1) (p / (state.pages.size - 1)).coerceIn(0f, 1f) else 0.5f
            view.windowToken?.let { runCatching { wm.setWallpaperOffsets(it, x, 0.5f) } }
        }
    }

    val iconDp = state.iconSize.dp.dp
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val cellW = maxWidth / GRID_COLS
            val cellH = maxHeight / GRID_ROWS
            with(density) { HomeGrid.cellWpx = cellW.toPx(); HomeGrid.cellHpx = cellH.toPx() }
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { it }) { page ->
                // A two-finger swipe is handled on its own; the one-finger swipe then stands down.
                var twoFingers by remember { mutableStateOf(false) }
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { onEditWidget(null) },
                                onLongPress = { onHomeLongPress() }
                            )
                        }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                twoFingers = false
                                var startY = Float.NaN
                                do {
                                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                                    val down = ev.changes.filter { it.pressed }
                                    if (down.size >= 2) {
                                        val y = down.map { it.position.y }.average().toFloat()
                                        if (startY.isNaN()) startY = y
                                        if (!twoFingers && y - startY > 80.dp.toPx()) { twoFingers = true; onTwoFingerDown() }
                                    }
                                } while (down.isNotEmpty())
                            }
                        }
                        .pointerInput(Unit) {
                            var dy = 0f
                            detectVerticalDragGestures(
                                onDragStart = { dy = 0f },
                                onDragEnd = {
                                    val far = with(density) { 80.dp.toPx() }
                                    when {
                                        twoFingers -> {}
                                        dy < -far -> onOpenDrawer()
                                        dy > far -> onSwipeDown()
                                    }
                                }
                            ) { c, d -> dy += d; c.consume() }
                        }
                ) {
                    val items = state.pages.getOrNull(page)?.items.orEmpty()
                    for (item in items) {
                        key(item) {
                            when (item) {
                                is HomeItem.App -> apps[item.pkg]?.let { app ->
                                    HomeCell(
                                        item, state, page, cellW, cellH, app.label,
                                        onPageFlip = { target -> scope.launch { pager.animateScrollToPage(target) } },
                                        onTap = { launchApp(view.context, app.packageName) },
                                        onMenu = { onAppMenu(AppMenuTarget(app, AppSource.Home(page, item))) }
                                    ) { AppIcon(app, state.iconStyle, iconDp) }
                                }
                                is HomeItem.Folder -> HomeCell(
                                    item, state, page, cellW, cellH, item.name,
                                    onPageFlip = { target -> scope.launch { pager.animateScrollToPage(target) } },
                                    onTap = { onOpenFolder(item, page) },
                                    onMenu = { onFolderMenu(item, page) }
                                ) { FolderIcon(item, apps, state.iconStyle, iconDp) }
                                is HomeItem.Widget -> HomeWidget(
                                    item, page, state, cellW, cellH,
                                    editing = editingWidget == item.id,
                                    onLongPress = { onEditWidget(item.id) },
                                    onDone = { onEditWidget(null) },
                                    onRemove = { WidgetHost.delete(item.id) }
                                ) { w, h, hold -> WidgetCell(item.id, w, h, hold, Modifier.fillMaxSize()) }
                                is HomeItem.Glass -> HomeWidget(
                                    item, page, state, cellW, cellH,
                                    editing = editingWidget == item.id,
                                    onLongPress = { onEditWidget(item.id) },
                                    onDone = { onEditWidget(null) },
                                    onRemove = {}
                                ) { _, _, hold -> GlassWidget(item.kind, hold) }
                            }
                        }
                    }
                }
            }
        }
        // The page dots and the dock can be moved up (Move dock / Move page dots, or hold the dots).
        MovableBar(state.dotsLift, 0.27f, editingWidget == EDIT_DOTS, onHold = { onEditWidget(EDIT_DOTS) }, onDone = { onEditWidget(null) },
            save = { v -> LauncherStore.update { it.copy(dotsLift = v) } }) {
            PageDots(state.pages.size, pager.currentPage)
        }
        MovableBar(state.dockLift, 1f, editingWidget == EDIT_DOCK, onHold = null, onDone = { onEditWidget(null) },
            save = { v -> LauncherStore.update { it.copy(dockLift = v) } }) {
            Dock(state, apps, iconDp, onOpenDrawer, onAppMenu, onEmptyDockSlot)
        }
    }
}

/** editingWidget values for moving the dock / page dots (real widget ids never reach these). */
const val EDIT_DOCK = Int.MIN_VALUE
const val EDIT_DOTS = Int.MIN_VALUE + 1

/**
 * The dock or the page dots, lifted `lift` dp up from their spot at the bottom. While editing: a cyan
 * frame you drag up or down, with Reset (back to the bottom) and Done. Not editing: optional hold to start.
 */
@Composable
private fun MovableBar(
    savedLift: Float, defaultShare: Float, editing: Boolean, onHold: (() -> Unit)?, onDone: () -> Unit, save: (Float) -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val maxLift = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp - 170).coerceAtLeast(0).toFloat()
    // Not moved yet: the default height, as a share of what's available (same look on any screen size).
    val lift = if (savedLift < 0f) defaultShare * maxLift else savedLift
    var drag by remember { mutableStateOf(0f) }          // dp, while dragging
    val shown = (lift + drag).coerceIn(0f, maxLift)
    Box(
        Modifier
            .offset { IntOffset(0, -with(density) { shown.dp.roundToPx() }) }
            .zIndex(if (editing) 6f else 0f)
            .then(if (onHold != null && !editing) Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { onHold() }) } else Modifier)
    ) {
        content()
        if (editing) {
            Box(
                Modifier
                    .matchParentSize()
                    .border(2.dp, GlassCyan, RoundedCornerShape(14.dp))
                    .background(Color(0x2249A6FF), RoundedCornerShape(14.dp))
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = { save((lift + drag).coerceIn(0f, maxLift)); drag = 0f }
                        ) { c, d -> c.consume(); drag -= with(density) { d.toDp().value } }
                    }
            )
            Row(
                Modifier.align(Alignment.TopCenter).offset(y = (-52).dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassButton("Reset") { drag = 0f; save(0f) }
                GlassButton("Done", selected = true) { onDone() }
            }
        }
    }
}

/**
 * One app or folder on a home page: tap, hold for its menu, or hold and drag to move it. Dropping an
 * app on another app makes a folder; dropping it on a folder puts it inside.
 */
@Composable
private fun HomeCell(
    item: HomeItem, state: LauncherState, page: Int,
    cellW: Dp, cellH: Dp, label: String,
    onPageFlip: (Int) -> Unit, onTap: () -> Unit, onMenu: () -> Unit,
    icon: @Composable () -> Unit
) {
    val density = LocalDensity.current
    var drag by remember { mutableStateOf<Offset?>(null) }
    val cwPx = with(density) { cellW.toPx() }
    val chPx = with(density) { cellH.toPx() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .offset { IntOffset((item.col * cwPx).roundToInt(), (item.row * chPx).roundToInt()) }
            .size(cellW, cellH)
            .zIndex(if (drag != null) 10f else 0f)
            .graphicsLayer {
                val d = drag ?: Offset.Zero
                translationX = d.x; translationY = d.y
                val s = if (drag != null) 1.12f else 1f
                scaleX = s; scaleY = s
            }
            .pointerInput(item) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { drag = Offset.Zero },
                    onDragCancel = { drag = null },
                    onDragEnd = {
                        val d = drag ?: Offset.Zero
                        drag = null
                        if (d.getDistance() < 24f) { onMenu(); return@detectDragGesturesAfterLongPress }
                        val col = ((item.col * cwPx + d.x) / cwPx).roundToInt()
                        val row = ((item.row * chPx + d.y) / chPx).roundToInt().coerceIn(0, GRID_ROWS - 1)
                        // Dropped past a screen edge: move to the next/previous page.
                        val target = when {
                            col < 0 && page > 0 -> page - 1
                            col >= GRID_COLS && page < state.pages.size - 1 -> page + 1
                            else -> page
                        }
                        LauncherStore.update { s ->
                            if (target == page) {
                                val under = s.itemAt(page, col, row)?.takeIf { it != item }
                                when {
                                    item is HomeItem.App && under is HomeItem.App -> s.withPage(page) { items ->
                                        items - item - under + HomeItem.Folder(
                                            java.util.UUID.randomUUID().toString(), "Folder", listOf(under.pkg, item.pkg), under.col, under.row
                                        )
                                    }
                                    item is HomeItem.App && under is HomeItem.Folder -> s.withPage(page) { items ->
                                        (items - item).map { if (it == under) under.copy(apps = (under.apps - item.pkg) + item.pkg) else it }
                                    }
                                    s.fits(page, item, col, row) -> s.withPage(page) { items -> items.map { x -> if (x != item) x else item.at(col, row) } }
                                    else -> s
                                }
                            } else {
                                val cell = s.freeCell(target) ?: return@update s
                                s.withPage(page) { it - item }.withPage(target) { it + item.at(cell.first, cell.second) }
                            }
                        }
                        if (target != page) onPageFlip(target)
                    }
                ) { change, amount -> change.consume(); drag = (drag ?: Offset.Zero) + amount }
            }
            .clickable { onTap() }
    ) {
        icon()
        if (state.showLabels) {
            Spacer(Modifier.height(4.dp))
            BasicText(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = labelStyle, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

@Composable
private fun HomeWidget(
    item: HomeItem, page: Int, state: LauncherState, cellW: Dp, cellH: Dp,
    editing: Boolean, onLongPress: () -> Unit, onDone: () -> Unit, onRemove: () -> Unit,
    content: @Composable (Dp, Dp, () -> Unit) -> Unit
) {
    val density = LocalDensity.current
    val cwPx = with(density) { cellW.toPx() }
    val chPx = with(density) { cellH.toPx() }
    var move by remember { mutableStateOf(Offset.Zero) }
    var grow by remember { mutableStateOf(Offset.Zero) }
    val w = cellW * item.spanX
    val h = cellH * item.spanY
    Box(
        Modifier
            .offset { IntOffset((item.col * cwPx + move.x).roundToInt(), (item.row * chPx + move.y).roundToInt()) }
            .size(w + with(density) { grow.x.toDp() }, h + with(density) { grow.y.toDp() })
            .zIndex(if (editing) 5f else 1f)
            .padding(4.dp)
    ) {
        content(w - 8.dp, h - 8.dp, onLongPress)
        if (editing) {
            // Edit frame: drag the body to move, the corner to resize.
            Box(
                Modifier
                    .fillMaxSize()
                    .border(2.dp, GlassCyan, RoundedCornerShape(14.dp))
                    .background(Color(0x2249A6FF), RoundedCornerShape(14.dp))
                    .pointerInput(item) {
                        detectDragGestures(
                            onDragEnd = {
                                val col = ((item.col * cwPx + move.x) / cwPx).roundToInt()
                                val row = ((item.row * chPx + move.y) / chPx).roundToInt()
                                move = Offset.Zero
                                LauncherStore.update { s ->
                                    if (s.fits(page, item, col, row)) s.withPage(page) { it.map { x -> if (x == item) item.at(col, row) else x } } else s
                                }
                            }
                        ) { c, a -> c.consume(); move += a }
                    }
            )
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(GlassCyan)
                    .pointerInput(item) {
                        detectDragGestures(
                            onDragEnd = {
                                val sx = ((item.spanX * cwPx + grow.x) / cwPx).roundToInt().coerceIn(1, GRID_COLS)
                                val sy = ((item.spanY * chPx + grow.y) / chPx).roundToInt().coerceIn(1, GRID_ROWS)
                                grow = Offset.Zero
                                val resized = item.sized(sx, sy)
                                LauncherStore.update { s ->
                                    val without = s.withPage(page) { it - item }
                                    if (without.fits(page, resized, item.col, item.row)) without.withPage(page) { it + resized } else s
                                }
                            }
                        ) { c, a -> c.consume(); grow += a }
                    }
            )
            // Buttons above the widget, or below it when it sits in the top row.
            val below = item.row == 0
            Row(
                Modifier.align(if (below) Alignment.BottomCenter else Alignment.TopCenter).offset(y = if (below) 56.dp else (-56).dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassButton("Remove") {
                    onRemove()
                    LauncherStore.update { s -> s.withPage(page) { it - item } }
                    onDone()
                }
                GlassButton("Done", selected = true) { onDone() }
            }
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    Row(Modifier.fillMaxWidth().height(18.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        if (count > 1) repeat(count) { i ->
            Box(
                Modifier.padding(horizontal = 4.dp).size(if (i == current) 8.dp else 6.dp).clip(CircleShape)
                    .background(if (i == current) GlassCyan else Color(0x88FFFFFF))
            )
        }
    }
}

@Composable
private fun Dock(
    state: LauncherState, apps: Map<String, AppInfo>, iconDp: Dp,
    onOpenDrawer: () -> Unit, onAppMenu: (AppMenuTarget) -> Unit, onEmptySlot: (Int) -> Unit
) {
    val context = LocalView.current.context
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .glass(RoundedCornerShape(28.dp))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        state.dock.forEachIndexed { i, pkg ->
            val app = pkg?.let { apps[it] }
            if (app != null) {
                AppIcon(
                    app, state.iconStyle, iconDp,
                    Modifier.pointerInput(app.packageName, i) {
                        detectTapGestures(
                            onTap = { launchApp(context, app.packageName) },
                            onLongPress = { onAppMenu(AppMenuTarget(app, AppSource.Dock(i))) }
                        )
                    }
                )
            } else {
                Image(
                    GlassIcons.tile.asImageBitmap(), "Empty dock slot",
                    Modifier.size(iconDp).alpha(0.35f).clickable { onEmptySlot(i) }
                )
            }
        }
        Image(GlassIcons.appsButton.asImageBitmap(), "All apps", Modifier.size(iconDp).clickable { onOpenDrawer() })
    }
}
