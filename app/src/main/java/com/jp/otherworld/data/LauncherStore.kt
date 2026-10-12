package com.jp.otherworld.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class IconStyle { CUSTOM_GLASS, ORIGINAL_IN_GLASS }
enum class IconSize(val dp: Int) { S(46), M(56), L(66) }

const val GRID_COLS = 6
const val GRID_ROWS = 6
const val DOCK_MIN = 4
const val DOCK_MAX = 8
/** A per-app glow colour meaning "no glow for this app". */
const val GLOW_OFF = 0

sealed class HomeItem {
    abstract val col: Int
    abstract val row: Int
    abstract val spanX: Int
    abstract val spanY: Int

    data class App(val pkg: String, override val col: Int, override val row: Int) : HomeItem() {
        override val spanX get() = 1
        override val spanY get() = 1
    }

    data class Widget(
        val id: Int, val provider: String,
        override val col: Int, override val row: Int, override val spanX: Int, override val spanY: Int
    ) : HomeItem()

    /** A glass folder of apps on a home page. */
    data class Folder(
        val id: String, val name: String, val apps: List<String>,
        override val col: Int, override val row: Int
    ) : HomeItem() {
        override val spanX get() = 1
        override val spanY get() = 1
    }

    /** One of Other World's own glass widgets (clock, weather, battery). `id` is negative, never an Android widget id. */
    data class Glass(
        val id: Int, val kind: String,
        override val col: Int, override val row: Int, override val spanX: Int, override val spanY: Int
    ) : HomeItem()

    /** The same item at another cell. */
    fun at(c: Int, r: Int): HomeItem = when (this) {
        is App -> copy(col = c, row = r)
        is Folder -> copy(col = c, row = r)
        is Widget -> copy(col = c, row = r)
        is Glass -> copy(col = c, row = r)
    }

    /** The same widget at another size (apps and folders stay 1×1). */
    fun sized(sx: Int, sy: Int): HomeItem = when (this) {
        is Widget -> copy(spanX = sx, spanY = sy)
        is Glass -> copy(spanX = sx, spanY = sy)
        else -> this
    }

    fun covers(c: Int, r: Int) = c in col until col + spanX && r in row until row + spanY
}

data class Page(val items: List<HomeItem> = emptyList())

