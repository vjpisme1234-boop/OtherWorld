package com.jp.otherworld

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.jp.otherworld.data.*
import com.jp.otherworld.ui.home.*
import com.jp.otherworld.ui.common.AlphabetBar
import com.jp.otherworld.ui.vault.VaultIntro
import com.jp.otherworld.ui.common.GlassLabel
import com.jp.otherworld.ui.common.GlassDim
import com.jp.otherworld.ui.common.GlassCyan
import com.jp.otherworld.ui.common.GlassSheet
import com.jp.otherworld.ui.common.GlassMenuItem
import com.jp.otherworld.ui.common.glass
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.unit.sp
import com.jp.otherworld.ui.common.letterIndex
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import com.jp.otherworld.ui.panel.AppChooser
import com.jp.otherworld.ui.panel.AppDrawer
import com.jp.otherworld.ui.panel.SettingsPage
import com.jp.otherworld.ui.wheel.GlassWheel
import com.jp.otherworld.ui.wheel.Panel
import com.jp.otherworld.ui.wheel.WheelArt
import com.jp.otherworld.ui.wheel.loadWheelArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
class HomeActivity : ComponentActivity() {
    // Bumped on every Home press while we're the launcher, so the UI can reset.
    private var homePresses by mutableIntStateOf(0)
    // Checked each time home comes back: Samsung sometimes hands Home to another launcher.
    private var isDefaultHome by mutableStateOf(true)

