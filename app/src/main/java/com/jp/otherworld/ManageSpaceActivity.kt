package com.jp.otherworld

import android.Manifest
import android.app.ActivityManager
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jp.otherworld.data.Vault
import com.jp.otherworld.ui.common.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android shows this in place of "Clear data" in Other World's storage settings (manageSpaceActivity).
 * Before wiping, it gives the hidden drawer's numbers, files and apps back to the phone, so clearing
 * the data never loses them. Uninstalling still erases everything — Android gives apps no chance then.
 */
class ManageSpaceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Vault.init(this)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { ManageSpace(onDone = { finish() }) }
    }
}

private enum class Step { Ask, Confirm, Working, Failed }

@Composable
private fun ManageSpace(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vault by Vault.state.collectAsState()
    var step by remember { mutableStateOf(Step.Ask) }
    var problems by remember { mutableStateOf(emptyList<String>()) }

    fun restoreAndClear() {
        step = Step.Working
        scope.launch {
            val failed = withContext(Dispatchers.IO) { Vault.restoreToPhone(context) }
            if (failed.isEmpty()) {
                // Wipes Other World's data and closes it; the phone keeps everything put back.
                context.getSystemService(ActivityManager::class.java).clearApplicationUserData()
            } else { problems = failed; step = Step.Failed }
        }
    }
    val askContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) restoreAndClear() else { problems = listOf("Contacts permission is needed to put the numbers back."); step = Step.Failed }
    }
    fun start() {
        val needContacts = vault.numbers.isNotEmpty() &&
            context.checkSelfPermission(Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED
        if (needContacts) askContacts.launch(Manifest.permission.WRITE_CONTACTS) else restoreAndClear()
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xFF020814)).systemBarsPadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        GlassLabel("Clear Other World's data", 26.sp, weight = FontWeight.Light)
        Column(
            Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlassLabel(
                "This resets Other World: home pages, dock, widgets and settings.\n\n" +
                    "Anything in the hidden drawer goes back to your phone first:\n" +
                    "• ${vault.numbers.size} phone number(s) → your Contacts\n" +
                    "• ${vault.files.size} file(s) → Pictures, Movies, Music or Download, in an \"Other World\" folder\n" +
                    "• ${vault.notes.size} note(s) → text files in Download/Other World\n" +
                    "• ${vault.hiddenApps.size} hidden app(s) → shown again",
                15.sp
            )
            when (step) {
                Step.Ask -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton("Put back & clear") { step = Step.Confirm }
                    GlassButton("Cancel", onClick = onDone)
                }
                Step.Confirm -> {
                    GlassLabel("Are you sure? This can't be undone.", 15.sp, GlassCyan)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassButton("Yes, clear it", selected = true) { start() }
                        GlassButton("Cancel", onClick = onDone)
                    }
                }
                Step.Working -> GlassLabel("Putting everything back…", 15.sp, GlassCyan)
                Step.Failed -> {
                    GlassLabel("Nothing was cleared, because some things couldn't go back:", 15.sp, GlassCyan)
                    problems.forEach { GlassLabel("• $it", 14.sp, GlassDim) }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassButton("Try again") { start() }
                        GlassButton("Close", onClick = onDone)
                    }
                }
            }
        }
    }
}
