package com.jp.otherworld.ui.wheel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jp.otherworld.data.AppInfo
import com.jp.otherworld.ui.common.*

/** The wheel's glass search field: type to narrow the wheel to matching apps; Search opens the first one. */
@Composable
fun WheelSearch(query: String, onQuery: (String) -> Unit, onGo: () -> Unit, modifier: Modifier = Modifier) {
    val keyboard = LocalSoftwareKeyboardController.current
    Row(
        modifier.glass(RoundedCornerShape(22.dp)).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassLabel("⌕", 20.sp, GlassDim)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) GlassLabel("Search apps", 16.sp, GlassDim)
            BasicTextField(
                query, { onQuery(it.take(40)) }, singleLine = true,
                textStyle = TextStyle(color = GlassText, fontSize = 16.sp), cursorBrush = SolidColor(GlassCyan),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrect = false, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); onGo() }),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (query.isNotEmpty()) GlassLabel("✕", 16.sp, GlassDim, modifier = Modifier.padding(start = 10.dp).clickable { onQuery(""); keyboard?.hide() })
    }
}

/** Apps whose name matches: names starting with the text first, then a word starting with it, then anywhere. */
fun searchApps(apps: List<AppInfo>, query: String): List<AppInfo> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    fun rank(a: AppInfo): Int {
        val name = a.label.lowercase()
        return when {
            name.startsWith(q) -> 0
            name.split(' ', '-', '.', '_').any { it.startsWith(q) } -> 1
            q in name -> 2
            else -> 3
        }
    }
    return apps.map { it to rank(it) }.filter { it.second < 3 }.sortedWith(compareBy({ it.second }, { it.first.label.lowercase() })).map { it.first }
}