    // A widget being added: its id, provider, and the page it goes on.
    private var pendingWidget: Triple<Int, AppWidgetProviderInfo, Int>? = null
    private val bindWidget = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val p = pendingWidget ?: return@registerForActivityResult
        if (r.resultCode == RESULT_OK) configureOrPlaceWidget() else { WidgetHost.delete(p.first); pendingWidget = null }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LauncherStore.init(this)
        AppRepository.start(this)
        WidgetHost.init(this)
        Sounds.init(this)
        Vault.init(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val bars = WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            val context = LocalContext.current
            val density = LocalDensity.current
            val state by LauncherStore.state.collectAsState()
            val allApps by AppRepository.apps.collectAsState()
            val vault by Vault.state.collectAsState()
            // Apps put in the hidden drawer disappear from home, dock, wheel and All apps.
            val appList = remember(allApps, vault.hiddenApps) { allApps.filter { it.packageName !in vault.hiddenApps } }
            val apps = remember(appList) { appList.associateBy { it.packageName } }
            var vaultOpen by remember { mutableStateOf(false) }
            val art by produceState<WheelArt?>(null) { value = withContext(Dispatchers.IO) { loadWheelArt(context) } }

            val pager = rememberPagerState(initialPage = state.defaultPage) { state.pages.size }

            var drawerOpen by remember { mutableStateOf(false) }
            var allAppsOpen by remember { mutableStateOf(false) }
            var settingsOpen by remember { mutableStateOf(false) }
            var editPagesOpen by remember { mutableStateOf(false) }
            var widgetsOpen by remember { mutableStateOf(false) }
            var wallpaperOpen by remember { mutableStateOf(false) }
            var homeMenuOpen by remember { mutableStateOf(false) }
            var chooserSlot by remember { mutableStateOf<Int?>(null) }
            var appMenu by remember { mutableStateOf<AppMenuTarget?>(null) }
            // Open folder (and its page), the folder's hold menu, and renaming.
            var openFolder by remember { mutableStateOf<Pair<String, Int>?>(null) }
            var folderMenu by remember { mutableStateOf<Pair<String, Int>?>(null) }
            var renaming by remember { mutableStateOf<Pair<String, Int>?>(null) }
            fun folderOf(ref: Pair<String, Int>?) = ref?.let { (id, page) ->
                state.pages.getOrNull(page)?.items?.firstOrNull { it is HomeItem.Folder && it.id == id } as? HomeItem.Folder
            }
            var editingWidget by remember { mutableStateOf<Int?>(null) }
            var wheelJump by remember { mutableStateOf<Pair<Int, Long>?>(null) }
            // Typed in the wheel's search field; while set, the wheel shows only matching apps (from every ring).
            var wheelQuery by remember { mutableStateOf("") }
            var ringIndex by remember { mutableIntStateOf(0) }
            var ringListOpen by remember { mutableStateOf(false) }
            var recentOpen by remember { mutableStateOf(false) }
            var ringPicker by remember { mutableStateOf<AppInfo?>(null) }
            val letters = remember(appList) { letterIndex(appList) }

            var introPlaying by remember { mutableStateOf(false) }
            var introPreview by remember { mutableStateOf(false) }
            var outroPlaying by remember { mutableStateOf(false) }
            var previewStyle by remember { mutableStateOf("VIDEO") }
            fun closeAll() {
                drawerOpen = false; allAppsOpen = false; settingsOpen = false; editPagesOpen = false
                widgetsOpen = false; wallpaperOpen = false; homeMenuOpen = false; chooserSlot = null
                appMenu = null; editingWidget = null; vaultOpen = false
                openFolder = null; folderMenu = null; renaming = null
                introPlaying = false; introPreview = false; outroPlaying = false
                ringListOpen = false; recentOpen = false; ringPicker = null
            }
            // Opening the hidden drawer: the alien breaks apart first (if it's the wallpaper), then the drawer fades in.
            val scope = rememberCoroutineScope()
            // VIDEO: the smoke → glass case film plays, then the drawer fades in over its last frame.
            fun openVault() {
                if (state.vaultEffect.startsWith("VIDEO")) { introPlaying = true; return }
                if (AlienSignals.alienVisible) {
                    AlienSignals.vanish(state.vaultEffect)
                    scope.launch { delay(1300); vaultOpen = true }
                } else vaultOpen = true
            }
            // Closing runs the opening backwards: the reversed film (cube → smoke → alien), or the face reforming.
            fun closeVault() {
                if (state.vaultEffect.startsWith("VIDEO")) outroPlaying = true else vaultOpen = false
            }
            var vaultWasOpen by remember { mutableStateOf(false) }
            LaunchedEffect(vaultOpen) {
                if (vaultOpen) vaultWasOpen = true else if (vaultWasOpen) { vaultWasOpen = false; AlienSignals.reform() }
            }
            // Home pressed while already home: close everything, go to the default page.
            LaunchedEffect(homePresses) {
                if (homePresses > 0) {
                    val wasClear = !drawerOpen && !allAppsOpen && !settingsOpen && !editPagesOpen && !widgetsOpen &&
                        !wallpaperOpen && !homeMenuOpen && chooserSlot == null && appMenu == null
                    closeAll()
                    if (wasClear) pager.animateScrollToPage(state.defaultPage)
                }
            }
            // Status bar on home; the wheel drawer is full screen.
            LaunchedEffect(drawerOpen) {
                if (!drawerOpen) wheelQuery = ""
                if (drawerOpen) bars.hide(WindowInsetsCompat.Type.systemBars()) else bars.show(WindowInsetsCompat.Type.systemBars())
            }

            // Back closes the top-most layer.
            BackHandler(vaultOpen || outroPlaying) { if (!outroPlaying) closeVault() }
            BackHandler(introPlaying || introPreview) { introPlaying = false; introPreview = false }
            BackHandler(appMenu != null) { appMenu = null }
            BackHandler(ringPicker != null) { ringPicker = null }
            BackHandler(ringListOpen) { ringListOpen = false }
            BackHandler(recentOpen && appMenu == null) { recentOpen = false }
            BackHandler(renaming != null) { renaming = null }
            BackHandler(folderMenu != null) { folderMenu = null }
            BackHandler(openFolder != null && appMenu == null) { openFolder = null }
            BackHandler(homeMenuOpen) { homeMenuOpen = false }
            BackHandler(chooserSlot != null) { chooserSlot = null }
            BackHandler(widgetsOpen) { widgetsOpen = false }
            BackHandler(editPagesOpen) { editPagesOpen = false }
            BackHandler(settingsOpen) { settingsOpen = false }
            BackHandler(allAppsOpen) { allAppsOpen = false }
            BackHandler(drawerOpen && !allAppsOpen) { if (wheelQuery.isNotEmpty()) wheelQuery = "" else drawerOpen = false }
            BackHandler(editingWidget != null) { editingWidget = null }

            // Leaving Other World always shuts the hidden drawer.
            val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
            DisposableEffect(lifecycle) {
                val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_STOP) vaultOpen = false }
                lifecycle.addObserver(obs)
                onDispose { lifecycle.removeObserver(obs) }
            }

            var eyesHeld by remember { mutableStateOf(false) }
            val alienEyes by produceState<AlienEyes?>(null, homePresses) {
                value = withContext(Dispatchers.Default) {
                    val (w, h) = com.jp.otherworld.wallpaper.alienSize(context)
                    AlienEyes(com.jp.otherworld.wallpaper.alienEyeSpots(context), w, h)
                }
            }
            // Room around each eye for the face's float, tilt (~6% of the width) and lean (~3%) plus a fingertip.
            val eyeSlack = with(density) { 24.dp.toPx() } + resources.displayMetrics.widthPixels * 0.09f
            Box(Modifier.fillMaxSize().leanAlien().secretEyes(alienEyes.takeIf { !vaultOpen }, eyeSlack, onHolding = { eyesHeld = it }) { closeAll(); openVault() }) {
                if (GlassIconsReady(appList)) HomeScreen(
                    state, apps, pager, editingWidget,
                    onEditWidget = { editingWidget = it },
                    onOpenDrawer = { drawerOpen = true },
                    onHomeLongPress = { homeMenuOpen = true },
                    onAppMenu = { appMenu = it },
                    onEmptyDockSlot = { chooserSlot = it },
                    onOpenFolder = { f, page -> openFolder = f.id to page },
                    onFolderMenu = { f, page -> folderMenu = f.id to page },
                    onSwipeDown = { if (state.swipeDownNotifications) com.jp.otherworld.service.Gestures.notifications(context) },
                    onTwoFingerDown = { if (state.twoFingerQuickSettings) com.jp.otherworld.service.Gestures.quickSettings(context) }
                )

                // The glass wheel: the app drawer.
                AnimatedVisibility(
                    drawerOpen,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    val wheelArt = art
                    val unread by NotificationHub.counts.collectAsState()
                    // Rings: All, then each category (or user ring) that has apps.
                    val rings = remember(appList, state.ringOf, state.customRings) { state.rings(appList) }
                    val ringName = rings.getOrElse(ringIndex.coerceIn(0, rings.size - 1)) { ALL_RING }
                    val ringApps = remember(appList, ringName, state.ringOf) { state.appsIn(ringName, appList) }
                    val recentApps = remember(apps, state.recent) { state.recent.mapNotNull { apps[it] } }
                    val searching = wheelQuery.isNotBlank()
                    val found = remember(appList, wheelQuery) { com.jp.otherworld.ui.wheel.searchApps(appList, wheelQuery) }
                    val panels = remember(ringApps, recentApps, state.iconStyle, unread, state.showDots, searching, found) {
                        if (searching && found.isNotEmpty()) found.map { Panel.App(it, it.iconFor(state.iconStyle), state.showDots && (unread[it.packageName] ?: 0) > 0) }
                        else listOf<Panel>(Panel.Drawer(appList.take(9).map { it.iconFor(state.iconStyle) })) +
                            (if (recentApps.isNotEmpty()) listOf(Panel.Recent(recentApps.map { it.iconFor(state.iconStyle) })) else emptyList()) +
                            ringApps.map { Panel.App(it, it.iconFor(state.iconStyle), state.showDots && (unread[it.packageName] ?: 0) > 0) }
                    }
                    val firstApp = panels.size - ringApps.size
                    // Each new letter typed: spin to the best match.
                    LaunchedEffect(found) { if (found.isNotEmpty()) wheelJump = 0 to System.nanoTime() }
                    val ringLetters = remember(ringApps) { letterIndex(ringApps) }
                    LaunchedEffect(Unit) { Sounds.play("drawer") }
                    fun switchRing(by: Int) {
                        if (rings.size > 1) ringIndex = ((ringIndex + by) % rings.size + rings.size) % rings.size
                    }
                    Box(
                        Modifier.fillMaxSize().background(Color.Black).pointerInput(rings.size) {
                            // Up/down on the wheel switches rings; down from the top strip closes the drawer.
                            var dy = 0f; var fromTop = false
                            detectVerticalDragGestures(
                                onDragStart = { o -> dy = 0f; fromTop = o.y < size.height * 0.14f },
                                onDragEnd = {
                                    val far = with(density) { 80.dp.toPx() }
                                    when {
                                        fromTop && dy > far -> drawerOpen = false
                                        dy < -far -> switchRing(1)
                                        dy > far -> switchRing(-1)
                                    }
                                }
                            ) { c, d -> dy += d; c.consume() }
                        }
                    ) {
                        if (wheelArt != null) GlassWheel(
                            wheelArt, panels,
                            active = drawerOpen && !allAppsOpen,
                            jumpTo = wheelJump,
                            ringKey = if (searching) "search" else ringName,
                            center = state.wheelCenter,
                            onLongPress = { p -> (p as? Panel.App)?.let { appMenu = AppMenuTarget(it.app, AppSource.Drawer) } }
                        ) { panel ->
                            when (panel) {
                                is Panel.App -> { launchApp(context, panel.app.packageName); drawerOpen = false }
                                is Panel.Drawer -> allAppsOpen = true
                                is Panel.Recent -> recentOpen = true
                            }
                        }
                        // The ring's name at the top; tap for the list of rings.
                        Row(
                            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 18.dp)
                                .glass(androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                                .clickable { ringListOpen = true }.padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GlassLabel("‹  ", 16.sp, GlassDim)
                            GlassLabel(ringName, 17.sp, GlassCyan)
                            GlassLabel("  ›", 16.sp, GlassDim)
                        }
                        val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                        // Search: type an app's name; Search on the keyboard opens the best match.
                        com.jp.otherworld.ui.wheel.WheelSearch(
                            wheelQuery, { wheelQuery = it },
                            onGo = { found.firstOrNull()?.let { launchApp(context, it.packageName); drawerOpen = false } },
                            // Landscape: top-left corner, beside the ring name, so it doesn't cross the panels.
                            modifier = if (landscape) Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 24.dp, top = 18.dp).fillMaxWidth(0.3f)
                                else Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 76.dp).fillMaxWidth(0.84f)
                        )
                        if (searching && found.isEmpty()) GlassLabel("No apps match “${wheelQuery.trim()}”", 15.sp, GlassDim,
                            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 136.dp))
                        // A–Z: slide to spin the wheel to that letter (within this ring).
                        if (!searching) AlphabetBar(
                            ringLetters.keys,
                            onLetter = { l -> ringLetters[l]?.let { wheelJump = (it + firstApp) to System.nanoTime() } },
                            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 18.dp).fillMaxWidth()
                        )
                        if (ringListOpen) GlassSheet({ ringListOpen = false }) {
                            GlassLabel("Rings", 20.sp)
                            rings.forEachIndexed { k, r ->
                                GlassMenuItem(if (k == ringIndex) "$r  ✓" else r) { ringIndex = k; ringListOpen = false }
                            }
                            GlassLabel("Swipe up or down on the wheel to switch. Hold an app to move it to another ring.", 13.sp, GlassDim)
                        }
                        if (recentOpen) MiniWheelSheet(
                            "Recent", recentApps, state.iconStyle,
                            onDismiss = { recentOpen = false; drawerOpen = false },
                            onAppMenu = { app -> appMenu = AppMenuTarget(app, AppSource.Drawer) }
                        )
                    }
                }
                AnimatedVisibility(allAppsOpen, enter = fadeIn(), exit = fadeOut()) {
                    AppDrawer(
                        appList, state.iconStyle,
                        onLaunch = { app -> launchApp(context, app.packageName); allAppsOpen = false; drawerOpen = false },
                        onMenu = { app -> appMenu = AppMenuTarget(app, AppSource.Drawer) }
                    )
                }
                AnimatedVisibility(settingsOpen, enter = fadeIn(), exit = fadeOut()) {
                    SettingsPage(state, apps, onChooseDockApp = { chooserSlot = it }, onPreviewEffect = { style ->
                        // Show the break-apart on the alien behind home, then bring the face back and return here.
                        settingsOpen = false
                        if (style.startsWith("VIDEO")) { previewStyle = style; introPreview = true; return@SettingsPage }
                        scope.launch {
                            delay(350)
                            AlienSignals.vanish(style)
                            delay(AlienSignals.VANISH_MS + 500)
                            AlienSignals.reform()
                            delay(AlienSignals.REFORM_MS + 300)
                            settingsOpen = true
                        }
                    })
                }
                AnimatedVisibility(editPagesOpen, enter = fadeIn(), exit = fadeOut()) {
                    EditPages(state, apps) { editPagesOpen = false }
                }
                AnimatedVisibility(widgetsOpen, enter = fadeIn(), exit = fadeOut()) {
                    WidgetPicker(onPickGlass = { kind ->
                        widgetsOpen = false
                        val (sx, sy) = GLASS_WIDGETS.first { it.first == kind }.third
                        val page = pager.currentPage
                        LauncherStore.update { st ->
                            val id = -(1..Int.MAX_VALUE - 1).random()
                            val order = listOf(page) + st.pages.indices.filter { it != page }
                            for (p in order) st.freeCell(p, sx, sy)?.let { (c, r) -> return@update st.withPage(p) { it + HomeItem.Glass(id, kind, c, r, sx, sy) } }
                            st.copy(pages = st.pages + Page(listOf(HomeItem.Glass(id, kind, 0, 0, sx, sy))))
                        }
                    }) { info ->
                        widgetsOpen = false
                        addWidget(info, pager.currentPage)
                    }
                }
                chooserSlot?.let { slot ->
                    AppChooser("Dock slot ${slot + 1}", appList, state.iconStyle) { app ->
                        LauncherStore.update { s ->
                            s.copy(dock = s.dock.mapIndexed { i, p -> if (i == slot) app.packageName else if (p == app.packageName) null else p })
                        }
                        chooserSlot = null
                    }
                }
                if (wallpaperOpen) WallpaperPicker { wallpaperOpen = false }
                // After a missed call, the first time home is opened: the alien whispers "you have a phone call".
                var missedPlaying by remember { mutableStateOf(false) }
                val missedLife = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
                DisposableEffect(missedLife) {
                    com.jp.otherworld.data.MissedCall.init(context)
                    val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
                        if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME && com.jp.otherworld.data.MissedCall.pending.value) {
                            val alienSet = runCatching { android.app.WallpaperManager.getInstance(context).wallpaperInfo?.serviceName?.startsWith("com.jp.otherworld.wallpaper.Alien") }.getOrNull() == true
                            com.jp.otherworld.data.MissedCall.said(context)
                            if (alienSet && !vaultOpen) missedPlaying = true
                        }
                    }
                    missedLife.addObserver(obs)
                    onDispose { missedLife.removeObserver(obs) }
                }
                if (missedPlaying) com.jp.otherworld.ui.home.MissedCallVideo { missedPlaying = false }

                // First run: the setup guide (home screen, wallpaper, notifications, talk along, lock, secret drawer).
                if (!state.setupDone) com.jp.otherworld.ui.home.SetupGuide { }
                // Not the default home any more: one tap to make it the home screen again.
                val askHome = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
                ) { isDefaultHome = getSystemService(android.app.role.RoleManager::class.java)?.isRoleHeld(android.app.role.RoleManager.ROLE_HOME) != false }
                if (state.setupDone && !isDefaultHome && !vaultOpen && !drawerOpen) Box(Modifier.fillMaxSize().statusBarsPadding().padding(top = 8.dp), contentAlignment = Alignment.TopCenter) {
                    com.jp.otherworld.ui.common.GlassButton("Make Other World your home screen", selected = true) {
                        getSystemService(android.app.role.RoleManager::class.java)?.let { askHome.launch(it.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME)) }
                    }
                }

                // Once: offer Talk along (the alien's lips move when an assistant talks); it needs the microphone.
                val askTalkMic = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                ) { ok -> LauncherStore.update { it.copy(talkAsked = true, talkMode = if (ok) it.talkMode else "OFF") } }
                val alienWallpaper = remember(homePresses, state.globeOffered) {
                    runCatching { android.app.WallpaperManager.getInstance(context).wallpaperInfo?.serviceName?.startsWith("com.jp.otherworld.wallpaper.Alien") }.getOrNull() == true
                }
                if (state.setupDone && !state.talkAsked && state.talkMode != "OFF" && alienWallpaper && !vaultOpen && !drawerOpen &&
                    context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) GlassSheet({ LauncherStore.update { it.copy(talkAsked = true, talkMode = "OFF") } }) {
                    GlassLabel("Let the alien talk?", 22.sp)
                    GlassLabel("His lips can move when Claude, Google Assistant, Alexa or Bixby talks. Android needs the microphone permission for this. Only how loud the voice is gets used; nothing is recorded or sent.", 15.sp, GlassDim)
                    GlassMenuItem("Allow") { askTalkMic.launch(android.Manifest.permission.RECORD_AUDIO) }
                    GlassMenuItem("Not now") { LauncherStore.update { it.copy(talkAsked = true, talkMode = "OFF") } }
                }
                if (homeMenuOpen) HomeMenu(onDismiss = { homeMenuOpen = false }) { action ->
                    homeMenuOpen = false
                    when (action) {
                        HomeAction.Wallpaper -> wallpaperOpen = true
                        HomeAction.Widgets -> widgetsOpen = true
                        HomeAction.Settings -> settingsOpen = true
                        HomeAction.EditPages -> editPagesOpen = true
                        HomeAction.MoveDock -> editingWidget = com.jp.otherworld.ui.home.EDIT_DOCK
                        HomeAction.MoveDots -> editingWidget = com.jp.otherworld.ui.home.EDIT_DOTS
                    }
                }
                folderOf(openFolder)?.let { f ->
                    FolderSheet(f, openFolder!!.second, apps, state.iconStyle,
                        onDismiss = { openFolder = null },
                        onAppMenu = { appMenu = it },
                        onRename = { renaming = openFolder })
                } ?: run { if (openFolder != null) openFolder = null }
                folderOf(folderMenu)?.let { f ->
                    FolderMenu(f, folderMenu!!.second, onDismiss = { folderMenu = null }, onRename = { renaming = folderMenu; folderMenu = null })
                }
                folderOf(renaming)?.let { f -> RenameFolder(f, renaming!!.second) { renaming = null } }
                appMenu?.let { target -> AppMenu(target, pager.currentPage, onMoveToRing = { ringPicker = it }) { appMenu = null } }
                ringPicker?.let { app -> RingPicker(app, state, appList) { ringPicker = null } }
                // Countdown rings around both eyes while the secret hold is in progress.
                if (eyesHeld) {
                    val p = remember { androidx.compose.animation.core.Animatable(0f) }
                    LaunchedEffect(Unit) { p.animateTo(1f, androidx.compose.animation.core.tween(6000, easing = androidx.compose.animation.core.LinearEasing)) }
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        val r = 34.dp.toPx(); val stroke = 4.dp.toPx()
                        for (c in alienEyes?.onScreen(size.width, size.height)?.map { it.intersect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height)).center }.orEmpty()) {
                            drawCircle(Color(0x337FE9FF), r, c)
                            drawArc(Color(0xFF7FE9FF), -90f, 360f * p.value, false,
                                topLeft = androidx.compose.ui.geometry.Offset(c.x - r, c.y - r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                        }
                    }
                }
                fun introAsset(style: String) = if (style == "VIDEO_SMOKE") "vault_open_smoke.mp4" else "vault_open.mp4"
                fun outroAsset(style: String) = if (style == "VIDEO_SMOKE") "vault_close_smoke.mp4" else "vault_close.mp4"
                if (introPlaying) VaultIntro(introAsset(state.vaultEffect)) {
                    vaultOpen = true
                    scope.launch { delay(700); introPlaying = false }
                }
                if (introPreview) VaultIntro(introAsset(previewStyle)) { introPreview = false; settingsOpen = true }
                AnimatedVisibility(vaultOpen, enter = fadeIn(androidx.compose.animation.core.tween(500)), exit = fadeOut(androidx.compose.animation.core.tween(250))) {
                    val bg by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null) {
                        value = withContext(Dispatchers.IO) {
                            context.assets.open("vault_cube.png").use { android.graphics.BitmapFactory.decodeStream(it) }.asImageBitmap()
                        }
                    }
                    // Face-down (panic) shuts it at once; Close and Back play it in reverse.
                    com.jp.otherworld.ui.vault.VaultScreen(allApps, state.iconStyle, bg, onPanic = { vaultOpen = false; outroPlaying = false }) { closeVault() }
                }
                // The film starts on the cube, so the drawer fades out under it; it ends on the alien, then fades to home.
                AnimatedVisibility(outroPlaying, enter = androidx.compose.animation.EnterTransition.None, exit = fadeOut(androidx.compose.animation.core.tween(400))) {
                    VaultIntro(outroAsset(state.vaultEffect), onFirstFrame = { vaultOpen = false }) { vaultOpen = false; outroPlaying = false }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) homePresses++
    }

    override fun onResume() {
        super.onResume()
        isDefaultHome = getSystemService(android.app.role.RoleManager::class.java)?.isRoleHeld(android.app.role.RoleManager.ROLE_HOME) != false
        // First time Other World is the home screen: offer the green alien wallpaper (once).
        val rm = getSystemService(android.app.role.RoleManager::class.java)
        if (LauncherStore.state.value.setupDone && rm?.isRoleHeld(android.app.role.RoleManager.ROLE_HOME) == true && !LauncherStore.state.value.globeOffered) {
            LauncherStore.update { it.copy(globeOffered = true) }
            setLiveWallpaper(this, com.jp.otherworld.wallpaper.AlienGreenWallpaperService::class.java)
        }
    }

    override fun onStart() {
        super.onStart()
        runCatching { WidgetHost.host.startListening() }
    }

    override fun onStop() {
        super.onStop()
        runCatching { WidgetHost.host.stopListening() }
    }

    // ── Adding a widget: bind (ask permission if needed) → configure (if it has a setup screen) → place ──

    private fun addWidget(info: AppWidgetProviderInfo, page: Int) {
        val id = WidgetHost.host.allocateAppWidgetId()
        pendingWidget = Triple(id, info, page)
        if (WidgetHost.manager.bindAppWidgetIdIfAllowed(id, info.provider)) configureOrPlaceWidget()
        else bindWidget.launch(
            Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
        )
    }

    private fun configureOrPlaceWidget() {
        val (id, info, _) = pendingWidget ?: return
        if (info.configure != null) {
            val started = runCatching {
                WidgetHost.host.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIGURE, null)
            }.isSuccess
            if (started) return
        }
        placeWidget()
    }

    @Deprecated("Widget configure screens still report back here")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_CONFIGURE) return
        val p = pendingWidget ?: return
        if (resultCode == RESULT_OK) placeWidget() else { WidgetHost.delete(p.first); pendingWidget = null }
    }

    private fun placeWidget() {
        val (id, info, page) = pendingWidget ?: return
        pendingWidget = null
        val cw = HomeGrid.cellWpx.takeIf { it > 0 } ?: 150f
        val ch = HomeGrid.cellHpx.takeIf { it > 0 } ?: 150f
        // Never smaller than the widget's own minimum size (its suggested cell count can be).
        val sx = maxOf(info.targetCellWidth, ceil(info.minWidth / cw).toInt()).coerceIn(1, GRID_COLS)
        val sy = maxOf(info.targetCellHeight, ceil(info.minHeight / ch).toInt()).coerceIn(1, GRID_ROWS)
        LauncherStore.update { s ->
            val order = listOf(page) + s.pages.indices.filter { it != page }
            for (p in order) s.freeCell(p, sx, sy)?.let { (c, r) ->
                return@update s.withPage(p) { it + HomeItem.Widget(id, info.provider.flattenToString(), c, r, sx, sy) }
            }
            s.copy(pages = s.pages + Page(listOf(HomeItem.Widget(id, info.provider.flattenToString(), 0, 0, sx, sy))))
        }
        Toast.makeText(this, "Widget added — hold it to move or resize", Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val REQ_CONFIGURE = 7001
    }
}

