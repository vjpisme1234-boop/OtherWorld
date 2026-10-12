package com.jp.otherworld.ui.panel

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.jp.otherworld.data.LauncherState
import com.jp.otherworld.data.LauncherStore
import com.jp.otherworld.service.GestureService
import com.jp.otherworld.ui.common.*

/** Home settings → Home gestures & dots. */
@Composable
fun HomeGestureSettings(state: LauncherState) {
    val context = LocalContext.current
    var lockReady by remember { mutableStateOf(GestureService.isEnabled(context)) }
    var dotsReady by remember { mutableStateOf(hasNotificationAccess(context)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) { lockReady = GestureService.isEnabled(context); dotsReady = hasNotificationAccess(context) }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    @Composable
    fun Toggle(label: String, on: Boolean, set: (Boolean) -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassLabel(label, modifier = Modifier.weight(1f))
            GlassButton("On", selected = on) { set(true) }
            GlassButton("Off", selected = !on) { set(false) }
        }
    }

    GlassLabel("Home gestures", 18.sp, GlassCyan)
    Column(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Toggle("Double-tap to lock", state.doubleTapLock) { v -> LauncherStore.update { it.copy(doubleTapLock = v) } }
        if (state.doubleTapLock && !lockReady) {
            GlassLabel("Needs Other World turned on in Accessibility (Installed apps → Other World). It's only used to lock the screen; fingerprint and face unlock keep working.", 13.sp, GlassDim)
            GlassButton("Open Accessibility") { GestureService.openSettings(context) }
        }
        Toggle("Swipe down: notifications", state.swipeDownNotifications) { v -> LauncherStore.update { it.copy(swipeDownNotifications = v) } }
        Toggle("Two-finger swipe down: quick settings", state.twoFingerQuickSettings) { v -> LauncherStore.update { it.copy(twoFingerQuickSettings = v) } }
        GlassLabel("Swipe up still opens the wheel.", 13.sp, GlassDim)
        Toggle("Notification dots on icons", state.showDots) { v -> LauncherStore.update { it.copy(showDots = v) } }
        if (state.showDots && !dotsReady) GlassButton("Allow notification access") { openNotificationAccess(context) }
    }

    // What spins in the middle of the wheel.
    GlassLabel("Wheel center", 18.sp, GlassCyan)
    Column(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        listOf("MORPH" to "Earth → smoke → Mars", "EARTH" to "Earth", "MARS" to "Mars", "NONE" to "Nothing").forEach { (k, name) ->
            GlassButton(name, Modifier.fillMaxWidth(), selected = state.wheelCenter == k) { LauncherStore.update { it.copy(wheelCenter = k) } }
        }
    }

    SoundSettings(state)
}