data class LauncherState(
    val pages: List<Page> = listOf(Page()),
    val dock: List<String?> = List(5) { null },
    val iconStyle: IconStyle = IconStyle.ORIGINAL_IN_GLASS,
    val iconSize: IconSize = IconSize.M,
    val showLabels: Boolean = true,
    val defaultPage: Int = 0,
    /** The default wallpaper (green alien; the globe before 2026-10-10) has been offered, first time we became home. */
    val globeOffered: Boolean = false,
    /** The hidden-drawer instructions in Home settings were deleted by the user (never shown again). */
    val secretHintDeleted: Boolean = false,
    // ── Alien wallpaper ──
    /** New notifications make the alien's halo pulse in a colour. */
    val notifGlow: Boolean = true,
    /** Test: the alien's lips move with whatever sound the phone plays (needs the microphone permission). */
    val alienTalks: Boolean = false,
    /** Per-app glow colour (ARGB); [GLOW_OFF] = never glow for that app. Apps not listed use their type's colour. */
    val glowColors: Map<String, Int> = emptyMap(),
    /** Night mode: alien dims, eyes half shut. Times are minutes after midnight. */
    val nightOn: Boolean = true,
    val nightStart: Int = 22 * 60,
    val nightEnd: Int = 7 * 60,
    /** How the hidden drawer opens: VIDEO (smoke → glass case, the user's pick), or the alien breaking apart: DUST, SHARDS, SPARKS. */
    val vaultEffect: String = "VIDEO_SMOKE",
    // ── Home ──
    /** Glowing dots on icons of apps with unread notifications. */
    val showDots: Boolean = true,
    /** How far the dock and the page dots have been moved up from the bottom (dp). */
    val dockLift: Float = 0f,
    val dotsLift: Float = 0f,
    /** Gestures on empty home. */
    val doubleTapLock: Boolean = true,
    val swipeDownNotifications: Boolean = true,
    val twoFingerQuickSettings: Boolean = true,
    // ── Wheel ──
    /** Apps the user moved to another ring (package → ring name). Others go by their category. */
    val ringOf: Map<String, String> = emptyMap(),
    /** Rings the user made. */
    val customRings: List<String> = emptyList(),
    /** Last apps opened from Other World, newest first (max 5). */
    val recent: List<String> = emptyList(),
    /** Glass sounds in the wheel (silent anyway when the phone is on vibrate/silent). */
    val sounds: Boolean = true,
    /** Which take (1–3) the user picked for each sound: tick, open, ring, drawer. */
    val soundPick: Map<String, Int> = emptyMap(),
    /** Sounds the user switched off one by one. */
    val soundOff: Set<String> = emptySet(),
    /** Each sound's own volume, 0..1 (missing = its default). */
    val soundVolume: Map<String, Float> = emptyMap(),
    /** Middle of the wheel: MORPH (Earth → smoke → Mars each time it opens), EARTH, MARS or NONE. */
    val wheelCenter: String = "MORPH"
) {
    val dockSize get() = dock.size

    /** First free cell on a page for a spanX×spanY item, or null if it doesn't fit. */
    fun freeCell(page: Int, spanX: Int = 1, spanY: Int = 1, skip: HomeItem? = null): Pair<Int, Int>? {
        val items = pages.getOrNull(page)?.items?.filter { it != skip } ?: return null
        for (r in 0..GRID_ROWS - spanY) for (c in 0..GRID_COLS - spanX) {
            val clash = items.any { it.col < c + spanX && c < it.col + it.spanX && it.row < r + spanY && r < it.row + it.spanY }
            if (!clash) return c to r
        }
        return null
    }

    fun fits(page: Int, item: HomeItem, col: Int, row: Int): Boolean {
        if (col < 0 || row < 0 || col + item.spanX > GRID_COLS || row + item.spanY > GRID_ROWS) return false
        val items = pages.getOrNull(page)?.items?.filter { it != item } ?: return false
        return items.none { it.col < col + item.spanX && col < it.col + it.spanX && it.row < row + item.spanY && row < it.row + it.spanY }
    }

    fun withPage(i: Int, f: (List<HomeItem>) -> List<HomeItem>) =
        copy(pages = pages.mapIndexed { k, p -> if (k == i) Page(f(p.items)) else p })

    /** Put an app on `page` (or the first page with room, adding one if all are full). */
    fun addApp(pkg: String, page: Int): LauncherState {
        val order = listOf(page) + pages.indices.filter { it != page }
        for (p in order) freeCell(p)?.let { (c, r) -> return withPage(p) { it + HomeItem.App(pkg, c, r) } }
        return copy(pages = pages + Page(listOf(HomeItem.App(pkg, 0, 0))))
    }

    /** The item at a cell on a page, if any. */
    fun itemAt(page: Int, col: Int, row: Int): HomeItem? = pages.getOrNull(page)?.items?.firstOrNull { it.covers(col, row) }

    fun withoutPackage(pkg: String) = copy(
        pages = pages.map { p ->
            Page(p.items.mapNotNull {
                when {
                    it is HomeItem.App && it.pkg == pkg -> null
                    it is HomeItem.Folder && pkg in it.apps -> it.copy(apps = it.apps - pkg).takeIf { f -> f.apps.isNotEmpty() }
                    else -> it
                }
            })
        },
        dock = dock.map { if (it == pkg) null else it }
    )
}