/** Home waits for the first app load so the glass tile art is ready. */
private fun GlassIconsReady(apps: List<AppInfo>) = apps.isNotEmpty()

/** Tells the alien wallpaper where a finger is on home, so it can lean toward it. Never takes the touch. */
private fun Modifier.leanAlien(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Initial)
            val p = ev.changes.firstOrNull { it.pressed }
            if (p != null) AlienSignals.touch(p.position.x / size.width, p.position.y / size.height) else AlienSignals.release()
        }
    }
}

/** The two eyes of the alien wallpaper: spots as fractions of the image, plus the image size. */
private class AlienEyes(val spots: List<android.graphics.RectF>, val imgW: Int, val imgH: Int) {
    /** Each eye on a screen of w×h, placed the way the wallpaper draws it ([alienPlace]). */
    fun onScreen(w: Float, h: Float): List<androidx.compose.ui.geometry.Rect> {
        val p = com.jp.otherworld.wallpaper.alienPlace(w, h, imgW, imgH)
        val sc = p.width() / imgW
        val l = p.left; val t = p.top
        return spots.map { androidx.compose.ui.geometry.Rect(l + it.left * imgW * sc, t + it.top * imgH * sc, l + it.right * imgW * sc, t + it.bottom * imgH * sc) }
    }
}

