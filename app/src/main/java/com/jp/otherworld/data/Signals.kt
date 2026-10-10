package com.jp.otherworld.data

import android.app.Notification
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Live signals for the alien wallpaper. The wallpaper engines run in the launcher's own process, so
 * the home screen and the notification watcher write here and the wallpaper reads it every frame.
 */
object AlienSignals {
    // Finger on home, as fractions of the screen (0..1).
    @Volatile var touchX = 0.5f; private set
    @Volatile var touchY = 0.5f; private set
    @Volatile var touching = false; private set
    @Volatile var releasedAt = 0L; private set

    fun touch(fx: Float, fy: Float) { touchX = fx; touchY = fy; touching = true }
    fun release() { if (touching) { touching = false; releasedAt = SystemClock.uptimeMillis() } }

    // Notification pulse: colour and when it started.
    @Volatile var pulseColor = 0; private set
    @Volatile var pulseStart = -1L; private set

    fun pulse(color: Int) { pulseColor = color; pulseStart = SystemClock.uptimeMillis() }

    /** The alien wallpaper is on screen behind home right now (set by the wallpaper itself). */
    @Volatile var alienVisible = false

    // Hidden drawer: the face breaks apart (vanishing) and later comes back together (reforming).
    const val VANISH_MS = 1600L
    const val REFORM_MS = 1200L
    @Volatile var vanishing = false; private set
    @Volatile var vanishAt = -1L; private set
    @Volatile var shatterStyle = "DUST"

    fun vanish(style: String) { shatterStyle = style; vanishing = true; vanishAt = SystemClock.uptimeMillis() }
    fun reform() { if (vanishing) { vanishing = false; vanishAt = SystemClock.uptimeMillis() } }

    /** 0 = whole face, 1 = fully gone. */
    fun shatterProgress(now: Long): Float {
        if (vanishAt < 0) return 0f
        return if (vanishing) ((now - vanishAt) / VANISH_MS.toFloat()).coerceIn(0f, 1f)
        else 1f - ((now - vanishAt) / REFORM_MS.toFloat()).coerceIn(0f, 1f)
    }
}

/** Unread notifications per app (for the dots), kept by the notification watcher. */
object NotificationHub {
    private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val counts: StateFlow<Map<String, Int>> = _counts
    fun set(counts: Map<String, Int>) { _counts.value = counts }
}

/** Glow colours by kind of notification. */
object GlowColors {
    const val MESSAGE = 0xFF39FF6A.toInt()   // green
    const val CALL = 0xFF3AA0FF.toInt()      // blue
    const val EMAIL = 0xFFFFE14A.toInt()     // yellow
    const val SOCIAL = 0xFFFF4FD8.toInt()    // pink
    const val OTHER = 0xFFFFFFFF.toInt()     // white

    /** Choices offered in Home settings, by name. */
    val palette = listOf(
        "Green" to MESSAGE, "Blue" to CALL, "Yellow" to EMAIL, "Pink" to SOCIAL, "White" to OTHER,
        "Purple" to 0xFFB45CFF.toInt(), "Red" to 0xFFFF4040.toInt(), "Orange" to 0xFFFF9A2E.toInt(), "Cyan" to 0xFF7FE9FF.toInt()
    )

    /** The default colour for a notification: by its own category, else by the app's category. */
    fun byType(context: Context, pkg: String, n: Notification): Int = when (n.category) {
        Notification.CATEGORY_MESSAGE -> MESSAGE
        Notification.CATEGORY_CALL, Notification.CATEGORY_MISSED_CALL -> CALL
        Notification.CATEGORY_EMAIL -> EMAIL
        Notification.CATEGORY_SOCIAL -> SOCIAL
        else -> when (runCatching { context.packageManager.getApplicationInfo(pkg, 0).category }.getOrNull()) {
            ApplicationInfo.CATEGORY_SOCIAL -> SOCIAL
            else -> OTHER
        }
    }
}
