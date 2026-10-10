package com.jp.otherworld.ui.home

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.SizeF
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.abs

/** The launcher's single AppWidgetHost; started while the home activity is visible. */
object WidgetHost {
    private const val HOST_ID = 4071
    lateinit var host: AppWidgetHost; private set
    lateinit var manager: AppWidgetManager; private set
    private val views = HashMap<Int, LongPressHostView>()

    fun init(context: Context) {
        if (::host.isInitialized) return
        val app = context.applicationContext
        manager = AppWidgetManager.getInstance(app)
        host = object : AppWidgetHost(app, HOST_ID) {
            override fun onCreateView(context: Context, id: Int, info: AppWidgetProviderInfo?): AppWidgetHostView =
                LongPressHostView(context)
        }
    }

    fun info(id: Int): AppWidgetProviderInfo? = manager.getAppWidgetInfo(id)

    /** One view per widget id, reused across recompositions. */
    fun view(context: Context, id: Int, onLongPress: () -> Unit): LongPressHostView? {
        val info = info(id) ?: return null
        val v = views.getOrPut(id) { host.createView(context.applicationContext, id, info) as LongPressHostView }
        v.onLongPress = onLongPress
        (v.parent as? android.view.ViewGroup)?.removeView(v)
        return v
    }

    fun delete(id: Int) {
        views.remove(id)
        runCatching { host.deleteAppWidgetId(id) }
    }

    fun resize(id: Int, wDp: Float, hDp: Float) {
        val size = SizeF(wDp, hDp)
        val opts = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, wDp.toInt())
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, wDp.toInt())
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, hDp.toInt())
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, hDp.toInt())
            putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(size))
        }
        runCatching { manager.updateAppWidgetOptions(id, opts) }
    }
}

/** Widget view that still lets the launcher see a long-press (widgets swallow touches otherwise). */
class LongPressHostView(context: Context) : AppWidgetHostView(context) {
    var onLongPress: (() -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var fired = false
    private val longPress = Runnable { fired = true; parent?.requestDisallowInterceptTouchEvent(true); onLongPress?.invoke() }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                fired = false; downX = ev.x; downY = ev.y
                handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> if (abs(ev.x - downX) > slop || abs(ev.y - downY) > slop) handler.removeCallbacks(longPress)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(longPress)
        }
        return fired
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            handler.removeCallbacks(longPress); fired = false
        }
        return fired || super.onTouchEvent(event)
    }
}

@Composable
fun WidgetCell(id: Int, widthDp: Dp, heightDp: Dp, onLongPress: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier) {
        AndroidView(
            factory = { ctx -> WidgetHost.view(ctx, id, onLongPress) ?: android.view.View(ctx) },
            update = { v ->
                (v as? LongPressHostView)?.onLongPress = onLongPress
                WidgetHost.resize(id, widthDp.value, heightDp.value)
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
