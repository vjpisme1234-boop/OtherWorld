package com.jp.otherworld.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jp.otherworld.data.*
import com.jp.otherworld.ui.common.*

/** Add, delete, reorder pages and pick the default one. */
@Composable
fun EditPages(state: LauncherState, apps: Map<String, AppInfo>, onClose: () -> Unit) {
    var confirmDelete by remember { mutableStateOf<Int?>(null) }
    Column(
        Modifier.fillMaxSize().background(Color(0xE6020814)).systemBarsPadding().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GlassLabel("Edit pages", 26.sp, weight = FontWeight.Light, modifier = Modifier.weight(1f))
            GlassButton("Done", selected = true) { onClose() }
        }
        Spacer(Modifier.height(24.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            state.pages.forEachIndexed { i, page ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PageThumb(page, apps, state, isDefault = i == state.defaultPage)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GlassButton("◀") { if (i > 0) LauncherStore.update { it.copy(pages = it.pages.swap(i, i - 1), defaultPage = it.defaultPage.swapped(i, i - 1)) } }
                        GlassButton(if (i == state.defaultPage) "★" else "☆", selected = i == state.defaultPage) {
                            LauncherStore.update { it.copy(defaultPage = i) }
                        }
                        GlassButton("✕") {
                            if (state.pages.size > 1) { if (page.items.isEmpty()) deletePage(i) else confirmDelete = i }
                        }
                        GlassButton("▶") { if (i < state.pages.size - 1) LauncherStore.update { it.copy(pages = it.pages.swap(i, i + 1), defaultPage = it.defaultPage.swapped(i, i + 1)) } }
                    }
                }
            }
            Box(
                Modifier.size(150.dp, 240.dp).glass(RoundedCornerShape(18.dp)).clickable {
                    LauncherStore.update { it.copy(pages = it.pages + Page()) }
                },
                contentAlignment = Alignment.Center
            ) { GlassLabel("+", 48.sp, GlassCyan, FontWeight.Light) }
        }
    }
    confirmDelete?.let { i ->
        GlassSheet({ confirmDelete = null }) {
            GlassLabel("Delete page ${i + 1}?", 20.sp)
            GlassLabel("Its apps and widgets will be removed from home (apps stay installed).", 14.sp, GlassDim)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton("Cancel") { confirmDelete = null }
                GlassButton("Delete", selected = true) { deletePage(i); confirmDelete = null }
            }
        }
    }
}

private fun deletePage(i: Int) {
    val s = LauncherStore.state.value
    s.pages[i].items.filterIsInstance<HomeItem.Widget>().forEach { WidgetHost.delete(it.id) }
    LauncherStore.update {
        val pages = it.pages.filterIndexed { k, _ -> k != i }
        val def = when { it.defaultPage == i -> 0; it.defaultPage > i -> it.defaultPage - 1; else -> it.defaultPage }
        it.copy(pages = pages, defaultPage = def.coerceIn(0, pages.size - 1))
    }
}

private fun <T> List<T>.swap(a: Int, b: Int) = toMutableList().also { val t = it[a]; it[a] = it[b]; it[b] = t }
private fun Int.swapped(a: Int, b: Int) = when (this) { a -> b; b -> a; else -> this }

/** A small picture of a page: its icons at their cells, widgets as glass blocks. */
@Composable
private fun PageThumb(page: Page, apps: Map<String, AppInfo>, state: LauncherState, isDefault: Boolean) {
    val w = 150.dp; val h = 240.dp
    val cw = w / GRID_COLS; val ch = h / GRID_ROWS
    Box(
        Modifier.size(w, h).glass(RoundedCornerShape(18.dp))
            .then(if (isDefault) Modifier.border(2.dp, GlassCyan, RoundedCornerShape(18.dp)) else Modifier)
    ) {
        for (item in page.items) {
            val mod = Modifier.offset(cw * item.col, ch * item.row).size(cw * item.spanX, ch * item.spanY).padding(3.dp)
            when (item) {
                is HomeItem.App -> apps[item.pkg]?.let { Image(it.iconFor(state.iconStyle).asImageBitmap(), null, mod) }
                is HomeItem.Folder -> Image(GlassIcons.tile.asImageBitmap(), null, mod)
                is HomeItem.Glass -> Box(mod.background(Color(0x7749A6FF), RoundedCornerShape(6.dp)))
                is HomeItem.Widget -> Box(mod.background(Color(0x5549A6FF), RoundedCornerShape(6.dp)))
            }
        }
    }
}
