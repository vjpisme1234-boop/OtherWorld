package com.jp.otherworld.ui.common

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jp.otherworld.data.AppInfo

val ALPHABET = listOf("#") + ('A'..'Z').map { it.toString() }

/** The bar's letter for an app: its first letter, or # for anything else. */
fun letterOf(label: String): String {
    val c = label.trimStart().firstOrNull()?.uppercaseChar() ?: return "#"
    return if (c in 'A'..'Z') c.toString() else "#"
}

/** First index in `apps` for each letter that has apps. */
fun letterIndex(apps: List<AppInfo>): Map<String, Int> {
    val m = LinkedHashMap<String, Int>()
    apps.forEachIndexed { i, a -> m.putIfAbsent(letterOf(a.label), i) }
    return m
}

/**
 * A horizontal A–Z glass bar along the bottom. Touch or slide a finger along it: each letter under
 * the finger is reported once (with a tick), and a big letter bubble pops up above the finger.
 */
@Composable
fun AlphabetBar(available: Set<String>, onLetter: (String) -> Unit, modifier: Modifier = Modifier) {
    val view = LocalView.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    var width by remember { mutableIntStateOf(1) }
    var current by remember { mutableStateOf<String?>(null) }
    var fingerX by remember { mutableFloatStateOf(0f) }
    val onLetterNow by rememberUpdatedState(onLetter)

    fun pick(x: Float) {
        fingerX = x.coerceIn(0f, width.toFloat())
        val i = (x / width * ALPHABET.size).toInt().coerceIn(0, ALPHABET.size - 1)
        // Letters with no apps snap to the nearest one that has some.
        val letter = ALPHABET.indices.sortedBy { kotlin.math.abs(it - i) }.map { ALPHABET[it] }.firstOrNull { it in available } ?: return
        if (letter != current) {
            current = letter
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onLetterNow(letter)
        }
    }

    Box(modifier.height(34.dp)) {
        Row(
            Modifier
                .fillMaxSize()
                .glass(RoundedCornerShape(17.dp))
                .onSizeChanged { width = it.width }
                .pointerInput(available) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        pick(down.position.x)
                        do {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull() ?: break
                            if (ch.positionChange() != androidx.compose.ui.geometry.Offset.Zero) { ch.consume(); pick(ch.position.x) }
                        } while (ch.pressed)
                        current = null
                    }
                }
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ALPHABET.forEach { l ->
                GlassLabel(
                    l, 12.sp,
                    color = when { l == current -> GlassCyan; l in available -> GlassText; else -> Color(0x55FFFFFF) },
                    weight = if (l == current) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
        // Big bubble above the finger while sliding (overflows; the bar keeps its size).
        current?.let { l ->
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = with(density) { fingerX.toDp() } - 32.dp, y = (-64).dp)
                    .requiredSize(64.dp)
                    .glass(CircleShape, strong = true),
                contentAlignment = Alignment.Center
            ) { GlassLabel(l, 30.sp, GlassCyan, FontWeight.Medium) }
        }
    }
}
