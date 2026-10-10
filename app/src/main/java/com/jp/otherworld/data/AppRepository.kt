package com.jp.otherworld.data

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import com.jp.otherworld.ui.home.GlassIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Installed apps, loaded off the main thread and kept current as apps are installed or removed. */
object AppRepository {
    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loadJob: Job? = null
    private var started = false

    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        reload(app)
        app.getSystemService(LauncherApps::class.java).registerCallback(object : LauncherApps.Callback() {
            override fun onPackageAdded(pkg: String, user: UserHandle) = reload(app)
            override fun onPackageChanged(pkg: String, user: UserHandle) = reload(app)
            override fun onPackageRemoved(pkg: String, user: UserHandle) {
                LauncherStore.update { it.withoutPackage(pkg) }
                reload(app)
            }
            override fun onPackagesAvailable(pkgs: Array<out String>, user: UserHandle, replacing: Boolean) = reload(app)
            override fun onPackagesUnavailable(pkgs: Array<out String>, user: UserHandle, replacing: Boolean) = reload(app)
        }, Handler(Looper.getMainLooper()))
    }

    fun reload(context: Context) {
        loadJob?.cancel()
        loadJob = scope.launch {
            GlassIcons.load(context)
            _apps.value = loadApps(context) { GlassIcons.glassed(it) }
        }
    }
}
