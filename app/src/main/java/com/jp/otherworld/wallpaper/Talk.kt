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
 * "Talk along": the alien's lips move with a voice the phone is playing. Listens to the phone's overall
 * output level only (Android's Visualizer on the output mix); nothing is recorded or kept. Needs the
 * microphone permission because that's how Android guards this.
 *
 * By default only voices count: Android doesn't say which app is playing, but it does say what kind of
 * sound each player is. Assistants (Google, Bixby) play "assistant", voice chats (Claude live, calls over
 * the internet) "voice communication", and talking apps (Alexa, read-aloud) "speech". Music and videos
 * don't move his mouth unless "Any sound" is chosen.
 */
class TalkListener {
    private var viz: Visualizer? = null
    private var level = 0f
    private var wide = 0.5f
    private val m = Visualizer.MeasurementPeakRms()
    private var fft = ByteArray(0)

    private var audio: android.media.AudioManager? = null
    private var voiceUntil = 0L; private var checkedAt = 0L

    fun start(context: Context): Boolean {
        if (viz != null) return true
        audio = context.getSystemService(android.media.AudioManager::class.java)
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return false
        viz = runCatching {
            Visualizer(0).apply {
                enabled = false
                captureSize = 256
                measurementMode = Visualizer.MEASUREMENT_MODE_PEAK_RMS
                scalingMode = Visualizer.SCALING_MODE_AS_PLAYED
                enabled = true
            }
        }.onFailure { android.util.Log.w("OWTalk", "Visualizer unavailable: $it") }.getOrNull()
        fft = ByteArray(viz?.captureSize ?: 0)
        return viz != null
    }

    fun stop() { runCatching { viz?.enabled = false; viz?.release() }; viz = null; level = 0f }

    /** Is something playing a voice right now (assistant, voice chat or speech)? Checked a few times a second. */
    private fun voicePlaying(): Boolean {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - checkedAt > 300) {
            checkedAt = now
            val voice = runCatching {
                audio?.activePlaybackConfigurations.orEmpty().any { cfg ->
                    val a = cfg.audioAttributes
                    a.usage == android.media.AudioAttributes.USAGE_ASSISTANT ||
                        a.usage == android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION ||
                        a.contentType == android.media.AudioAttributes.CONTENT_TYPE_SPEECH
                }
            }.getOrDefault(false)
            if (voice) voiceUntil = now + 400
        }
        return now < voiceUntil
    }

    /** How open the mouth should be now, 0..1 (opens fast, closes a little slower, like speech). */
    fun open(anySound: Boolean = false): Float {
        val v = viz ?: return 0f
        val heard = anySound || voicePlaying()
        val target = if (!heard) 0f else runCatching {
            v.getMeasurementPeakRms(m)
            // mRms is in millibels: about -9600 (silence) .. 0 (full). Voices sit around -4500..-1500.
            // a square-root curve so normal speech already opens it most of the way
            Math.sqrt(((m.mRms + 5600f) / 3600f).coerceIn(0f, 1f).toDouble()).toFloat()
        }.getOrDefault(0f)
        level += (target - level) * (if (target > level) 0.55f else 0.35f)
        // Shape: bright sounds (ee, s) pull the mouth wide and flat, dark ones (oo, oh) small and round.
        runCatching {
            if (fft.isNotEmpty() && v.getFft(fft) == Visualizer.SUCCESS) {
                val rate = v.samplingRate / 1000f    // samplingRate is in milliHz
                val bins = fft.size / 2
                fun band(lo: Float, hi: Float): Float {
                    var e = 0f
                    for (k in 1 until bins) {
                        val f = k * rate / 2f / bins
                        if (f in lo..hi) { val re = fft[2 * k].toFloat(); val im = fft[2 * k + 1].toFloat(); e += re * re + im * im }
                    }
                    return e
                }
                val low = band(150f, 900f); val high = band(1800f, 6000f)
                if (low + high > 50f) wide += ((high / (low + high)).coerceIn(0f, 1f) - wide) * 0.25f
            }
        }
        return level
    }

    /** 0 = round (oo), 1 = wide (ee). */
    fun width(): Float = wide
}

/**
 * The alien's mouth and jaw, drawn in image pixels (the canvas is already placed and scaled like the face).
 * Talking: the upper lip lifts a little, the lips part, and the whole lower jaw drops (chin included),
 * with the cheeks stretching smoothly. Everything above the mouth stays put, so the head never tips.
 * The opening's width follows the sound (round for oo/oh, wide for ee).
 */
