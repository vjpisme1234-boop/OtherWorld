package com.jp.otherworld.ui.home

import android.Manifest
import android.app.WallpaperManager
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.jp.otherworld.data.LauncherStore
import com.jp.otherworld.ui.common.*
import com.jp.otherworld.ui.panel.hasNotificationAccess
import com.jp.otherworld.ui.panel.openNotificationAccess

/**
 * First run: everything Other World needs, in one place, each with its own button and a ✓ once done.
 * Replaces the one-at-a-time prompts (wallpaper offer, "let the alien talk?") for new installs.
 */
@Composable
fun SetupGuide(onDone: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }          // re-check everything when we come back
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        lifecycle.addObserver(obs); onDispose { lifecycle.removeObserver(obs) }
    }
    val isHome = remember(tick) { context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME) == true }
    val alien = remember(tick) { alienWallpaperSet(context) }
    val notif = remember(tick) { hasNotificationAccess(context) }
    val mic = remember(tick) { context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED }

    val askHome = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { tick++ }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    fun finish() {
        LauncherStore.update { it.copy(setupDone = true, globeOffered = true, talkAsked = true, talkMode = if (mic) it.talkMode else "OFF") }
        onDone()
    }
    BackHandler { finish() }

    Box(Modifier.fillMaxSize().background(Color(0xD9000814)).systemBarsPadding().padding(16.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxWidth().glass(RoundedCornerShape(28.dp), strong = true)
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlassLabel("Welcome to Other World", 24.sp, GlassText, FontWeight.Light)
            GlassLabel("A few things to set up. Each one is optional; you can change them later in Home settings.", 14.sp, GlassDim)
            Step("Make it your home screen", "So Home opens Other World.", isHome, "Set") {
                context.getSystemService(RoleManager::class.java)?.let { askHome.launch(it.createRequestRoleIntent(RoleManager.ROLE_HOME)) }
            }
            Step("Alien wallpaper", "The living alien behind your home screen.", alien, "Choose") {
                setLiveWallpaper(context, com.jp.otherworld.wallpaper.AlienGreenWallpaperService::class.java)
            }
            Step("Notifications", "The alien glows for new messages, apps get dots, and he tells you about missed calls. Nothing is saved or sent.", notif, "Allow") {
                openNotificationAccess(context)
            }
            Step("Talk along", "His mouth moves when your assistant (Claude, Google, Alexa, Bixby) talks. Uses the microphone permission for loudness only; nothing is recorded.", mic, "Allow") {
                askMic.launch(Manifest.permission.RECORD_AUDIO)
            }
            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                GlassLabel("Your secret drawer", 16.sp, GlassCyan)
                GlassLabel("Hold one finger on each of the alien's eyes for 6 seconds. Hidden numbers, apps, files and notes live there, only on this phone.", 13.sp, GlassDim)
            }
            GlassButton("Done", selected = true, modifier = Modifier.fillMaxWidth()) { finish() }
        }
    }
}

@Composable
private fun Step(title: String, info: String, done: Boolean, action: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            GlassLabel(title, 16.sp)
            GlassLabel(info, 12.sp, GlassDim)
        }
        if (done) GlassLabel("✓", 22.sp, GlassCyan) else GlassButton(action, selected = true, onClick = onClick)
    }
}

fun alienWallpaperSet(context: Context): Boolean = runCatching {
    WallpaperManager.getInstance(context).wallpaperInfo?.serviceName?.startsWith("com.jp.otherworld.wallpaper.Alien")
}.getOrNull() == true
