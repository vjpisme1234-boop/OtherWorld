package com.jp.otherworld.ui.vault

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.provider.DocumentsContract
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.imePadding
import com.jp.otherworld.data.VaultNote
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.jp.otherworld.data.*
import com.jp.otherworld.ui.common.*
import com.jp.otherworld.ui.panel.AppChooser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Tab(val title: String) { Numbers("Numbers"), Apps("Apps"), Files("Files"), Notes("Notes") }

/** The hidden drawer: numbers, hidden apps and files, kept apart from the phone's own. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VaultScreen(allApps: List<AppInfo>, style: IconStyle, background: ImageBitmap?, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vault by Vault.state.collectAsState()
    var tab by remember { mutableStateOf(Tab.Numbers) }
    var adding by remember { mutableStateOf(false) }
    var choosingApp by remember { mutableStateOf(false) }
    var numberMenu by remember { mutableStateOf<VaultNumber?>(null) }
    var fileMenu by remember { mutableStateOf<VaultFile?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    // The note being edited: null = none, a note with blank id = a new one.
    var editing by remember { mutableStateOf<VaultNote?>(null) }

    // Panic close: turn the phone face-down and the drawer shuts at once.
    DisposableEffect(Unit) {
        val sm = context.getSystemService(android.hardware.SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(android.hardware.Sensor.TYPE_GRAVITY) ?: sm?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
        var downSince = -1L
        val listener = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(e: android.hardware.SensorEvent) {
                val now = android.os.SystemClock.uptimeMillis()
                if (e.values[2] < -7f) {
                    if (downSince < 0) downSince = now
                    if (now - downSince > 300) { downSince = Long.MAX_VALUE / 2; onClose() }
                } else downSince = -1L
            }
            override fun onAccuracyChanged(s: android.hardware.Sensor?, a: Int) {}
        }
        sensor?.let { sm.registerListener(listener, it, android.hardware.SensorManager.SENSOR_DELAY_UI) }
        onDispose { sm?.unregisterListener(listener) }
    }

    // Back closes the top box first, then the drawer.
    androidx.activity.compose.BackHandler(adding || choosingApp || numberMenu != null || fileMenu != null || editing != null) {
        adding = false; choosingApp = false; numberMenu = null; fileMenu = null; editing = null
    }

    // Opened files are copied to a temp folder; wipe it when the drawer closes.
    DisposableEffect(Unit) { onDispose { Vault.clearTemp(context) } }

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        busy = "Moving ${uris.size} file(s) in…"
        scope.launch {
            var stored = 0; var notDeleted = 0
            withContext(Dispatchers.IO) {
                for (u in uris) {
                    if (Vault.importFile(context, u)) {
                        stored++
                        // Move = remove the original once the copy is safe.
                        val gone = runCatching { DocumentsContract.deleteDocument(context.contentResolver, u) }.getOrDefault(false)
                        if (!gone) notDeleted++
                    }
                }
            }
            busy = null
            val msg = buildString {
                append("$stored file(s) moved into the drawer")
                if (notDeleted > 0) append(" — $notDeleted original(s) couldn't be deleted from where they were")
            }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }
    var exporting by remember { mutableStateOf<VaultFile?>(null) }
    val saveOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val f = exporting ?: return@rememberLauncherForActivityResult
        exporting = null
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { Vault.exportTo(context, f, uri) }.isSuccess }
            Toast.makeText(context, if (ok) "Saved a copy of ${f.name}" else "Couldn't save ${f.name}", Toast.LENGTH_SHORT).show()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // The user's picture, whole and untouched.
        background?.let { Image(it, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassLabel("Hidden drawer", 26.sp, weight = FontWeight.Light, modifier = Modifier.weight(1f))
                GlassButton("Close") { onClose() }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tab.entries.forEach { t -> GlassButton(t.title, selected = tab == t) { tab = t } }
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    // Numbers and hidden apps are shown inside the glass case (below), not here.
                    Tab.Numbers, Tab.Apps -> {}
                    Tab.Notes -> if (vault.notes.isEmpty()) Empty("No notes yet") else
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(vault.notes, key = { it.id }) { n ->
                                Column(
                                    Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp))
                                        .combinedClickable { editing = n }
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    GlassLabel(n.title.ifBlank { n.body.lineSequence().firstOrNull().orEmpty().ifBlank { "Note" } }, 17.sp)
                                    GlassLabel(
                                        android.text.format.DateUtils.getRelativeTimeSpanString(n.edited).toString() + " · " +
                                            n.body.replace('\n', ' ').take(60), 13.sp, GlassDim
                                    )
                                }
                            }
                        }
                    Tab.Files -> if (vault.files.isEmpty()) Empty("No files yet") else
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(vault.files, key = { it.id }) { f ->
                                Column(
                                    Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp))
                                        .combinedClickable(onLongClick = { fileMenu = f }) { fileMenu = f }
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    GlassLabel(f.name, 16.sp)
                                    GlassLabel(Formatter.formatShortFileSize(context, f.size), 13.sp, GlassDim)
                                }
                            }
                        }
                }
            }
            Spacer(Modifier.height(10.dp))
            GlassButton(
                when (tab) { Tab.Numbers -> "+ Add number"; Tab.Apps -> "+ Hide an app"; Tab.Files -> "+ Move files in"; Tab.Notes -> "+ New note" },
                selected = true, modifier = Modifier.fillMaxWidth()
            ) {
                when (tab) {
                    Tab.Numbers -> adding = true
                    Tab.Apps -> choosingApp = true
                    Tab.Files -> pickFiles.launch(arrayOf("*/*"))
                    Tab.Notes -> editing = VaultNote("", "", "", 0)
                }
            }
            if (tab == Tab.Apps && vault.hiddenApps.isNotEmpty())
                GlassLabel("Tap to open · hold to put back", 13.sp, GlassDim, modifier = Modifier.padding(top = 6.dp).align(Alignment.CenterHorizontally))
        }
        // Inside the display case: where the case's glass sits in the picture (fit to the screen, centred).
        if (tab == Tab.Numbers || tab == Tab.Apps) BoxWithConstraints(Modifier.fillMaxSize()) {
            val pic = background
            val iw = (pic?.width ?: CASE_IMG_W).toFloat(); val ih = (pic?.height ?: CASE_IMG_H).toFloat()
            val scale = minOf(maxWidth.value / iw, maxHeight.value / ih)
            val left = (maxWidth.value - iw * scale) / 2; val top = (maxHeight.value - ih * scale) / 2
            Box(
                Modifier
                    .offset((left + CASE_L * iw * scale).dp, (top + CASE_T * ih * scale).dp)
                    .size(((CASE_R - CASE_L) * iw * scale).dp, ((CASE_B - CASE_T) * ih * scale).dp)
            ) {
                if (tab == Tab.Numbers) {
                    if (vault.numbers.isEmpty()) Empty("No numbers yet") else
                        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(4.dp)) {
                            items(vault.numbers, key = { it.id }) { n ->
                                Row(
                                    Modifier.fillMaxWidth().glass(RoundedCornerShape(12.dp))
                                        .combinedClickable(onLongClick = { numberMenu = n }) { numberMenu = n }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    GlassLabel(n.name.ifBlank { "No name" }, 15.sp, modifier = Modifier.weight(1f))
                                    GlassLabel(n.number, 14.sp, GlassCyan)
                                }
                            }
                        }
                } else {
                    val hidden = allApps.filter { it.packageName in vault.hiddenApps }
                    if (hidden.isEmpty()) Empty("No hidden apps yet") else
                        LazyVerticalGrid(GridCells.Adaptive(68.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(hidden, key = { it.packageName }) { app ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.combinedClickable(onLongClick = {
                                        Vault.unhideApp(app.packageName)
                                        Toast.makeText(context, "${app.label} is back on your home and drawer", Toast.LENGTH_SHORT).show()
                                    }) { launchApp(context, app.packageName) }.padding(2.dp)
                                ) {
                                    Image(app.iconFor(style).asImageBitmap(), app.label, Modifier.size(44.dp))
                                    Spacer(Modifier.height(4.dp))
                                    BasicText(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        style = TextStyle(color = GlassText, fontSize = 10.sp, textAlign = TextAlign.Center))
                                }
                            }
                        }
                }
            }
        }
    }

    if (adding) AddNumber(onDismiss = { adding = false }) { name, number -> Vault.addNumber(name, number); adding = false }
    if (choosingApp) AppChooser("Hide an app", allApps.filter { it.packageName !in vault.hiddenApps }, style) { app ->
        Vault.hideApp(app.packageName); choosingApp = false
    }
    numberMenu?.let { n ->
        GlassSheet({ numberMenu = null }) {
            GlassLabel(n.name, 20.sp); GlassLabel(n.number, 16.sp, GlassCyan)
            GlassMenuItem("Copy number") {
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("number", n.number))
                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show(); numberMenu = null
            }
            GlassMenuItem("Delete") { Vault.removeNumber(n.id); numberMenu = null }
        }
    }
    fileMenu?.let { f ->
        GlassSheet({ fileMenu = null }) {
            GlassLabel(f.name, 18.sp)
            GlassMenuItem("Open") {
                fileMenu = null
                scope.launch {
                    val tmp = withContext(Dispatchers.IO) { runCatching { Vault.decryptToTemp(context, f) }.getOrNull() }
                    if (tmp == null) { Toast.makeText(context, "Couldn't open ${f.name}", Toast.LENGTH_SHORT).show(); return@launch }
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.vault", tmp)
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, f.mime)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.onFailure { Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show() }
                }
            }
            GlassMenuItem("Save a copy out") { exporting = f; fileMenu = null; saveOut.launch(f.name) }
            GlassMenuItem("Delete for good") { Vault.deleteFile(f); fileMenu = null }
        }
    }
    editing?.let { n -> NoteEditor(n, onDone = { editing = null }) }
    busy?.let { GlassSheet({}) { GlassLabel(it, 18.sp) } }
}

