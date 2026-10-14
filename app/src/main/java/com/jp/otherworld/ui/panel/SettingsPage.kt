package com.jp.otherworld.ui.panel

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.jp.otherworld.data.*
import com.jp.otherworld.ui.common.*
import com.jp.otherworld.ui.home.GlassIcons

/** Home settings — the launcher's own settings: Dock & icons, and the glass "Set as home" button. */
@Composable
fun SettingsPage(state: LauncherState, apps: Map<String, AppInfo>, onChooseDockApp: (Int) -> Unit, onPreviewEffect: (String) -> Unit) {
    val context = LocalContext.current
    val button = remember {
        context.assets.open("glass_button_home.png").use { BitmapFactory.decodeStream(it) }.asImageBitmap()
    }
    // Re-check when we come back from the system's home-app picker.
    var isHome by remember { mutableStateOf(isDefaultHome(context)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) isHome = isDefaultHome(context) }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xF0020814)).systemBarsPadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        GlassLabel("Home settings", 28.sp, weight = FontWeight.Light)

        // How to get into the hidden drawer, until the user deletes it for good.
        if (!state.secretHintDeleted) {
            GlassLabel("Hidden drawer", 18.sp, GlassCyan)
            Column(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlassLabel(
                    "Other World has a secret drawer for phone numbers, apps and files you want kept apart " +
                        "from the rest of your phone.\n\n" +
                        "To open it: on the home screen, put one finger on each of the alien's eyes and hold " +
                        "both for 6 seconds. A ring fills up over each eye; when they're full, the drawer opens. " +
                        "Lifting a finger starts over.\n\n" +
                        "Nothing else opens it, so remember how before you delete these instructions.",
                    15.sp
                )
                GlassButton("Click here to delete these instructions") {
                    LauncherStore.update { it.copy(secretHintDeleted = true) }
                }
            }
        }

        GlassLabel("Dock & icons", 18.sp, GlassCyan)
        Column(
            Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Dock size
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassLabel("Dock size", modifier = Modifier.weight(1f))
                GlassButton("−") { LauncherStore.update { s -> if (s.dockSize > DOCK_MIN) s.copy(dock = s.dock.dropLast(1)) else s } }
                GlassLabel("  ${state.dockSize}  ", 18.sp)
                GlassButton("+") { LauncherStore.update { s -> if (s.dockSize < DOCK_MAX) s.copy(dock = s.dock + null) else s } }
            }
            // Dock apps: tap a slot to choose, long-press to empty it.
            GlassLabel("Dock apps — tap to choose, hold to clear", 14.sp, GlassDim)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                state.dock.forEachIndexed { i, pkg ->
                    val app = pkg?.let { apps[it] }
                    val bmp = app?.iconFor(state.iconStyle) ?: GlassIcons.tile
                    Image(
                        bmp.asImageBitmap(), app?.label ?: "Empty slot",
                        Modifier.size(48.dp).alpha(if (app == null) 0.4f else 1f).pointerInput(i) {
                            detectTapGestures(
                                onTap = { onChooseDockApp(i) },
                                onLongPress = { LauncherStore.update { s -> s.copy(dock = s.dock.mapIndexed { k, p -> if (k == i) null else p }) } }
                            )
                        }
                    )
                }
            }
            // Icon size
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassLabel("Icon size", modifier = Modifier.weight(1f))
                IconSize.entries.forEach { size ->
                    GlassButton(size.name, selected = state.iconSize == size) { LauncherStore.update { it.copy(iconSize = size) } }
                }
            }
            // Labels
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassLabel("App names on home", modifier = Modifier.weight(1f))
                GlassButton("On", selected = state.showLabels) { LauncherStore.update { it.copy(showLabels = true) } }
                GlassButton("Off", selected = !state.showLabels) { LauncherStore.update { it.copy(showLabels = false) } }
            }
        }

        HomeGestureSettings(state)

        AlienSettings(state, apps, onPreviewEffect)

        Spacer(Modifier.height(8.dp))
        // The generated glass button; its right side is left clear for the label.
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .aspectRatio(1376f / 768f)
                .clickable { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
        ) {
            Image(button, contentDescription = "Set as home", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(0.5f).align(Alignment.CenterEnd).padding(end = 30.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    if (isHome) "Home ✓" else "Set as home",
                    maxLines = 1,
                    style = TextStyle(color = Color(0xFFDDF6FF), fontSize = 20.sp, fontWeight = FontWeight.Medium)
                )
            }
        }
    }
}

private fun isDefaultHome(context: Context): Boolean {
    val rm = context.getSystemService(RoleManager::class.java) ?: return false
    return rm.isRoleHeld(RoleManager.ROLE_HOME)
}
