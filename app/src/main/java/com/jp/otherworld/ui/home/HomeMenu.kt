package com.jp.otherworld.ui.home

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jp.otherworld.data.LauncherStore
import com.jp.otherworld.data.HomeItem
import com.jp.otherworld.data.AppInfo
import com.jp.otherworld.data.drawableToBitmap
import com.jp.otherworld.ui.common.glass
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.remember
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import com.jp.otherworld.data.openAppInfo
import com.jp.otherworld.data.uninstallApp
import com.jp.otherworld.ui.common.GlassLabel
import com.jp.otherworld.ui.common.GlassMenuItem
import com.jp.otherworld.ui.common.GlassSheet

enum class HomeAction { Wallpaper, Widgets, Settings, EditPages }

/** Long-press on empty home. */
@Composable
fun HomeMenu(onDismiss: () -> Unit, onAction: (HomeAction) -> Unit) {
    GlassSheet(onDismiss) {
        GlassLabel("Home", 22.sp, weight = FontWeight.Light)
        GlassMenuItem("Wallpaper") { onAction(HomeAction.Wallpaper) }
        GlassMenuItem("Widgets") { onAction(HomeAction.Widgets) }
        GlassMenuItem("Home settings") { onAction(HomeAction.Settings) }
        GlassMenuItem("Edit pages") { onAction(HomeAction.EditPages) }
    }
}

/** Long-press on an app, wherever it is. */
@Composable
fun AppMenu(target: AppMenuTarget, currentPage: Int, onMoveToRing: (AppInfo) -> Unit = {}, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = target.app
    fun done(f: () -> Unit) { f(); onDismiss() }
    GlassSheet(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(app.iconFor(LauncherStore.state.value.iconStyle).asImageBitmap(), null, Modifier.size(44.dp))
            Spacer(Modifier.width(12.dp))
            GlassLabel(app.label, 20.sp)
        }
        // The app's own shortcuts (e.g. "New message"), when Other World is the home app.
        val shortcuts = remember(app.packageName) { loadShortcuts(context, app.packageName) }
        shortcuts.forEach { sc ->
            Row(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).clickable { done { startShortcut(context, sc.info) } }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                sc.icon?.let { Image(it.asImageBitmap(), null, Modifier.size(28.dp)) }
                Spacer(Modifier.width(12.dp))
                GlassLabel(sc.label, 16.sp)
            }
        }
        when (val src = target.source) {
            is AppSource.Home -> GlassMenuItem("Remove from home") {
                done { LauncherStore.update { s -> s.withPage(src.page) { it - src.item } } }
            }
            is AppSource.Dock -> GlassMenuItem("Remove from dock") {
                done { LauncherStore.update { s -> s.copy(dock = s.dock.mapIndexed { i, p -> if (i == src.slot) null else p }) } }
            }
            is AppSource.Folder -> GlassMenuItem("Take out of folder") {
                done {
                    LauncherStore.update { s ->
                        val folder = s.pages.getOrNull(src.page)?.items?.firstOrNull { it is HomeItem.Folder && it.id == src.folderId } as? HomeItem.Folder
                            ?: return@update s
                        val left = folder.apps - app.packageName
                        // A folder left with one app turns back into that app.
                        val replaced = s.withPage(src.page) { items ->
                            items.map { if (it == folder) (if (left.size == 1) HomeItem.App(left[0], folder.col, folder.row) else folder.copy(apps = left)) else it }
                                .filterNot { it is HomeItem.Folder && it.apps.isEmpty() }
                        }
                        replaced.addApp(app.packageName, src.page)
                    }
                }
            }
            AppSource.Drawer -> GlassMenuItem("Add to home") {
                done { LauncherStore.update { it.addApp(app.packageName, currentPage) } }
            }
        }
        if (target.source !is AppSource.Dock) GlassMenuItem("Add to dock") {
            done {
                val s = LauncherStore.state.value
                val slot = s.dock.indexOfFirst { it == null }
                when {
                    app.packageName in s.dock -> Toast.makeText(context, "Already in the dock", Toast.LENGTH_SHORT).show()
                    slot < 0 -> Toast.makeText(context, "Dock is full — free a slot or make it bigger in Home settings", Toast.LENGTH_LONG).show()
                    else -> LauncherStore.update { it.copy(dock = it.dock.mapIndexed { i, p -> if (i == slot) app.packageName else p }) }
                }
            }
        }
        if (target.source is AppSource.Drawer) GlassMenuItem("Move to ring…") { onDismiss(); onMoveToRing(app) }
        GlassMenuItem("App info") { done { openAppInfo(context, app.packageName) } }
        GlassMenuItem("Uninstall") { done { uninstallApp(context, app.packageName) } }
    }
}

class AppShortcut(val info: ShortcutInfo, val label: String, val icon: Bitmap?)

/** Up to 4 of an app's shortcuts (static ones first). Only works while Other World is the home app. */
fun loadShortcuts(context: Context, pkg: String): List<AppShortcut> = runCatching {
    val la = context.getSystemService(LauncherApps::class.java)
    if (!la.hasShortcutHostPermission()) return emptyList()
    val q = LauncherApps.ShortcutQuery().setPackage(pkg).setQueryFlags(
        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
    )
    val dpi = context.resources.displayMetrics.densityDpi
    la.getShortcuts(q, android.os.Process.myUserHandle()).orEmpty()
        .filter { it.isEnabled }
        .sortedWith(compareBy({ !it.isDeclaredInManifest }, { it.rank }))
        .take(4)
        .map { sc ->
            val label = (sc.shortLabel ?: sc.longLabel ?: "").toString()
            val icon = runCatching { la.getShortcutIconDrawable(sc, dpi)?.let { drawableToBitmap(it, 96) } }.getOrNull()
            AppShortcut(sc, label, icon)
        }
}.getOrDefault(emptyList())

fun startShortcut(context: Context, sc: ShortcutInfo) {
    runCatching { context.getSystemService(LauncherApps::class.java).startShortcut(sc, null, null) }
}
