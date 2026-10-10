package com.jp.otherworld.ui.panel

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jp.otherworld.data.AppInfo
import com.jp.otherworld.data.IconStyle
import com.jp.otherworld.ui.common.AlphabetBar
import com.jp.otherworld.ui.common.letterIndex
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import kotlinx.coroutines.launch

/** The app-drawer panel opened full screen: searchable grid of every app. Hold an app for its menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawer(apps: List<AppInfo>, style: IconStyle, onLaunch: (AppInfo) -> Unit, onMenu: (AppInfo) -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = remember(query, apps) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xF0020814))
            .systemBarsPadding()
            .imePadding()
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .background(Color(0x3349A6FF), RoundedCornerShape(22.dp))
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            if (query.isEmpty()) BasicText("Search apps", style = TextStyle(color = Color(0xFF7FA6C9), fontSize = 16.sp))
            BasicTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                textStyle = TextStyle(color = Color(0xFFEAF6FF), fontSize = 16.sp),
                cursorBrush = SolidColor(Color(0xFF7FE9FF)),
                modifier = Modifier.fillMaxWidth()
            )
        }
        val grid = rememberLazyGridState()
        val scope = rememberCoroutineScope()
        val letters = remember(shown) { letterIndex(shown) }
        Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = grid,
            columns = GridCells.Adaptive(84.dp),
            modifier = Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp, bottom = 64.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(shown, key = { it.packageName }) { app ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.combinedClickable(onLongClick = { onMenu(app) }) { onLaunch(app) }.padding(4.dp)
                ) {
                    com.jp.otherworld.ui.common.AppIcon(app, style, 56.dp)
                    Spacer(Modifier.height(6.dp))
                    BasicText(
                        app.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(color = Color(0xFFD5E6F5), fontSize = 12.sp, textAlign = TextAlign.Center)
                    )
                }
            }
        }
        AlphabetBar(
            letters.keys,
            onLetter = { l -> letters[l]?.let { scope.launch { grid.scrollToItem(it) } } },
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 12.dp).fillMaxWidth()
        )
        }
    }
}
