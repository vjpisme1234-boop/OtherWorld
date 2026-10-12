package com.jp.otherworld.wallpaper

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.media.audiofx.Visualizer

/**
 * "Talk along" (test): the alien's lips part with whatever sound the phone is playing (a voice, music…).
 * Listens to the phone's overall output level only (Android's Visualizer on the output mix); nothing is
 * recorded or kept. Needs the microphone permission because that's how Android guards this.
 */
class TalkListener {
    private var viz: Visualizer? = null
    private var level = 0f
    private val m = Visualizer.MeasurementPeakRms()

    fun start(context: Context): Boolean {
        if (viz != null) return true
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return false
        viz = runCatching {
            Visualizer(0).apply {
                enabled = false
                captureSize = Visualizer.getCaptureSizeRange()[0]
                measurementMode = Visualizer.MEASUREMENT_MODE_PEAK_RMS
                scalingMode = Visualizer.SCALING_MODE_AS_PLAYED
                enabled = true
            }
        }.onFailure { android.util.Log.w("OWTalk", "Visualizer unavailable: $it") }.getOrNull()
        return viz != null
    }

    fun stop() { runCatching { viz?.enabled = false; viz?.release() }; viz = null; level = 0f }

    /** How open the mouth should be now, 0..1 (opens fast, closes a little slower, like speech). */
    fun open(): Float {
        val v = viz ?: return 0f
        val target = runCatching {
            v.getMeasurementPeakRms(m)
            // mRms is in millibels: about -9600 (silence) .. 0 (full). Voices sit around -4500..-1500.
            ((m.mRms + 4800f) / 3300f).coerceIn(0f, 1f)
        }.getOrDefault(0f)
        level += (target - level) * (if (target > level) 0.6f else 0.3f)
        return level
    }
}

/**
 * The alien's mouth, drawn in image pixels (the canvas is already placed and scaled like the face).
 * Opening: the lower lip and chin slide down and a dark gap with a glowing rim shows between the lips.
 */
class Mouth(private val alien: Bitmap, private val rim: Int) {
    private val w = alien.width.toFloat(); private val h = alien.height.toFloat()
    // The user's alien (originals/alien.png, 521×597): lips meet at y ≈ 84%, centred at x ≈ 52%.
    private val cx = w * 0.518f; private val lipY = h * 0.841f; private val half = w * 0.088f
    private val maxGap = h * 0.024f
    private val chin = h * 0.06f
    private val src = Rect((cx - half).toInt(), lipY.toInt(), (cx + half).toInt(), (lipY + chin).toInt())
    private val dst = RectF()
    private val clip = Path()
    private val inside = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(240, 2, 6, 10) }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = rim; alpha = 150 }
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    fun draw(c: Canvas, open: Float) {
        if (open < 0.03f) return
        val gap = maxGap * open
        val save = c.save()
        // Only inside a lens around the lips, so the edges of the change fade into the mesh.
        clip.reset(); clip.addOval(RectF(cx - half, lipY - chin * 0.4f, cx + half, lipY + chin + gap), Path.Direction.CW)
        c.clipPath(clip)
        // Lower lip + chin, pushed down.
        dst.set(src.left.toFloat(), lipY + gap, src.right.toFloat(), lipY + chin + gap)
        c.drawBitmap(alien, src, dst, paint)
        // The opening between the lips.
        val o = RectF(cx - half * 0.78f, lipY - gap * 0.15f, cx + half * 0.78f, lipY + gap * 1.05f)
        c.drawOval(o, inside)
        rimPaint.strokeWidth = (h * 0.003f).coerceAtLeast(1f)
        c.drawOval(o, rimPaint)
        c.restoreToCount(save)
    }
}