/**
 * The hidden drawer's secret: one finger on each of the alien's eyes, both held for 6 seconds.
 * The spots stay where the eyes are drawn, with room for the face's float and tilt. Watches touches
 * without taking them; once both eyes are held it swallows the touch so nothing underneath reacts.
 */
private fun Modifier.secretEyes(eyes: AlienEyes?, slack: Float, onHolding: (Boolean) -> Unit, onOpen: () -> Unit): Modifier =
    if (eyes == null || eyes.spots.size < 2) this else pointerInput(eyes) {
    coroutineScope {
        var job: Job? = null
        var holding = false
        awaitPointerEventScope {
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                val (left, right) = eyes.onScreen(size.width.toFloat(), size.height.toFloat()).map { it.inflate(slack) }
                val down = ev.changes.filter { it.pressed }
                // Two different fingers, one on each eye.
                val onLeft = down.filter { left.contains(it.position) }
                val onRight = down.filter { right.contains(it.position) }
                val both = onLeft.any { a -> onRight.any { b -> b.id != a.id } }
                if (both) {
                    holding = true
                    if (job == null) { onHolding(true); job = launch { delay(6000); onHolding(false); onOpen() } }
                } else {
                    if (job != null) onHolding(false)
                    job?.cancel(); job = null
                }
                if (holding) ev.changes.forEach { it.consume() }
                if (down.isEmpty()) holding = false
            }
        }
    }
}