/** The launcher's layout and settings, saved as one JSON file. */
object LauncherStore {
    private val _state = MutableStateFlow(LauncherState())
    val state: StateFlow<LauncherState> = _state
    private lateinit var file: File
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()
    private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        file = File(context.filesDir, "launcher.json")
        _state.value = if (file.exists()) runCatching { decode(JSONObject(file.readText())) }.getOrElse { firstRun(context) }
        else firstRun(context).also { save(it) }
    }

    fun update(f: (LauncherState) -> LauncherState) {
        val next = f(_state.value)
        if (next == _state.value) return
        _state.value = next
        save(next)
    }

    /** Remember an app as just opened (for the wheel's Recent panel). */
    fun opened(pkg: String) { if (loaded) update { it.copy(recent = (listOf(pkg) + (it.recent - pkg)).take(5)) } }

    private fun save(s: LauncherState) {
        val text = encode(s).toString()
        scope.launch { writeLock.withLock { file.writeText(text) } }
    }

    // First run: a phone / messages / browser / camera / gallery dock, glass icons if you have them.
    private fun firstRun(context: Context): LauncherState {
        val pm = context.packageManager
        fun first(vararg pkgs: String) = pkgs.firstOrNull { pm.getLaunchIntentForPackage(it) != null }
        val dock = listOf(
            first("com.samsung.android.dialer", "com.google.android.dialer", "com.android.dialer"),
            first("com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms"),
            first("com.android.chrome", "com.sec.android.app.sbrowser", "org.mozilla.firefox"),
            first("com.sec.android.app.camera", "com.google.android.GoogleCamera", "com.android.camera2"),
            first("com.sec.android.gallery3d", "com.google.android.apps.photos")
        )
        return LauncherState(
            dock = dock,
            iconStyle = if (hasCustomIcons(context)) IconStyle.CUSTOM_GLASS else IconStyle.ORIGINAL_IN_GLASS
        )
    }

    private fun encode(s: LauncherState) = JSONObject().apply {
        put("pages", JSONArray(s.pages.map { p ->
            JSONArray(p.items.map { item ->
                when (item) {
                    is HomeItem.App -> JSONObject().put("type", "app").put("pkg", item.pkg).put("col", item.col).put("row", item.row)
                    is HomeItem.Glass -> JSONObject().put("type", "glass").put("id", item.id).put("kind", item.kind)
                        .put("col", item.col).put("row", item.row).put("spanX", item.spanX).put("spanY", item.spanY)
                    is HomeItem.Folder -> JSONObject().put("type", "folder").put("id", item.id).put("name", item.name)
                        .put("apps", JSONArray(item.apps)).put("col", item.col).put("row", item.row)
                    is HomeItem.Widget -> JSONObject().put("type", "widget").put("id", item.id).put("provider", item.provider)
                        .put("col", item.col).put("row", item.row).put("spanX", item.spanX).put("spanY", item.spanY)
                }
            })
        }))
        put("dock", JSONArray(s.dock.map { it ?: JSONObject.NULL }))
        put("iconStyle", s.iconStyle.name)
        put("iconSize", s.iconSize.name)
        put("showLabels", s.showLabels)
        put("defaultPage", s.defaultPage)
        put("globeOffered", s.globeOffered)
        put("secretHintDeleted", s.secretHintDeleted)
        put("notifGlow", s.notifGlow)
        put("alienTalks", s.alienTalks)
        put("glowColors", JSONObject().apply { s.glowColors.forEach { (k, v) -> put(k, v) } })
        put("nightOn", s.nightOn)
        put("nightStart", s.nightStart)
        put("nightEnd", s.nightEnd)
        put("vaultEffect", s.vaultEffect)
        put("showDots", s.showDots)
        put("dockLift", s.dockLift.toDouble())
        put("dotsLift", s.dotsLift.toDouble())
        put("doubleTapLock", s.doubleTapLock)
        put("swipeDownNotifications", s.swipeDownNotifications)
        put("twoFingerQuickSettings", s.twoFingerQuickSettings)
        put("ringOf", JSONObject().apply { s.ringOf.forEach { (k, v) -> put(k, v) } })
        put("customRings", JSONArray(s.customRings))
        put("recent", JSONArray(s.recent))
        put("sounds", s.sounds)
        put("soundPick", JSONObject().apply { s.soundPick.forEach { (k, v) -> put(k, v) } })
        put("soundOff", JSONArray(s.soundOff.toList()))
        put("soundVolume", JSONObject().apply { s.soundVolume.forEach { (k, v) -> put(k, v.toDouble()) } })
        put("wheelCenter", s.wheelCenter)
    }

    private fun decode(o: JSONObject): LauncherState {
        val pages = o.getJSONArray("pages").let { arr ->
            (0 until arr.length()).map { i ->
                val items = arr.getJSONArray(i)
                Page((0 until items.length()).map { k ->
                    val it = items.getJSONObject(k)
                    when (it.getString("type")) {
                        "app" -> HomeItem.App(it.getString("pkg"), it.getInt("col"), it.getInt("row"))
                        "glass" -> HomeItem.Glass(it.getInt("id"), it.getString("kind"), it.getInt("col"), it.getInt("row"), it.getInt("spanX"), it.getInt("spanY"))
                        "folder" -> HomeItem.Folder(
                            it.getString("id"), it.optString("name", "Folder"),
                            it.getJSONArray("apps").let { a -> (0 until a.length()).map { k -> a.getString(k) } },
                            it.getInt("col"), it.getInt("row")
                        )
                        else -> HomeItem.Widget(it.getInt("id"), it.getString("provider"), it.getInt("col"), it.getInt("row"), it.getInt("spanX"), it.getInt("spanY"))
                    }
                })
            }
        }.ifEmpty { listOf(Page()) }
        val dock = o.getJSONArray("dock").let { arr -> (0 until arr.length()).map { if (arr.isNull(it)) null else arr.getString(it) } }
        return LauncherState(
            pages = pages,
            dock = dock,
            iconStyle = IconStyle.valueOf(o.optString("iconStyle", IconStyle.ORIGINAL_IN_GLASS.name)),
            iconSize = IconSize.valueOf(o.optString("iconSize", IconSize.M.name)),
            showLabels = o.optBoolean("showLabels", true),
            defaultPage = o.optInt("defaultPage", 0).coerceIn(0, pages.size - 1),
            globeOffered = o.optBoolean("globeOffered", false),
            secretHintDeleted = o.optBoolean("secretHintDeleted", false),
            notifGlow = o.optBoolean("notifGlow", true),
            alienTalks = o.optBoolean("alienTalks", false),
            glowColors = o.optJSONObject("glowColors")?.let { g -> g.keys().asSequence().associateWith { g.getInt(it) } }.orEmpty(),
            nightOn = o.optBoolean("nightOn", true),
            nightStart = o.optInt("nightStart", 22 * 60),
            nightEnd = o.optInt("nightEnd", 7 * 60),
            vaultEffect = o.optString("vaultEffect", "VIDEO_SMOKE"),
            showDots = o.optBoolean("showDots", true),
            dockLift = o.optDouble("dockLift", 0.0).toFloat(),
            dotsLift = o.optDouble("dotsLift", 0.0).toFloat(),
            doubleTapLock = o.optBoolean("doubleTapLock", true),
            swipeDownNotifications = o.optBoolean("swipeDownNotifications", true),
            twoFingerQuickSettings = o.optBoolean("twoFingerQuickSettings", true),
            ringOf = o.optJSONObject("ringOf")?.let { g -> g.keys().asSequence().associateWith { g.getString(it) } }.orEmpty(),
            customRings = o.optJSONArray("customRings")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(),
            recent = o.optJSONArray("recent")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(),
            sounds = o.optBoolean("sounds", true),
            soundPick = o.optJSONObject("soundPick")?.let { g -> g.keys().asSequence().associateWith { g.getInt(it) } }.orEmpty(),
            soundOff = o.optJSONArray("soundOff")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }.orEmpty(),
            soundVolume = o.optJSONObject("soundVolume")?.let { g -> g.keys().asSequence().associateWith { g.getDouble(it).toFloat() } }.orEmpty(),
            wheelCenter = o.optString("wheelCenter", "MORPH")
        )
    }
}
