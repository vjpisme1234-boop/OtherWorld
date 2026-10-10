package com.jp.otherworld.data

import android.content.pm.ApplicationInfo

/** The wheel's rings, in order, after "All". Apps go by the category they declare unless moved. */
val BUILT_IN_RINGS = listOf("Games", "Social", "Video & Music", "Photos", "Tools")
const val ALL_RING = "All"

fun defaultRing(category: Int): String = when (category) {
    ApplicationInfo.CATEGORY_GAME -> "Games"
    ApplicationInfo.CATEGORY_SOCIAL -> "Social"
    ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO -> "Video & Music"
    ApplicationInfo.CATEGORY_IMAGE -> "Photos"
    else -> "Tools"
}

fun LauncherState.ringOf(app: AppInfo): String = ringOf[app.packageName] ?: defaultRing(app.category)

/** "All" plus every ring that has apps in it. */
fun LauncherState.rings(apps: List<AppInfo>): List<String> {
    val used = apps.map { ringOf(it) }.toSet()
    return listOf(ALL_RING) + (BUILT_IN_RINGS + customRings).distinct().filter { it in used }
}

fun LauncherState.appsIn(ring: String, apps: List<AppInfo>): List<AppInfo> =
    if (ring == ALL_RING) apps else apps.filter { ringOf(it) == ring }

/** Move an app to a ring (making it if new); moving it back to its own category clears the override. */
fun LauncherState.moveToRing(app: AppInfo, ring: String): LauncherState {
    val name = ring.trim().ifEmpty { return this }
    val custom = if (name in BUILT_IN_RINGS || name in customRings) customRings else customRings + name
    val map = if (name == defaultRing(app.category)) ringOf - app.packageName else ringOf + (app.packageName to name)
    return copy(ringOf = map, customRings = custom)
}