/** Write or edit a note: title and text. Saved on Save; Delete removes it for good. */
@Composable
private fun NoteEditor(note: VaultNote, onDone: () -> Unit) {
    var title by remember { mutableStateOf(note.title) }
    var body by remember { mutableStateOf(note.body) }
    fun save() {
        if (title.isNotBlank() || body.isNotBlank()) Vault.saveNote(note.id.ifEmpty { null }, title, body)
        onDone()
    }
    // Back saves too, like other notes apps.
    androidx.activity.compose.BackHandler { save() }
    Box(Modifier.fillMaxSize().background(Color(0xE6000814)).systemBarsPadding().imePadding().padding(16.dp)) {
        Column(Modifier.fillMaxSize().glass(RoundedCornerShape(24.dp), strong = true).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassLabel(if (note.id.isEmpty()) "New note" else "Note", 20.sp, modifier = Modifier.weight(1f))
                if (note.id.isNotEmpty()) GlassButton("Delete") { Vault.deleteNote(note.id); onDone() }
                GlassButton("Save", selected = true) { save() }
            }
            Box(Modifier.fillMaxWidth().glass(RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
                if (title.isEmpty()) GlassLabel("Title", 18.sp, GlassDim)
                BasicTextField(title, { title = it.take(80) }, singleLine = true,
                    textStyle = TextStyle(color = GlassText, fontSize = 18.sp), cursorBrush = SolidColor(GlassCyan), modifier = Modifier.fillMaxWidth())
            }
            Box(Modifier.fillMaxWidth().weight(1f).glass(RoundedCornerShape(14.dp)).padding(14.dp)) {
                if (body.isEmpty()) GlassLabel("Write anything…", 16.sp, GlassDim)
                BasicTextField(body, { body = it },
                    textStyle = TextStyle(color = GlassText, fontSize = 16.sp), cursorBrush = SolidColor(GlassCyan),
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()))
            }
        }
    }
}

// The glass case in the user's picture (art/originals/vault_bg.png, 2000×1506): its inside, as fractions.
private const val CASE_IMG_W = 2000; private const val CASE_IMG_H = 1506
private const val CASE_L = 0.15f; private const val CASE_R = 0.85f
private const val CASE_T = 0.285f; private const val CASE_B = 0.70f

@Composable
private fun Empty(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { GlassLabel(text, 16.sp, GlassDim) }
}

@Composable
private fun AddNumber(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    GlassSheet(onDismiss) {
        GlassLabel("Add number", 20.sp)
        Field(name, "Name") { name = it }
        Field(number, "Phone number", KeyboardType.Phone) { number = it }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton("Cancel") { onDismiss() }
            GlassButton("Save", selected = true) { if (number.isNotBlank()) onSave(name, number) }
        }
    }
}

@Composable
private fun Field(value: String, hint: String, type: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    Box(Modifier.fillMaxWidth().glass(RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
        if (value.isEmpty()) GlassLabel(hint, color = GlassDim)
        BasicTextField(
            value, onChange, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = type),
            textStyle = TextStyle(color = GlassText, fontSize = 16.sp), cursorBrush = SolidColor(GlassCyan),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
