package com.jp.otherworld.service

import android.content.Context

/** Home gestures: notifications and quick settings (opened the way launchers do; no special access). */
object Gestures {
    fun notifications(context: Context) = statusBar(context, "expandNotificationsPanel")

    fun quickSettings(context: Context) = statusBar(context, "expandSettingsPanel")

    /** Opens the notification shade (needs EXPAND_STATUS_BAR, a normal permission). */
    private fun statusBar(context: Context, method: String): Boolean = runCatching {
        @Suppress("WrongConstant") val sbm = context.getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager").getMethod(method).invoke(sbm)
        true
    }.getOrDefault(false)
}
