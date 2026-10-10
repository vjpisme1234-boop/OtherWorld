package com.jp.otherworld.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Only used so a double-tap on home can lock the screen (Android lets apps lock the screen this way
 * while keeping fingerprint/face unlock). It reads nothing on screen and reacts to no events.
 */
class GestureService : AccessibilityService() {
    override fun onServiceConnected() { running = this }
    override fun onUnbind(intent: Intent?): Boolean { running = null; return super.onUnbind(intent) }
    override fun onDestroy() { running = null; super.onDestroy() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    companion object {
        @Volatile private var running: GestureService? = null
        fun perform(action: Int): Boolean = running?.performGlobalAction(action) ?: false

        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, GestureService::class.java).flattenToString()
            val list = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            return list.split(':').any { it.equals(me, ignoreCase = true) }
        }

        fun openSettings(context: Context) {
            runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}

/** Home gestures: lock, notifications, quick settings. */
object Gestures {
    fun lock(): Boolean = GestureService.perform(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)

    fun notifications(context: Context) =
        statusBar(context, "expandNotificationsPanel") || GestureService.perform(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)

    fun quickSettings(context: Context) =
        statusBar(context, "expandSettingsPanel") || GestureService.perform(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)

    /** Opens the notification shade the way launchers do (needs EXPAND_STATUS_BAR, a normal permission). */
    private fun statusBar(context: Context, method: String): Boolean = runCatching {
        @Suppress("WrongConstant") val sbm = context.getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager").getMethod(method).invoke(sbm)
        true
    }.getOrDefault(false)
}
