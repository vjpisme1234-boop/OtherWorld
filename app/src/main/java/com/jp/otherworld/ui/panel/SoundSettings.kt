package com.jp.otherworld.ui.panel

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jp.otherworld.data.LauncherState
import com.jp.otherworld.data.LauncherStore
import com.jp.otherworld.data.Sounds
import com.jp.otherworld.ui.common.*

/** Home settings → Wheel sounds: master switch, then per sound: on/off, take (or your own), volume, record. */
@Composable
fun SoundSettings(state: LauncherState) {
    GlassLabel("Wheel sounds", 18.sp, GlassCyan)
    Column(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassLabel("All sounds", modifier = Modifier.weight(1f))
            GlassButton("On", selected = state.sounds) { LauncherStore.update { it.copy(sounds = true) } }
            GlassButton("Off", selected = !state.sounds) { LauncherStore.update { it.copy(sounds = false) } }
        }
        GlassLabel("Silent when your phone is on vibrate or silent. Tap a number to hear it; the lit one is used. Hold Record to make your own (up to 3 seconds).", 13.sp, GlassDim)
        listOf("tick" to "Panel tick", "open" to "Open", "ring" to "Ring change", "drawer" to "Wheel opens", "boom" to "Earth → Mars boom").forEach { (k, name) ->
            SoundRow(state, k, name)
        }
    }
}

@Composable
private fun SoundRow(state: LauncherState, kind: String, name: String) {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }          // bumped when a recording is made or deleted
    val hasMine = remember(version) { Sounds.hasMine(kind) }
    var recording by remember { mutableStateOf(false) }
    val on = kind !in state.soundOff
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) Toast.makeText(context, "Now hold Record and make your sound", Toast.LENGTH_SHORT).show()
    }

    Column(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassLabel(name, 16.sp, modifier = Modifier.weight(1f))
            GlassButton("On", selected = on) { LauncherStore.update { it.copy(soundOff = it.soundOff - kind) } }
            GlassButton("Off", selected = !on) { LauncherStore.update { it.copy(soundOff = it.soundOff + kind) } }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // A saved pick that no longer ships (or a deleted "Mine") lights the default instead.
            val picked = (state.soundPick[kind] ?: Sounds.defaultPick(kind)).let { p ->
                if (p in Sounds.takes(kind) || (p == Sounds.MINE && hasMine)) p else Sounds.defaultPick(kind)
            }
            Sounds.takes(kind).forEach { n ->
                GlassButton("$n", selected = picked == n) {
                    LauncherStore.update { it.copy(soundPick = it.soundPick + (kind to n)) }
                    Sounds.play(kind, take = n)
                }
            }
            if (hasMine) GlassButton("Mine", selected = picked == Sounds.MINE) {
                LauncherStore.update { it.copy(soundPick = it.soundPick + (kind to Sounds.MINE)) }
                Sounds.play(kind, take = Sounds.MINE)
            }
        }
        // Volume: drag the bar; it plays the sound when you let go so you can hear the level.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassLabel("Volume", 13.sp, GlassDim)
            val vol = state.soundVolume[kind] ?: Sounds.defaultVolume(kind)
            GlassSlider(vol, Modifier.weight(1f),
                onChange = { v -> LauncherStore.update { it.copy(soundVolume = it.soundVolume + (kind to v)) } },
                onRelease = { Sounds.play(kind, test = true) })
            GlassLabel("${(vol * 100).toInt()}", 13.sp, GlassDim, modifier = Modifier.widthIn(min = 26.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Hold to record, let go to stop (stops by itself at 3 s); then it plays back and becomes "Mine".
            Box(
                Modifier.glass(RoundedCornerShape(18.dp), strong = recording)
                    .pointerInput(kind) {
                        detectTapGestures(onPress = {
                            val granted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                            if (!granted) { ask.launch(Manifest.permission.RECORD_AUDIO); return@detectTapGestures }
                            recording = Sounds.startRecording(context, kind) { recording = false; version++; Sounds.play(kind, take = Sounds.MINE) }
                            if (!recording) { Toast.makeText(context, "Couldn't start recording", Toast.LENGTH_SHORT).show(); return@detectTapGestures }
                            tryAwaitRelease()
                            if (recording) {
                                Sounds.stopRecording(); recording = false; version++
                                Sounds.play(kind, take = Sounds.MINE)
                            }
                        })
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (recording) Color(0xFFFF4D4D) else Color(0x88FF4D4D)))
                    GlassLabel(if (recording) "Recording…" else "Hold to record", 14.sp)
                }
            }
            if (hasMine) GlassButton("Delete mine") { Sounds.deleteMine(kind); version++ }
        }
    }
}

/** A thin glass volume bar with a glowing knob. Value 0..1. */
@Composable
fun GlassSlider(value: Float, modifier: Modifier = Modifier, onChange: (Float) -> Unit, onRelease: () -> Unit = {}) {
    var width by remember { mutableFloatStateOf(1f) }
    val set by rememberUpdatedState(onChange)
    val done by rememberUpdatedState(onRelease)
    Canvas(
        modifier.height(32.dp)
            .pointerInput(Unit) {
                width = size.width.toFloat()
                detectTapGestures(onTap = { o -> set((o.x / width).coerceIn(0f, 1f)); done() })
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(onDragEnd = { done() }) { c, _ ->
                    c.consume(); set((c.position.x / size.width).coerceIn(0f, 1f))
                }
            }
    ) {
        width = size.width
        val y = size.height / 2; val h = 6.dp.toPx(); val r = 10.dp.toPx()
        drawRoundRect(Color(0x33FFFFFF), Offset(0f, y - h / 2), Size(size.width, h), CornerRadius(h / 2))
        drawRoundRect(GlassCyan, Offset(0f, y - h / 2), Size(size.width * value, h), CornerRadius(h / 2))
        val x = size.width * value.coerceIn(0f, 1f)
        drawCircle(Color(0x557FE9FF), r * 1.6f, Offset(x, y))
        drawCircle(Color(0xFFE6FCFF), r, Offset(x, y))
    }
}
