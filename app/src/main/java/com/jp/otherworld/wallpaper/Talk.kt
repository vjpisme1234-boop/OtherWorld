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
 * The alien's mouth, drawn in image pixels (the canvas is already placed and scaled like the face).
 * The mouth shapes are real frames from the lip-sync video the user liked (assets/talk/m0..m9, closed to
 * wide open, lined up with this alien's lips and feathered into the face). The louder the voice, the more
 * open the frame shown; it fades in from the face's own closed lips. A small jaw drop goes with it.
 * Tinted to the wallpaper's colour (same hue shifts the coloured aliens were made with).
 */
class Mouth(assets: android.content.res.AssetManager, private val alien: Bitmap, asset: String) {
    private val w = alien.width.toFloat(); private val h = alien.height.toFloat()
    private val sprites: List<Bitmap>
    private val box: RectF
    init {
        val meta = org.json.JSONObject(assets.open("talk/talk.json").bufferedReader().use { it.readText() })
        sprites = (0 until meta.getInt("count")).map { i -> assets.open("talk/m$i.png").use { android.graphics.BitmapFactory.decodeStream(it) } }
        box = RectF((meta.getDouble("x") * w).toFloat(), (meta.getDouble("y") * h).toFloat(),
            ((meta.getDouble("x") + meta.getDouble("w")) * w).toFloat(), ((meta.getDouble("y") + meta.getDouble("h")) * h).toFloat())
    }
    private val spritePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
        tint(asset)?.let { colorFilter = android.graphics.ColorMatrixColorFilter(it) }
    }
    private var shown = 0f   // eased frame position, so frames don't flicker

    // Jaw: the lower face below the lips drops a little, fading to still at its sides (the head never moves).
    private val cx = w * 0.518f; private val lipY = h * 0.8375f
    private val jawHalf = w * 0.32f; private val maxJaw = h * 0.014f
    private val lLeft = (cx - jawHalf).toInt().coerceAtLeast(0); private val lRight = (cx + jawHalf).toInt().coerceAtMost(alien.width)
    private val mid = lipY.toInt(); private val lBottom = alien.height
    private val lower = Bitmap.createBitmap(alien, lLeft, mid, lRight - lLeft, lBottom - mid)
    private val colsL = 32; private val rowsL = 14
    private val vl = FloatArray((colsL + 1) * (rowsL + 1) * 2)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    private fun bell(x: Float, span: Float): Float {
        val d = (x - cx) / span
        if (d <= -1f || d >= 1f) return 0f
        val c = Math.cos(d * Math.PI / 2).toFloat(); return c * c
    }

    fun draw(c: Canvas, open: Float, @Suppress("UNUSED_PARAMETER") wide: Float) {
        shown += (open * (sprites.size - 1) - shown) * 0.5f
        if (open < 0.03f && shown < 0.3f) return
        // Jaw (under the mouth image, so its top edge is hidden by it).
        val jaw = maxJaw * open
        if (jaw > 0.2f) {
            var i = 0
            val lw = (lRight - lLeft).toFloat()
            for (r in 0..rowsL) {
                val u = r / rowsL.toFloat()
                val y0 = mid + (lBottom - mid) * u
                val ease = (u / 0.35f).coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
                for (k in 0..colsL) { val x = lLeft + lw * k / colsL; vl[i++] = x; vl[i++] = y0 + jaw * bell(x, jawHalf) * maxOf(bell(x, w * 0.12f), ease) }
            }
            c.drawBitmapMesh(lower, colsL, rowsL, vl, 0, null, 0, paint)
        }
        // The mouth frame for this loudness, fading in from the face's own lips when nearly closed.
        val idx = shown.roundToIntSafe().coerceIn(0, sprites.size - 1)
        spritePaint.alpha = (255 * (shown / 1.2f).coerceIn(0f, 1f)).toInt()
        c.drawBitmap(sprites[idx], null, box, spritePaint)
    }

    private fun Float.roundToIntSafe() = Math.round(this)

    companion object {
        /** Hue shift from the cyan original to this wallpaper's colour (null = cyan). */
        fun tint(asset: String): android.graphics.ColorMatrix? {
            if (asset.contains("ice")) return android.graphics.ColorMatrix().apply { setSaturation(0.18f) }
            val deg = when {
                asset.contains("green") -> -71f; asset.contains("pink") -> 133f; asset.contains("purple") -> 86f
                asset.contains("red") -> 174f; asset.contains("gold") -> -162f; asset.contains("blue") -> 39f
                else -> return null
            }
            val r = Math.toRadians(deg.toDouble()); val a = Math.cos(r).toFloat(); val b = Math.sin(r).toFloat()
            return android.graphics.ColorMatrix(floatArrayOf(
                0.213f + 0.787f * a - 0.213f * b, 0.715f - 0.715f * a - 0.715f * b, 0.072f - 0.072f * a + 0.928f * b, 0f, 0f,
                0.213f - 0.213f * a + 0.143f * b, 0.715f + 0.285f * a + 0.140f * b, 0.072f - 0.072f * a - 0.283f * b, 0f, 0f,
                0.213f - 0.213f * a - 0.787f * b, 0.715f - 0.715f * a + 0.715f * b, 0.072f + 0.928f * a + 0.072f * b, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
    }
}
