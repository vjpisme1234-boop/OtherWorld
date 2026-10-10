package com.jp.otherworld.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class VaultNumber(val id: String, val name: String, val number: String)
data class VaultFile(val id: String, val name: String, val mime: String, val size: Long, val added: Long)
data class VaultNote(val id: String, val title: String, val body: String, val edited: Long)
data class VaultState(
    val numbers: List<VaultNumber> = emptyList(),
    val hiddenApps: Set<String> = emptySet(),
    val files: List<VaultFile> = emptyList(),
    val notes: List<VaultNote> = emptyList()
)

/**
 * The hidden drawer's storage: numbers, hidden apps and files, kept apart from the phone's own
 * contacts and files. Everything lives, unencrypted, in the app's private folder (other apps can't
 * read it). Not backed up. Uninstalling Other World or clearing its data erases it.
 */
object Vault {
    private val _state = MutableStateFlow(VaultState())
    val state: StateFlow<VaultState> = _state
    private lateinit var dir: File
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        dir = File(context.filesDir, "vault").apply { mkdirs() }
        File(dir, "files").mkdirs()
        val index = File(dir, "index.json")
        _state.value = if (index.exists()) runCatching { decode(JSONObject(index.readText())) }.getOrDefault(VaultState()) else VaultState()
    }

    private fun update(f: (VaultState) -> VaultState) {
        val next = f(_state.value)
        _state.value = next
        val text = encode(next).toString()
        io.launch {
            synchronized(this@Vault) {
                val tmp = File(dir, "index.json.tmp"); tmp.writeText(text); tmp.renameTo(File(dir, "index.json"))
            }
        }
    }

    // ── Numbers ──
    fun addNumber(name: String, number: String) =
        update { it.copy(numbers = (it.numbers + VaultNumber(UUID.randomUUID().toString(), name.trim(), number.trim())).sortedBy { n -> n.name.lowercase() }) }
    fun removeNumber(id: String) = update { it.copy(numbers = it.numbers.filterNot { n -> n.id == id }) }

    // ── Apps ──
    fun hideApp(pkg: String) = update { it.copy(hiddenApps = it.hiddenApps + pkg) }
    fun unhideApp(pkg: String) = update { it.copy(hiddenApps = it.hiddenApps - pkg) }

    // ── Notes ──
    /** Saves a note (new when `id` is null); newest edited first. */
    fun saveNote(id: String?, title: String, body: String) = update { s ->
        val note = VaultNote(id ?: UUID.randomUUID().toString(), title.trim(), body, System.currentTimeMillis())
        s.copy(notes = (listOf(note) + s.notes.filterNot { it.id == note.id }))
    }
    fun deleteNote(id: String) = update { it.copy(notes = it.notes.filterNot { n -> n.id == id }) }

    // ── Files ──
    /** Copies a picked file into the vault. Returns true if it was stored. */
    fun importFile(context: Context, uri: Uri): Boolean = runCatching {
        val cr = context.contentResolver
        var name = "file"; var size = 0L
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) { name = c.getString(0) ?: name; size = c.getLong(1) }
        }
        val id = UUID.randomUUID().toString()
        cr.openInputStream(uri)!!.use { input -> File(dir, "files/$id").outputStream().use { input.copyTo(it) } }
        val mime = cr.getType(uri) ?: "application/octet-stream"
        update { it.copy(files = it.files + VaultFile(id, name, mime, size, System.currentTimeMillis())) }
        true
    }.getOrDefault(false)

    /** Copies a vault file to a temporary file with its real name (for opening), cleared by [clearTemp]. */
    fun decryptToTemp(context: Context, f: VaultFile): File {
        val out = File(File(context.cacheDir, "vault_open").apply { mkdirs() }, f.name)
        File(dir, "files/${f.id}").copyTo(out, overwrite = true)
        return out
    }

    fun exportTo(context: Context, f: VaultFile, target: Uri) {
        context.contentResolver.openOutputStream(target)!!.use { os -> File(dir, "files/${f.id}").inputStream().use { it.copyTo(os) } }
    }

    fun deleteFile(f: VaultFile) {
        File(dir, "files/${f.id}").delete()
        update { it.copy(files = it.files.filterNot { x -> x.id == f.id }) }
    }

    fun clearTemp(context: Context) { File(context.cacheDir, "vault_open").deleteRecursively() }

    // ── Putting everything back (before Other World's data is cleared) ──
    /**
     * Gives the hidden drawer's contents back to the phone: numbers go into the phone's Contacts
     * (needs WRITE_CONTACTS), files into the phone's shared folders (pictures → Pictures/Other World,
     * videos → Movies/Other World, audio → Music/Other World, everything else → Download/Other World).
     * Hidden apps need nothing: they never left the phone. Returns what failed (empty = all back).
     */
    fun restoreToPhone(context: Context): List<String> {
        val failed = ArrayList<String>()
        val cr = context.contentResolver
        for (n in _state.value.numbers) {
            val ok = runCatching {
                val ops = arrayListOf(
                    android.content.ContentProviderOperation.newInsert(android.provider.ContactsContract.RawContacts.CONTENT_URI)
                        .withValue(android.provider.ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                        .withValue(android.provider.ContactsContract.RawContacts.ACCOUNT_NAME, null).build(),
                    android.content.ContentProviderOperation.newInsert(android.provider.ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(android.provider.ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(android.provider.ContactsContract.Data.MIMETYPE, android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                        .withValue(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER, n.number)
                        .withValue(android.provider.ContactsContract.CommonDataKinds.Phone.TYPE, android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE).build()
                )
                if (n.name.isNotBlank()) ops += android.content.ContentProviderOperation.newInsert(android.provider.ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(android.provider.ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(android.provider.ContactsContract.Data.MIMETYPE, android.provider.ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(android.provider.ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, n.name).build()
                cr.applyBatch(android.provider.ContactsContract.AUTHORITY, ops)
            }.isSuccess
            if (!ok) failed += "Number: ${n.name.ifBlank { n.number }}"
        }
        for (n in _state.value.notes) {
            val ok = runCatching {
                val name = (n.title.ifBlank { "Note" }.replace(Regex("[\\\\/:*?\"<>|]"), "_")) + ".txt"
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Download/Other World")
                }
                val uri = cr.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
                cr.openOutputStream(uri)!!.use { it.write((if (n.title.isBlank()) n.body else n.title + "\n\n" + n.body).toByteArray()) }
            }.isSuccess
            if (!ok) failed += "Note: ${n.title.ifBlank { "Note" }}"
        }
        for (f in _state.value.files) {
            val ok = runCatching {
                val (collection, folder) = when {
                    f.mime.startsWith("image/") -> android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI to "Pictures/Other World"
                    f.mime.startsWith("video/") -> android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI to "Movies/Other World"
                    f.mime.startsWith("audio/") -> android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to "Music/Other World"
                    else -> android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI to "Download/Other World"
                }
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, f.name)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, f.mime)
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, folder)
                }
                val uri = cr.insert(collection, values)!!
                cr.openOutputStream(uri)!!.use { os -> File(dir, "files/${f.id}").inputStream().use { it.copyTo(os) } }
            }.isSuccess
            if (!ok) failed += "File: ${f.name}"
        }
        return failed
    }

    private fun encode(s: VaultState) = JSONObject().apply {
        put("numbers", JSONArray(s.numbers.map { JSONObject().put("id", it.id).put("name", it.name).put("number", it.number) }))
        put("hiddenApps", JSONArray(s.hiddenApps.toList()))
        put("notes", JSONArray(s.notes.map {
            JSONObject().put("id", it.id).put("title", it.title).put("body", it.body).put("edited", it.edited)
        }))
        put("files", JSONArray(s.files.map {
            JSONObject().put("id", it.id).put("name", it.name).put("mime", it.mime).put("size", it.size).put("added", it.added)
        }))
    }

    private fun decode(o: JSONObject) = VaultState(
        numbers = o.optJSONArray("numbers")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).let { n -> VaultNumber(n.getString("id"), n.getString("name"), n.getString("number")) } } }.orEmpty(),
        hiddenApps = o.optJSONArray("hiddenApps")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }.orEmpty(),
        notes = o.optJSONArray("notes")?.let { a ->
            (0 until a.length()).map { a.getJSONObject(it).let { n -> VaultNote(n.getString("id"), n.optString("title"), n.optString("body"), n.optLong("edited")) } }
        }.orEmpty(),
        files = o.optJSONArray("files")?.let { a ->
            (0 until a.length()).map { a.getJSONObject(it).let { f -> VaultFile(f.getString("id"), f.getString("name"), f.getString("mime"), f.getLong("size"), f.getLong("added")) } }
        }.orEmpty()
    )
}
