package com.jp.otherworld.ui.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF

/**
 * The generated glass tiles (art/generated/glass_tile.png, glass_apps.png) and the
 * "original icon in glass" look: the app's own icon sits inside the clear tile and the tile's
 * bright edges are screened back over it — the same edge-only sheen as the wheel.
 */
object GlassIcons {
    lateinit var tile: Bitmap; private set
    lateinit var appsButton: Bitmap; private set

    private val filter = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val sheen = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
        blendMode = BlendMode.SCREEN
        colorFilter = ColorMatrixColorFilter(floatArrayOf(
            1.6f, 0f, 0f, 0f, -150f,
            0f, 1.6f, 0f, 0f, -150f,
            0f, 0f, 1.6f, 0f, -150f,
            0f, 0f, 0f, 1f, 0f
        ))
    }

    @Synchronized
    fun load(context: Context) {
        if (::tile.isInitialized) return
        fun bmp(name: String) = context.assets.open(name).use { BitmapFactory.decodeStream(it) }
        tile = bmp("glass_tile.png")
        appsButton = bmp("glass_apps.png")
    }

    fun glassed(icon: Bitmap, px: Int = 192): Bitmap {
        val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val all = Rect(0, 0, px, px)
        c.drawBitmap(tile, null, all, filter)
        val inset = px * 0.2f
        c.drawBitmap(icon, null, RectF(inset, inset, px - inset, px - inset), filter)
        c.drawBitmap(tile, null, all, sheen)
        return out
    }
}
