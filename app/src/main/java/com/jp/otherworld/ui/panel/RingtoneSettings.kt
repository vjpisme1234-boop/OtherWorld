package com.jp.otherworld.ui.panel

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.jp.otherworld.data.Ringtone
import com.jp.otherworld.ui.common.*

/** Home settings → Ringtone: hear the "Radio control" ringtone and make it the phone's ringtone. */
@Composable
fun RingtoneSettings() {
    val context = LocalContext.current
    var playing by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf(Ringtone.isCurrent(context)) }
    // Set right after the user comes back from allowing "Modify system settings".
    var waitingForPermission by remember { mutableStateOf(false) }

    fun set() {
        if (!Ringtone.canSet(context)) {
            waitingForPermission = true
            Toast.makeText(context, "Allow Other World to modify system settings, then come back", Toast.LENGTH_LONG).show()
            Ringtone.askPermission(context)
            return
        }
        val ok = Ringtone.setAsRingtone(context)
        current = Ringtone.isCurrent(context)
        Toast.makeText(context, if (ok) "Radio Control is your ringtone" else "Couldn't set the ringtone", Toast.LENGTH_SHORT).show()
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                if (waitingForPermission && Ringtone.canSet(context)) { waitingForPermission = false; set() }
                current = Ringtone.isCurrent(context)
            }
            if (e == Lifecycle.Event.ON_PAUSE) playing = false
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    if (playing) RingtoneVideo { playing = false }

    GlassLabel("Ringtone", 18.sp, GlassCyan)
    Column(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                GlassLabel("Radio Control", 16.sp)
                GlassLabel(if (current) "Your phone's ringtone ✓" else "“I was just trying to tell you… you have a phone call”", 13.sp, GlassDim)
            }
            // Play: the alien says it (video with the ringtone's sound).
            GlassButton("Play", selected = playing) { playing = true }
        }
        GlassButton(if (current) "Set again" else "Set as phone ringtone", selected = !current, modifier = Modifier.fillMaxWidth()) { set() }
        GlassLabel("Also added to your phone's ringtone list, so you can pick it for a contact in Settings.", 12.sp, GlassDim)
    }
}
