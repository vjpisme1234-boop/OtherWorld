package com.jp.otherworld.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.jp.otherworld.data.AlienSignals
import com.jp.otherworld.data.GLOW_OFF
import com.jp.otherworld.data.GlowColors
import com.jp.otherworld.data.LauncherStore
import com.jp.otherworld.data.NotificationHub

/**
 * Watches notifications (once the user allows Notification access) for two things only: making the
 * alien glow when a new one arrives, and the unread dots on icons. Nothing is stored or sent anywhere.
 */
class NotificationWatcher : NotificationListenerService() {
    private val seen = HashSet<String>()

    override fun onCreate() {
        super.onCreate()
        LauncherStore.init(this)
    }

    override fun onListenerConnected() {
        val active = runCatching { activeNotifications }.getOrNull().orEmpty()
        seen.clear(); active.forEach { seen += it.key }
        recount(active)
    }

    override fun onListenerDisconnected() = NotificationHub.set(emptyMap())

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val isNew = seen.add(sbn.key)
        recount(runCatching { activeNotifications }.getOrNull().orEmpty())
        // Only brand-new notifications glow, not updates to one already showing.
        if (!isNew || !counts(sbn)) return
        val state = LauncherStore.state.value
        if (!state.notifGlow) return
        val color = state.glowColors[sbn.packageName] ?: GlowColors.byType(this, sbn.packageName, sbn.notification)
        if (color != GLOW_OFF) AlienSignals.pulse(color)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        seen -= sbn.key
        recount(runCatching { activeNotifications }.getOrNull().orEmpty())
    }

    /** Real, dismissable notifications from other apps (not ongoing ones like music or downloads). */
    private fun counts(sbn: StatusBarNotification): Boolean {
        val f = sbn.notification.flags
        return sbn.packageName != packageName && sbn.isClearable &&
            f and Notification.FLAG_ONGOING_EVENT == 0 && f and Notification.FLAG_GROUP_SUMMARY == 0
    }

    private fun recount(active: Array<out StatusBarNotification>) =
        NotificationHub.set(active.filter(::counts).groupingBy { it.packageName }.eachCount())
}