class Mouth(alien: Bitmap, private val rim: Int) {
    private val w = alien.width.toFloat(); private val h = alien.height.toFloat()
    // The user's alien (originals/alien.png, 521×597): the lips meet at y ≈ 83.75%, centred at x ≈ 51.8%.
    private val cx = w * 0.518f; private val lipY = h * 0.8375f; private val half = w * 0.10f
    private val maxLips = h * 0.032f     // extra parting of the lips
    private val maxJaw = h * 0.030f      // whole lower jaw drop
    private val maxUp = h * 0.012f
    // Above the lips: a narrow patch whose bend fades out before the nose.
    private val uLeft = (cx - half * 1.5f).toInt(); private val uRight = (cx + half * 1.5f).toInt()
    private val uTop = (lipY - h * 0.055f).toInt(); private val mid = lipY.toInt()
    // Below the lips: the whole lower face down to the bottom of the picture (the jaw), fading to still at its sides.
    private val jawHalf = w * 0.32f
    private val lLeft = (cx - jawHalf).toInt().coerceAtLeast(0); private val lRight = (cx + jawHalf).toInt().coerceAtMost(alien.width)
    private val lBottom = alien.height
    private val upper = Bitmap.createBitmap(alien, uLeft, uTop, uRight - uLeft, mid - uTop)
    private val lower = Bitmap.createBitmap(alien, lLeft, mid, lRight - lLeft, lBottom - mid)
    private val colsU = 28; private val rowsU = 8; private val colsL = 40; private val rowsL = 18
    private val vu = FloatArray((colsU + 1) * (rowsU + 1) * 2)
    private val vl = FloatArray((colsL + 1) * (rowsL + 1) * 2)
    private val hole = Path()
    private val inside = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(245, 2, 6, 10) }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = rim; alpha = 110 }
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    // 1 in the middle, easing to 0 at ±span.
    private fun bell(x: Float, span: Float): Float {
        val d = (x - cx) / span
        if (d <= -1f || d >= 1f) return 0f
        val c = Math.cos(d * Math.PI / 2).toFloat(); return c * c
    }

    /** How far the lower face moves down at x, `u` of the way from the lip line (0) to the picture's bottom (1). */
    private fun drop(x: Float, u: Float, span: Float, lips: Float, jaw: Float): Float {
        val lipPart = lips * bell(x, span) * (1f - u) * (1f - u)
        // The jaw drops as one piece across its width; right at the lip line it only moves inside the mouth,
        // so the corners stay closed, and it eases in going down.
        val ease = (u / 0.35f).coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
        val jawPart = jaw * bell(x, jawHalf) * maxOf(bell(x, span * 1.15f), ease)
        return lipPart + jawPart
    }

    fun draw(c: Canvas, open: Float, wide: Float) {
        if (open < 0.03f) return
        val span = half * (0.75f + 0.3f * wide)
        val lips = maxLips * open * (1.15f - 0.35f * wide)
        val jaw = maxJaw * open
        val up = maxUp * open
        var i = 0
        val uw = (uRight - uLeft).toFloat()
        for (r in 0..rowsU) {
            val t = r / rowsU.toFloat()
            val y0 = uTop + (mid - uTop) * t
            for (k in 0..colsU) { val x = uLeft + uw * k / colsU; vu[i++] = x; vu[i++] = y0 - up * bell(x, span) * t * t }
        }
        i = 0
        val lw = (lRight - lLeft).toFloat()
        for (r in 0..rowsL) {
            val u = r / rowsL.toFloat()
            val y0 = mid + (lBottom - mid) * u
            for (k in 0..colsL) { val x = lLeft + lw * k / colsL; vl[i++] = x; vl[i++] = y0 + drop(x, u, span, lips, jaw) }
        }
        // The opening (shows through the gap the parted lips leave), then the lips and jaw over it.
        val holeSpan = span * 1.15f
        hole.reset()
        val steps = 28
        for (s in 0..steps) {
            val x = cx - holeSpan + 2 * holeSpan * s / steps
            val y = lipY - up * bell(x, span)
            if (s == 0) hole.moveTo(x, y) else hole.lineTo(x, y)
        }
        for (s in steps downTo 0) {
            val x = cx - holeSpan + 2 * holeSpan * s / steps
            hole.lineTo(x, lipY + drop(x, 0f, span, lips, jaw))
        }
        hole.close()
        c.drawPath(hole, inside)
        c.drawBitmapMesh(lower, colsL, rowsL, vl, 0, null, 0, paint)
        c.drawBitmapMesh(upper, colsU, rowsU, vu, 0, null, 0, paint)
        rimPaint.strokeWidth = (h * 0.002f).coerceAtLeast(1f)
        c.drawPath(hole, rimPaint)
    }
}
