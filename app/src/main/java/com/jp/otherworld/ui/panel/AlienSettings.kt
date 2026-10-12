package com.jp.otherworld.ui.panel

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.jp.otherworld.data.*
import com.jp.otherworld.service.NotificationWatcher
import com.jp.otherworld.ui.common.*

fun hasNotificationAccess(context: Context) =
    context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

fun openNotificationAccess(context: Context) {
    val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
        .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(context, NotificationWatcher::class.java).flattenToString())
    runCatching { context.startActivity(detail) }.onFailure { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
}

private fun clock(m: Int): String {
    val h = m / 60; val min = m % 60
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "%d:%02d %s".format(h12, min, if (h < 12) "AM" else "PM")
}

/** Home settings → Alien: notification glow (and per-app colours) and night mode. */
@Composable
fun AlienSettings(state: LauncherState, apps: Map<String, AppInfo>, onPreviewEffect: (String) -> Unit) {
    val context = LocalContext.current
    var access by remember { mutableStateOf(hasNotificationAccess(context)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) access = hasNotificationAccess(context) }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    var colorsOpen by remember { mutableStateOf(false) }

    GlassLabel("Alien", 18.sp, GlassCyan)
    Column(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        GlassLabel("Works on the animated alien wallpapers. Leaning toward your finger and battery mood are always on.", 13.sp, GlassDim)
        // Talk along (test)
        val askMic = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok ->
            if (!ok) LauncherStore.update { it.copy(talkMode = "OFF") }
        }
        val micOk = context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        GlassLabel("Talk along", modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ASSISTANT" to "Assistants", "ANY" to "Any sound", "OFF" to "Off").forEach { (k, name) ->
                GlassButton(name, Modifier.weight(1f), selected = state.talkMode == k && (k == "OFF" || micOk)) {
                    LauncherStore.update { it.copy(talkMode = k, talkAsked = true) }
                    if (k != "OFF" && !micOk) askMic.launch(android.Manifest.permission.RECORD_AUDIO)
                }
            }
        }
        GlassLabel(
            (if (!micOk && state.talkMode != "OFF") "Needs the microphone: tap Assistants to allow. " else "") +
                "His lips move when Claude, Google Assistant, Alexa, Bixby or a voice chat talks (Any sound: music and videos too). Only the loudness is used; nothing is recorded.",
            13.sp, GlassDim
        )

        // Notification glow
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassLabel("Glow on new notifications", modifier = Modifier.weight(1f))
            GlassButton("On", selected = state.notifGlow) {
                LauncherStore.update { it.copy(notifGlow = true) }
                if (!access) openNotificationAccess(context)
            }
            GlassButton("Off", selected = !state.notifGlow) { LauncherStore.update { it.copy(notifGlow = false) } }
        }
        if (!access) {
            GlassLabel("Needs Notification access. Other World only uses it for the glow and the dots on icons; nothing is saved or sent.", 13.sp, GlassDim)
            GlassButton("Allow notification access") { openNotificationAccess(context) }
        }
        GlassLabel("Messages green · Calls blue · Email yellow · Social pink · Everything else white", 13.sp, GlassDim)
        GlassButton("Colour per app…") { colorsOpen = true }

        // Hidden drawer effect (no names that give the drawer away)
        GlassLabel("Break-apart style", 15.sp)
        GlassLabel("Tap Preview to watch it on the alien now.", 13.sp, GlassDim)
        listOf("VIDEO" to "Dust & glass case", "VIDEO_SMOKE" to "Smoke & glass cube", "DUST" to "Dust", "SHARDS" to "Shards", "SPARKS" to "Sparks").forEach { (k, name) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton(name, Modifier.weight(1f), selected = state.vaultEffect == k) { LauncherStore.update { it.copy(vaultEffect = k) } }
                GlassButton("Preview") { onPreviewEffect(k) }
            }
        }

        // Night mode
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassLabel("Night mode (dim, sleepy eyes)", modifier = Modifier.weight(1f))
            GlassButton("On", selected = state.nightOn) { LauncherStore.update { it.copy(nightOn = true) } }
            GlassButton("Off", selected = !state.nightOn) { LauncherStore.update { it.copy(nightOn = false) } }
        }
        if (state.nightOn) {
            TimeRow("Starts", state.nightStart) { m -> LauncherStore.update { it.copy(nightStart = m) } }
            TimeRow("Ends", state.nightEnd) { m -> LauncherStore.update { it.copy(nightEnd = m) } }
        }
    }

    if (colorsOpen) GlowColorSheet(state, apps) { colorsOpen = false }
}

@Composable
private fun TimeRow(label: String, minutes: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassLabel(label, modifier = Modifier.weight(1f))
        GlassButton("−") { onChange((minutes - 30 + 1440) % 1440) }
        GlassLabel(clock(minutes), 16.sp, modifier = Modifier.widthIn(min = 80.dp))
        GlassButton("+") { onChange((minutes + 30) % 1440) }
    }
}

/** Pick a glow colour for one app: Default (by type), a colour, or no glow. */
@Composable
private fun GlowColorSheet(state: LauncherState, apps: Map<String, AppInfo>, onDismiss: () -> Unit) {
    var picking by remember { mutableStateOf<String?>(null) }
    val list = remember(apps) { apps.values.sortedBy { it.label.lowercase() } }
    GlassSheet(onDismiss, width = 420.dp) {
        GlassLabel("Glow colour per app", 20.sp)
        LazyColumn(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(list, key = { it.packageName }) { app ->
                val chosen = state.glowColors[app.packageName]
                Column {
                    Row(
                        Modifier.fillMaxWidth().clickable { picking = if (picking == app.packageName) null else app.packageName }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Image(app.icon.asImageBitmap(), null, Modifier.size(32.dp))
                        GlassLabel(app.label, 15.sp, modifier = Modifier.weight(1f))
                        when (chosen) {
                            null -> GlassLabel("Default", 13.sp, GlassDim)
                            GLOW_OFF -> GlassLabel("No glow", 13.sp, GlassDim)
                            else -> Box(Modifier.size(18.dp).clip(CircleShape).background(Color(chosen)))
                        }
                    }
                    if (picking == app.packageName) Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically
                    ) {
                        fun set(v: Int?) {
                            LauncherStore.update { s -> s.copy(glowColors = if (v == null) s.glowColors - app.packageName else s.glowColors + (app.packageName to v)) }
                            picking = null
                        }
                        GlassLabel("Default", 12.sp, GlassCyan, modifier = Modifier.clickable { set(null) })
                        GlowColors.palette.forEach { (_, c) ->
                            Box(
                                Modifier.size(20.dp).clip(CircleShape).background(Color(c))
                                    .then(if (chosen == c) Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
                                    .clickable { set(c) }
                            )
                        }
                        GlassLabel("Off", 12.sp, GlassCyan, modifier = Modifier.clickable { set(GLOW_OFF) })
                    }
                }
            }
        }
    }
}
