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
    private var loudest = -9600f

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
            // mRms is in millibels (about -9600 = silence .. 0 = full). Phones play at very different volumes,
            // so judge against the loudest of the last few seconds: syllables open it fully at any volume,
            // the quiet between words closes it, and only real silence (< -8500) keeps it shut.
            // mRms is in millibels (about -9600 = silence .. 0 = full). Phones play at very different volumes,
            // so judge against the loudest of the last few seconds: syllables open it fully at any volume,
            // the quiet between words closes it, and only real silence (< -8500) keeps it shut.
            val rms = m.mRms.toFloat()
            loudest = if (rms > loudest) rms else loudest - 6f              // slowly forgets old peaks
            if (rms < -8500f) 0f
            else Math.sqrt(((rms - (loudest - 1500f)) / 1500f).coerceIn(0f, 1f).toDouble()).toFloat()
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
 * Talking face: while a voice plays, the alien becomes the frames of the lip-sync video the user liked
 * (assets/talkface/f000…, the whispering part only, no laugh or smirk), lined up with this alien and cut out
 * the same way (brightness → see-through), tinted to the wallpaper's colour. They play in order so the head
 * moves like in the video, but skip ahead to frames whose mouth matches how loud the voice is right now.
 * When the voice stops the frames fade back to the normal face.
 */
class TalkFace(private val assets: android.content.res.AssetManager, asset: String, private val framesDir: String = "talkface") {
    private val count: Int
    private val fps: Float
    private val openOf: FloatArray
    init {
        val meta = org.json.JSONObject(assets.open("$framesDir/talkface.json").bufferedReader().use { it.readText() })
        count = meta.getInt("count"); fps = meta.getDouble("fps").toFloat()
        val arr = meta.getJSONArray("open"); openOf = FloatArray(count) { arr.getDouble(it).toFloat() }
    }
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
        // Frames made from this alien itself are already the right colour.
        if (framesDir == "talkface") Mouth.tint(asset)?.let { colorFilter = android.graphics.ColorMatrixColorFilter(it) }
    }
    private val cache = object : LinkedHashMap<Int, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<Int, Bitmap>?) = size > 10
    }
    // Cheaper phones: if a frame takes too long to load, switch to half-size frames (drawn at full size).
    private var sample = 1
    private var loadMs = 0f
    private var fullW = 0; private var fullH = 0
    private fun frame(i: Int): Bitmap? = cache[i] ?: runCatching {
        val t0 = android.os.SystemClock.uptimeMillis()
        val b = assets.open("$framesDir/f%03d.webp".format(i)).use {
            android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
        }
        if (b != null && sample == 1) { fullW = b.width; fullH = b.height }
        loadMs = loadMs * 0.8f + (android.os.SystemClock.uptimeMillis() - t0) * 0.2f
        if (sample == 1 && loadMs > 20f) { sample = 2; cache.clear() }
        b
    }.getOrNull()?.also { cache[i] = it }
    private val dst = android.graphics.RectF()

    private var pos = 0f; private var dir = 1
    /** 0 = normal face, 1 = video face. */
    var amount = 0f; private set
    private var lastAt = 0L

    /** Advance by the time since the last frame; `open` = how loud the voice is now (0..1). */
    fun update(now: Long, open: Float) {
        val dt = if (lastAt == 0L) 0.033f else ((now - lastAt) / 1000f).coerceIn(0f, 0.1f)
        lastAt = now
        val talking = open > 0.06f
        amount += ((if (talking) 1f else 0f) - amount) * (if (talking) 0.25f else 0.08f)
        if (amount < 0.01f) { amount = 0f; return }
        pos += dir * fps * dt
        if (pos >= count - 1) { pos = (count - 1).toFloat(); dir = -1 }
        if (pos <= 0f) { pos = 0f; dir = 1 }
        // Look a few frames ahead (in the playing direction) for the mouth that best matches the voice.
        val base = Math.round(pos)
        var best = base; var bestErr = Float.MAX_VALUE
        for (k in 0..6) {
            val j = base + dir * k
            if (j !in 0 until count) break
            val err = kotlin.math.abs(openOf[j] - open) + k * 0.03f
            if (err < bestErr) { bestErr = err; best = j }
        }
        if (best != base) pos = best.toFloat()
    }

    /** Draws the current frame in image pixels (same size and place as the alien image). */
    fun draw(c: Canvas) {
        if (amount <= 0f) return
        val f = frame(Math.round(pos).coerceIn(0, count - 1)) ?: return
        paint.alpha = (255 * amount).toInt()
        if (fullW == 0) { fullW = f.width * sample; fullH = f.height * sample }
        dst.set(0f, 0f, fullW.toFloat(), fullH.toFloat())
        c.drawBitmap(f, null, dst, paint)
    }

    fun clear() { cache.clear(); amount = 0f; lastAt = 0L }
}

/** Colour helpers for the talking face. */
object Mouth {
    /** Hue shift from the cyan original to this wallpaper's colour (null = cyan). */
    fun tint(asset: String): android.graphics.ColorMatrix? {
        if (asset.contains("ice") || asset.contains("clear")) return android.graphics.ColorMatrix().apply { setSaturation(0.18f) }
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
