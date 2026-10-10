package com.jp.otherworld.ui.home

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.AlarmClock
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jp.otherworld.data.Weather
import com.jp.otherworld.ui.common.*
import kotlinx.coroutines.delay
import java.util.Date

/** Other World's own widgets: (kind, name, default columns × rows). */
val GLASS_WIDGETS = listOf(
    Triple("CLOCK", "Glass clock", 4 to 2),
    Triple("WEATHER", "Glass weather", 4 to 2),
    Triple("BATTERY", "Glass battery", 2 to 2)
)

/** One glass widget on home: the shared glass card, its content, tap action, hold to edit. */
@Composable
fun GlassWidget(kind: String, onHold: () -> Unit) {
    val context = LocalContext.current
    // What a tap does; a plain holder (not state) so setting it while drawing doesn't redraw.
    val tap = remember { arrayOfNulls<() -> Unit>(1) }
    Box(
        Modifier.fillMaxSize().glass(RoundedCornerShape(24.dp))
            .pointerInput(kind) { detectTapGestures(onTap = { tap[0]?.invoke() }, onLongPress = { onHold() }) }
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        when (kind) {
            "CLOCK" -> { tap[0] = { runCatching { context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }; ClockContent() }
            "WEATHER" -> WeatherContent { tap[0] = it }
            "BATTERY" -> { tap[0] = { runCatching { context.startActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }; BatteryContent() }
        }
    }
}

@Composable
private fun ClockContent() {
    val context = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(1000L - System.currentTimeMillis() % 1000) } }
    val time = DateFormat.format(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm", now).toString()
    val date = DateFormat.format("EEEE, MMMM d", now).toString()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GlassLabel(time, 44.sp, GlassText, FontWeight.Light)
        GlassLabel(date, 14.sp, GlassCyan)
    }
}

@Composable
private fun WeatherContent(setTap: (() -> Unit) -> Unit) {
    val context = LocalContext.current
    val now by Weather.now.collectAsState()
    var allowed by remember { mutableStateOf(Weather.hasPermission(context)) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> allowed = ok; refreshKey++ }
    LaunchedEffect(allowed, refreshKey) { while (true) { Weather.refresh(context, force = refreshKey > 0); delay(30 * 60 * 1000L) } }
    setTap { if (!allowed) ask.launch(Manifest.permission.ACCESS_COARSE_LOCATION) else refreshKey++ }
    val w = now
    when {
        !allowed && w == null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GlassLabel("Weather", 18.sp, GlassCyan)
            GlassLabel("Tap to use your rough location", 13.sp, GlassDim)
        }
        w == null -> GlassLabel("Getting weather…", 14.sp, GlassDim)
        else -> {
            val (words, symbol) = Weather.describe(w.code)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                GlassLabel(symbol, 40.sp)
                Column {
                    GlassLabel("${w.temp}${w.unit}", 36.sp, GlassText, FontWeight.Light)
                    GlassLabel("$words · ${w.hi}° / ${w.lo}°", 13.sp, GlassCyan)
                }
            }
        }
    }
}

@Composable
private fun BatteryContent() {
    val context = LocalContext.current
    var level by remember { mutableFloatStateOf(1f) }
    var charging by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (l >= 0 && s > 0) level = l / s.toFloat()
                charging = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            }
        }
        context.registerReceiver(r, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { context.unregisterReceiver(r) }
    }
    val ring = if (level <= 0.15f) Color(0xFFFF6A6A) else GlassCyan
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize(0.92f).aspectRatio(1f)) {
            val sw = size.minDimension * 0.09f
            val d = size.minDimension - sw
            val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
            drawArc(Color(0x33FFFFFF), -90f, 360f, false, tl, Size(d, d), style = Stroke(sw))
            drawArc(ring, -90f, 360f * level, false, tl, Size(d, d), style = Stroke(sw, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GlassLabel("${(level * 100).toInt()}%", 22.sp, GlassText, FontWeight.Light)
            if (charging) GlassLabel("⚡ charging", 11.sp, GlassCyan)
        }
    }
}
