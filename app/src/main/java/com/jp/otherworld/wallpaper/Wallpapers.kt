package com.jp.otherworld.wallpaper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.Surface
import android.view.SurfaceHolder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/**
 * The spinning globe (art/originals/globe_earth.mp4) as a live wallpaper. The whole video is shown
 * (fit), and the space around it is the same video blurred and dimmed, so no bars and no crop.
 */
class GlobeWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = GlobeEngine()

    private inner class GlobeEngine : Engine() {
        private val thread = HandlerThread("globe-gl").apply { start() }
        private val gl = Handler(thread.looper)
        private var renderer: VideoRenderer? = null
        private var player: MediaPlayer? = null

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            gl.post {
                val r = VideoRenderer(holder.surface)
                renderer = r
                val st = r.surfaceTexture
                st.setOnFrameAvailableListener({ r.drawFrame() }, gl)
                player = MediaPlayer().apply {
                    assets.openFd("globe_earth.mp4").use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                    setSurface(Surface(st))
                    isLooping = true
                    setVolume(0f, 0f)
                    prepare()
                    r.videoW = videoWidth; r.videoH = videoHeight
                    if (isVisible) start()
                }
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            gl.post { renderer?.resize(width, height); renderer?.drawFrame() }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            gl.post { player?.let { if (visible) it.start() else if (it.isPlaying) it.pause() } }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            gl.post {
                player?.release(); player = null
                renderer?.release(); renderer = null
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            gl.post { thread.quitSafely() }
        }
    }
}

/** Minimal EGL/GLES2 renderer for a video SurfaceTexture: blurred "cover" background + sharp "fit" foreground. */
private class VideoRenderer(output: Surface) {
    var videoW = 16; var videoH = 9
    private var w = 1; private var h = 1
    private val display: EGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    private val context: EGLContext
    private val surface: EGLSurface
    private val program: Int
    private val tex: Int
    val surfaceTexture: SurfaceTexture
    private val texMatrix = FloatArray(16)
    private val quad = ByteBuffer.allocateDirect(4 * 4 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f, -1f, 0f, 0f, 1f, -1f, 1f, 0f, -1f, 1f, 0f, 1f, 1f, 1f, 1f, 1f)); position(0)
    }

    init {
        val v = IntArray(2); EGL14.eglInitialize(display, v, 0, v, 1)
        val cfg = arrayOfNulls<EGLConfig>(1); val n = IntArray(1)
        EGL14.eglChooseConfig(display, intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_NONE
        ), 0, cfg, 0, 1, n, 0)
        context = EGL14.eglCreateContext(display, cfg[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        surface = EGL14.eglCreateWindowSurface(display, cfg[0], output, intArrayOf(EGL14.EGL_NONE), 0)
        EGL14.eglMakeCurrent(display, surface, surface, context)
        program = link(VERTEX, FRAGMENT)
        val t = IntArray(1); GLES20.glGenTextures(1, t, 0); tex = t[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        surfaceTexture = SurfaceTexture(tex)
        android.opengl.Matrix.setIdentityM(texMatrix, 0)
    }

    fun resize(width: Int, height: Int) { w = width; h = height }

    fun drawFrame() {
        runCatching { surfaceTexture.updateTexImage(); surfaceTexture.getTransformMatrix(texMatrix) }
        GLES20.glViewport(0, 0, w, h)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)
        val sa = w.toFloat() / h; val va = videoW.toFloat() / videoH
        // cover = fills the screen (cropped), fit = whole video visible
        val coverX = if (va > sa) va / sa else 1f; val coverY = if (va > sa) 1f else sa / va
        val fitX = if (va > sa) 1f else va / sa; val fitY = if (va > sa) sa / va else 1f
        draw(coverX, coverY, blur = 1f)
        draw(fitX, fitY, blur = 0f)
        EGL14.eglSwapBuffers(display, surface)
    }

    private fun draw(sx: Float, sy: Float, blur: Float) {
        val pos = GLES20.glGetAttribLocation(program, "aPos")
        val uv = GLES20.glGetAttribLocation(program, "aUv")
        quad.position(0); GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 16, quad); GLES20.glEnableVertexAttribArray(pos)
        quad.position(2); GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 16, quad); GLES20.glEnableVertexAttribArray(uv)
        GLES20.glUniform2f(GLES20.glGetUniformLocation(program, "uScale"), sx, sy)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uBlur"), blur)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uTex"), 1, false, texMatrix, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    fun release() {
        surfaceTexture.release()
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(display, surface)
        EGL14.eglDestroyContext(display, context)
        EGL14.eglTerminate(display)
    }

    private fun link(vs: String, fs: String): Int {
        fun shader(type: Int, src: String) = GLES20.glCreateShader(type).also { GLES20.glShaderSource(it, src); GLES20.glCompileShader(it) }
        return GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, shader(GLES20.GL_VERTEX_SHADER, vs))
            GLES20.glAttachShader(it, shader(GLES20.GL_FRAGMENT_SHADER, fs))
            GLES20.glLinkProgram(it)
        }
    }

    companion object {
        const val VERTEX = """
            attribute vec2 aPos; attribute vec2 aUv;
            uniform vec2 uScale; uniform mat4 uTex;
            varying vec2 vUv;
            void main() { gl_Position = vec4(aPos * uScale, 0.0, 1.0); vUv = (uTex * vec4(aUv, 0.0, 1.0)).xy; }
        """
        const val FRAGMENT = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES sTex; uniform float uBlur;
            varying vec2 vUv;
            void main() {
                if (uBlur < 0.5) { gl_FragColor = texture2D(sTex, vUv); return; }
                vec4 c = vec4(0.0);
                for (int x = -3; x <= 3; x++) for (int y = -3; y <= 3; y++)
                    c += texture2D(sTex, vUv + vec2(float(x), float(y)) * 0.007);
                gl_FragColor = vec4(c.rgb / 49.0 * 0.45, 1.0);
            }
        """
    }
}

/**
 * The alien face as a live wallpaper: the image filling the screen on black, with a slow
 * glow pulse and a gentle float. It leans as you tilt the phone and blinks every 7, 10, then 5 s —
 * the "lids" are the mesh just above each eye sliding down over it (only the image's own pixels).
 */
open class AlienWallpaperService : WallpaperService() {
    open val asset = "alien.png"
    /** Colour of the bright edge on the blinking lids, matched to the face. */
    open val seam = 0xFFAAF0FF.toInt()
    /** The user's brain inside the skull (only the Brain wallpaper). */
    open val withBrain = false
    /** Brain picture, already fitted to this alien's head (face-sized, 2×). */
    open val brainAsset = "alien_brain_layer.webp"
    /** Moves the face down by this share of its height (the brain stays put). */
    open val faceDrop = 0f
    /** How strongly the brain shows (1 = full; lower = more see-through). */
    open val brainStrength = 1f
    /** The brain picture is fitted in the face's own coordinates, so it moves down with the face. */
    open val brainWithFace = false
    /** How strong the soft halo around the head is (1 = normal). */
    open val haloStrength = 1f
    override fun onCreateEngine(): Engine = AlienEngine()

    private inner class AlienEngine : Engine(), android.hardware.SensorEventListener {
        private val handler = Handler(android.os.Looper.getMainLooper())
        private val alien: Bitmap = assets.open(asset).use { BitmapFactory.decodeStream(it) }
        // A soft halo made from the same image: shrink then enlarge = blur.
        private val glow: Bitmap = Bitmap.createScaledBitmap(
            Bitmap.createScaledBitmap(alien, alien.width / 12, alien.height / 12, true), alien.width, alien.height, true
        )
        private val eyes = EyeLids(alien, seam)
        // Talk along (test): lips move with the phone's sound.
        private val talkFace = TalkFace(assets, asset)
        private val brain: Bitmap? = if (!withBrain) null else runCatching { assets.open(brainAsset).use { BitmapFactory.decodeStream(it) } }.getOrNull()
        private val brainPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { blendMode = android.graphics.BlendMode.SCREEN; alpha = (255 * brainStrength).toInt() }
        private val brainRect = RectF()
        private val talk = TalkListener()
        // Breaking apart for the hidden drawer, built the first time each style is used.
        private val shatters = HashMap<ShatterStyle, Shatter>()
        private fun shatter(style: ShatterStyle) = shatters.getOrPut(style) { Shatter(alien, style, eyes.centers) }
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val glowPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { blendMode = android.graphics.BlendMode.SCREEN }
        // Notification pulse: the halo re-coloured (its brightness times the colour), screened on top.
        private val pulsePaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { blendMode = android.graphics.BlendMode.SCREEN }
        private var pulseTint = 0
        // Charging: a bright band of the halo sweeping up the face.
        private val sweepLayer = Paint().apply { blendMode = android.graphics.BlendMode.SCREEN }
        private val sweepMask = Paint().apply { blendMode = android.graphics.BlendMode.DST_IN }
        private val dimPaint = Paint()
        // Fades the still face out under the talking face (same shape, drawn as black).
        private val maskPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix(floatArrayOf(
                0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
        }

        init { com.jp.otherworld.data.LauncherStore.init(applicationContext) }

        // Leaning toward the finger on home (pixels, eased).
        private var leanX = 0f; private var leanY = 0f

        // Battery: 0..1 and whether it's charging.
        private var battery = 1f; private var charging = false
        private val batteryWatch = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context, i: android.content.Intent) {
                val level = i.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                val scale = i.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
                if (level >= 0 && scale > 0) battery = level / scale.toFloat()
                charging = i.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) != 0
            }
        }
        private var visible = false
        // Battery saver: Android's saver is on, or under 20% and not charging → half the frame rate, no talk along.
        private val power = getSystemService(android.os.PowerManager::class.java)
        private fun saving() = power?.isPowerSaveMode == true || (battery < 0.2f && !charging)
        private val start = SystemClock.uptimeMillis()
        private val frame = object : Runnable {
            override fun run() { draw(); if (visible) handler.postDelayed(this, if (saving()) 66 else 33) }
        }

        // Tilt: gravity, smoothed fast (what you see) and slow (the resting pose), so holding the
        // phone at any angle settles back to centre and only a tilt moves the face.
        private val sensors = getSystemService(android.hardware.SensorManager::class.java)
        private val gravity = sensors?.getDefaultSensor(android.hardware.Sensor.TYPE_GRAVITY)
            ?: sensors?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
        private var gx = 0f; private var gy = 0f; private var restX = 0f; private var restY = 0f
        private var haveRest = false

        // Blink schedule: gaps of 7 s, 10 s, 5 s, repeating; each blink ~300 ms.
        private val gaps = longArrayOf(7000, 10000, 5000)
        private var gap = 0
        private var nextBlink = SystemClock.uptimeMillis() + gaps[0]
        private var blinkStart = -1L

        override fun onSensorChanged(e: android.hardware.SensorEvent) {
            // Sensor axes are the phone's natural orientation; turn them to match the screen.
            val sx = e.values[0]; val sy = e.values[1]
            val rot = runCatching { displayContext?.display?.rotation }.getOrNull() ?: android.view.Surface.ROTATION_0
            val (x, y) = when (rot) {
                android.view.Surface.ROTATION_90 -> -sy to sx
                android.view.Surface.ROTATION_180 -> -sx to -sy
                android.view.Surface.ROTATION_270 -> sy to -sx
                else -> sx to sy
            }
            if (!haveRest) { restX = x; restY = y; gx = x; gy = y; haveRest = true }
            gx += (x - gx) * 0.2f; gy += (y - gy) * 0.2f
            // ~15 s to settle back to centre if you keep holding it tilted
            restX += (x - restX) * 0.0013f; restY += (y - restY) * 0.0013f
        }

        override fun onAccuracyChanged(s: android.hardware.Sensor?, a: Int) {}

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            if (!isPreview) com.jp.otherworld.data.AlienSignals.alienVisible = v
            handler.removeCallbacks(frame)
            if (v) {
                gravity?.let { sensors?.registerListener(this, it, android.hardware.SensorManager.SENSOR_DELAY_GAME) }
                runCatching { registerReceiver(batteryWatch, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)) }
                handler.post(frame)
            } else {
                sensors?.unregisterListener(this)
                runCatching { unregisterReceiver(batteryWatch) }
                haveRest = false
                talk.stop(); talkFace.clear()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = draw()

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false; handler.removeCallbacks(frame); sensors?.unregisterListener(this); talk.stop()
            runCatching { unregisterReceiver(batteryWatch) }
        }

        /** 0 = day, 1 = full night; fades over 10 minutes at each end. */
        private fun nightAmount(): Float {
            val s = com.jp.otherworld.data.LauncherStore.state.value
            if (!s.nightOn || s.nightStart == s.nightEnd) return 0f
            val cal = java.util.Calendar.getInstance()
            val m = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE) + cal.get(java.util.Calendar.SECOND) / 60f
            val len = ((s.nightEnd - s.nightStart + 1440) % 1440).toFloat()
            val since = ((m - s.nightStart) % 1440 + 1440) % 1440
            if (since >= len) return 0f
            return (minOf(since, len - since) / 10f).coerceIn(0f, 1f)
        }

        /** Notification pulse: two soft beats over 3 s. */
        private fun pulseAmount(now: Long): Float {
            val start = com.jp.otherworld.data.AlienSignals.pulseStart
            if (start < 0) return 0f
            val t = (now - start) / 1000f
            if (t !in 0f..3f) return 0f
            val s = sin(PI * (t % 1.5f) / 1.5).toFloat()
            return s * s
        }

        /** Rebuilds the pulse filter when the colour changes: halo brightness × colour, boosted. */
        private fun tintFor(color: Int) {
            if (color == pulseTint) return
            pulseTint = color
            val r = Color.red(color) / 255f * 1.6f; val g = Color.green(color) / 255f * 1.6f; val b = Color.blue(color) / 255f * 1.6f
            pulsePaint.colorFilter = android.graphics.ColorMatrixColorFilter(floatArrayOf(
                0.3f * r, 0.59f * r, 0.11f * r, 0f, 0f,
                0.3f * g, 0.59f * g, 0.11f * g, 0f, 0f,
                0.3f * b, 0.59f * b, 0.11f * b, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
        }

        /** 0 = open, 1 = shut. */
        private fun blinkAmount(now: Long): Float {
            if (blinkStart < 0 && now >= nextBlink) blinkStart = now
            if (blinkStart < 0) return 0f
            val ms = now - blinkStart
            val v = when {
                ms < 110 -> ms / 110f
                ms < 170 -> 1f
                ms < 290 -> 1f - (ms - 170) / 120f
                else -> { blinkStart = -1; gap = (gap + 1) % gaps.size; nextBlink = now + gaps[gap]; 0f }
            }
            return v.coerceIn(0f, 1f)
        }

        private fun draw() {
            val holder = surfaceHolder
            val c = runCatching { holder.lockHardwareCanvas() }.getOrNull() ?: return
            try {
                c.drawColor(Color.BLACK)
                val now = SystemClock.uptimeMillis()
                val t = (now - start) / 1000.0
                val float = (sin(t * 2 * PI / 6.0) * 14).toFloat()            // 6 s float
                val pulse = (0.5 + 0.5 * sin(t * 2 * PI / 3.0)).toFloat()      // 3 s glow
                // Lean with the tilt: ~5% of the screen per m/s² of tilt, at most ~6% of the width.
                val reach = c.width * 0.06f
                val tiltX = (-(gx - restX) * c.width * 0.012f).coerceIn(-reach, reach)
                val tiltY = ((gy - restY) * c.width * 0.012f).coerceIn(-reach, reach)
                // Lean toward a finger on home (up to ~4% of the width), easing back after it lifts.
                val sig = com.jp.otherworld.data.AlienSignals
                val leaning = sig.touching || now - sig.releasedAt < 1000
                val leanReach = c.width * 0.04f
                val wantX = if (leaning) (sig.touchX - 0.5f) * 2f * leanReach else 0f
                val wantY = if (leaning) (sig.touchY - 0.5f) * 2f * leanReach else 0f
                leanX += (wantX - leanX) * 0.12f; leanY += (wantY - leanY) * 0.12f
                // Talk along: how open the mouth is now, and a slower "is talking" level for head movement.
                val talkMode = com.jp.otherworld.data.LauncherStore.state.value.talkMode
                val talkOpen = if (talkMode != "OFF" && !saving() && talk.start(applicationContext)) talk.open(talkMode == "ANY") else { talk.stop(); 0f }
                // While a voice plays, the face becomes the lip-sync video (its head movement and mouth).
                talkFace.update(now, talkOpen)
                val moveX = tiltX + leanX; val moveY = tiltY + leanY
                // Battery mood: full neon above 50%, fading to dim at 15% and below.
                val mood = if (battery >= 0.5f) 1f else 0.25f + 0.75f * ((battery - 0.15f) / 0.35f).coerceIn(0f, 1f)
                val night = nightAmount()
                // Fills the screen, but on narrow phones shrinks so the whole face and both eyes show (see alienPlace).
                val place = alienPlace(c.width.toFloat(), c.height.toFloat(), alien.width, alien.height)
                val sc = place.width() / alien.width
                val aw = place.width(); val ah = place.height()
                val drop = place.height() * faceDrop
                val l = place.left + moveX; val top = place.top + float + moveY + drop
                val g = 1.12f
                // Hidden drawer: 0 = whole face, 1 = broken apart and gone.
                val gone = com.jp.otherworld.data.AlienSignals.shatterProgress(now)
                glowPaint.alpha = ((90 + 120 * pulse) * haloStrength * mood * (1f - gone)).toInt()
                // The halo lags the face slightly, which reads as depth when tilting.
                val hl = l - moveX * 0.4f; val ht = top - moveY * 0.4f
                val haloRect = RectF(hl - aw * (g - 1) / 2, ht - ah * (g - 1) / 2, hl + aw * (1 + (g - 1) / 2), ht + ah * (1 + (g - 1) / 2))
                if (glowPaint.alpha > 0) c.drawBitmap(glow, null, haloRect, glowPaint)
                if (gone > 0f) {
                    val style = runCatching { ShatterStyle.valueOf(com.jp.otherworld.data.AlienSignals.shatterStyle) }.getOrDefault(ShatterStyle.DUST)
                    if (gone < 1f) shatter(style).draw(c, gone, l, top, sc, c.width.toFloat(), now)
                    if (night > 0.01f) { dimPaint.color = Color.argb((255 * night * 0.4f).toInt(), 0, 0, 0); c.drawRect(0f, 0f, c.width.toFloat(), c.height.toFloat(), dimPaint) }
                    return
                }
                val save = c.save()
                c.translate(l, top); c.scale(sc, sc)
                c.drawBitmap(alien, 0f, 0f, paint)
                // At night the eyes rest half shut, and blinks close them the rest of the way.
                val blink = blinkAmount(now)
                eyes.draw(c, 0f, 0f, night * 0.5f + blink * (1f - night * 0.5f), paint)
                if (talkFace.amount > 0f) {
                    // Cover the still face with black (its eyelids too), then the video face on top.
                    c.drawBitmap(alien, 0f, 0f, maskPaint.also { it.alpha = (255 * talkFace.amount).toInt() })
                    talkFace.draw(c)
                }
                // The user's glass brain filling the skull (already cut to the head's shape, eyes clear, fading
                // out below the eyes; same size as the face at 2×). Screened, so it glows through the mesh
                // exactly as if it sat behind it (screen works the same in either order).
                brain?.let { b ->
                    // Same place as before the face moved down: undo the drop for the brain.
                    val up = if (brainWithFace) 0f else alien.height * faceDrop
                    brainRect.set(0f, -up, alien.width.toFloat(), alien.height - up)
                    c.drawBitmap(b, null, brainRect, brainPaint)
                }
                c.restoreToCount(save)
                // Charging: a bright band of the halo sweeps up the face every 2.5 s.
                if (charging) {
                    val p = (now % 2500) / 2500f
                    val faceTop = top; val faceBottom = top + ah
                    val y = faceBottom - (faceBottom - faceTop + ah * 0.3f) * p
                    val band = ah * 0.18f
                    // The face's own mesh, brightened inside a soft band (the face can fill the screen, so not the halo).
                    val faceRect = RectF(l, top, l + aw, top + ah)
                    val layer = c.saveLayer(faceRect, sweepLayer)
                    c.drawBitmap(alien, null, faceRect, paint)
                    sweepMask.shader = android.graphics.LinearGradient(0f, y - band, 0f, y + band,
                        intArrayOf(0, Color.WHITE, 0), null, android.graphics.Shader.TileMode.CLAMP)
                    c.drawRect(faceRect, sweepMask)
                    c.restoreToCount(layer)
                }
                // Notification: the halo pulses in the notification's colour.
                val beat = pulseAmount(now)
                if (beat > 0.01f) {
                    tintFor(sig.pulseColor)
                    pulsePaint.alpha = (255 * beat).toInt()
                    // The halo and the face's mesh both light up in the colour.
                    val pg = 1.22f
                    c.drawBitmap(glow, null, RectF(hl - aw * (pg - 1) / 2, ht - ah * (pg - 1) / 2, hl + aw * (1 + (pg - 1) / 2), ht + ah * (1 + (pg - 1) / 2)), pulsePaint)
                    pulsePaint.alpha = (200 * beat).toInt()
                    c.drawBitmap(alien, null, RectF(l, top, l + aw, top + ah), pulsePaint)
                }
                // Low battery dims the face a little; night dims everything.
                val dim = (1f - mood) * 0.25f + night * 0.4f
                if (dim > 0.01f) { dimPaint.color = Color.argb((255 * dim.coerceAtMost(0.7f)).toInt(), 0, 0, 0); c.drawRect(0f, 0f, c.width.toFloat(), c.height.toFloat(), dimPaint) }
            } finally {
                holder.unlockCanvasAndPost(c)
            }
        }
    }
}

/**
 * Eyelids for the alien, made from its own mesh: for each eye hole, the strip of face just above it
 * is slid down over the hole (clipped to the hole's shape), with a bright seam at the lid's edge.
 * Lid images for a few closing steps are prepared once.
 */
private class EyeLids(src: Bitmap, seam: Int) {
    private class Eye(val x: Int, val y: Int, val frames: List<Bitmap>)
    private val eyes: List<Eye>
    private val steps = 8
    /** Middle of each eye hole, in picture pixels. */
    val centers: List<Pair<Float, Float>> get() = eyes.map { it.x + it.frames[0].width / 2f to it.y + it.frames[0].height / 2f }

    init {
        val w = src.width; val h = src.height
        val px = IntArray(w * h).also { src.getPixels(it, 0, w, 0, 0, w, h) }
        fun dark(i: Int): Boolean { val p = px[i]; return maxOf((p shr 16) and 255, (p shr 8) and 255, p and 255) < 25 }
        val seen = BooleanArray(w * h)
        val found = ArrayList<Eye>()
        // The two big dark holes in the middle band of the face are the eyes.
        for (sy in h / 4 until h * 7 / 8) for (sx in w / 20 until w - w / 20) {
            val i0 = sy * w + sx
            if (seen[i0] || !dark(i0)) continue
            val q = ArrayDeque<Int>(); q.add(i0); seen[i0] = true
            val members = ArrayList<Int>()
            var minX = sx; var maxX = sx; var minY = sy; var maxY = sy; var touchesEdge = false
            while (q.isNotEmpty()) {
                val i = q.removeFirst(); members.add(i)
                val x = i % w; val y = i / w
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) touchesEdge = true
                minX = minOf(minX, x); maxX = maxOf(maxX, x); minY = minOf(minY, y); maxY = maxOf(maxY, y)
                for (n in intArrayOf(i - 1, i + 1, i - w, i + w)) if (n in 0 until w * h && !seen[n] && dark(n)) { seen[n] = true; q.add(n) }
            }
            if (touchesEdge || members.size < 2000 || minY < h / 4 || maxY > h * 7 / 8) continue
            // Hole mask, grown by 2 px so the lid covers the hole's soft rim.
            val bw = maxX - minX + 5; val bh = maxY - minY + 5; val ox = minX - 2; val oy = minY - 2
            val mask = BooleanArray(bw * bh)
            for (i in members) { val x = i % w - ox; val y = i / w - oy
                for (dy in -2..2) for (dx in -2..2) { val xx = x + dx; val yy = y + dy
                    if (xx in 0 until bw && yy in 0 until bh) mask[yy * bw + xx] = true } }
            val frames = (1..steps).map { s ->
                val cover = (bh * s / steps.toFloat()).toInt().coerceAtLeast(1)
                val out = IntArray(bw * bh)
                for (y in 0 until cover) for (x in 0 until bw) {
                    if (!mask[y * bw + x]) continue
                    val sxp = ox + x; val syp = oy - cover + y   // the mesh just above the eye
                    out[y * bw + x] = if (sxp in 0 until w && syp in 0 until h) px[syp * w + sxp] or (0xFF shl 24) else 0xFF000000.toInt()
                }
                if (cover < bh) for (x in 0 until bw) if (mask[(cover - 1) * bw + x]) out[(cover - 1) * bw + x] = seam
                Bitmap.createBitmap(out, bw, bh, Bitmap.Config.ARGB_8888)
            }
            found.add(Eye(ox, oy, frames))
        }
        eyes = found.sortedByDescending { it.frames.first().width * it.frames.first().height }.take(2)
    }

    fun draw(c: android.graphics.Canvas, left: Float, top: Float, amount: Float, paint: Paint) {
        if (amount <= 0.01f) return
        val step = (amount * steps).toInt().coerceIn(1, steps) - 1
        for (e in eyes) c.drawBitmap(e.frames[step], left + e.x, top + e.y, paint)
    }
}

/** The neon electric green copy (art/derived/alien_green.png). */
class AlienGreenWallpaperService : AlienWallpaperService() {
    override val asset = "alien_green.png"
    override val seam = 0xFFB8FFB0.toInt()
}

/** The user's glass alien (art/originals/alien_glass.png, lined up by the eyes) with their glass brain inside. */
class AlienClearWallpaperService : AlienWallpaperService() {
    override val asset = "alien_clear.png"
    override val seam = 0xFFE8FBFF.toInt()
    override val withBrain = true
    override val brainAsset = "alien_clear_brain_layer.webp"
    override val faceDrop = CLEAR_FACE_DROP
    override val brainStrength = 0.6f
    override val brainWithFace = true
    override val haloStrength = 0.25f
}

/** White (ice) alien with the user's brain (art/originals/brain.svg) glowing inside the skull. */
class AlienBrainWallpaperService : AlienWallpaperService() {
    override val asset = "alien_ice.png"
    override val seam = 0xFFFFFFFF.toInt()
    override val withBrain = true
}

/** The electric neon purple copy (art/derived/alien_purple.png). */
class AlienPurpleWallpaperService : AlienWallpaperService() {
    override val asset = "alien_purple.png"
    override val seam = 0xFFE0B8FF.toInt()
}

/** The glowing neon pink copy (art/derived/alien_pink.png). */
class AlienPinkWallpaperService : AlienWallpaperService() {
    override val asset = "alien_pink.png"
    override val seam = 0xFFFFB0EE.toInt()
}

/**
 * Where the alien's two eye holes are, as fractions of the image (left eye first). All four colours
 * share the same shape, so alien.png answers for them all. Same rule as [EyeLids], on a half-size copy.
 */
fun alienEyeSpots(context: android.content.Context): List<RectF> {
    val src = context.assets.open("alien.png").use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 2 }) }
        ?: return emptyList()
    val w = src.width; val h = src.height
    val px = IntArray(w * h).also { src.getPixels(it, 0, w, 0, 0, w, h) }
    fun dark(i: Int): Boolean { val p = px[i]; return maxOf((p shr 16) and 255, (p shr 8) and 255, p and 255) < 25 }
    val seen = BooleanArray(w * h)
    val found = ArrayList<Pair<Int, RectF>>()
    for (sy in h / 4 until h * 7 / 8) for (sx in w / 20 until w - w / 20) {
        val i0 = sy * w + sx
        if (seen[i0] || !dark(i0)) continue
        val q = ArrayDeque<Int>(); q.add(i0); seen[i0] = true
        var count = 0; var minX = sx; var maxX = sx; var minY = sy; var maxY = sy; var touchesEdge = false
        while (q.isNotEmpty()) {
            val i = q.removeFirst(); count++
            val x = i % w; val y = i / w
            if (x == 0 || y == 0 || x == w - 1 || y == h - 1) touchesEdge = true
            minX = minOf(minX, x); maxX = maxOf(maxX, x); minY = minOf(minY, y); maxY = maxOf(maxY, y)
            for (n in intArrayOf(i - 1, i + 1, i - w, i + w)) if (n in 0 until w * h && !seen[n] && dark(n)) { seen[n] = true; q.add(n) }
        }
        if (touchesEdge || count < 2000 / 4 || minY < h / 4 || maxY > h * 7 / 8) continue
        found.add(count to RectF(minX / w.toFloat(), minY / h.toFloat(), (maxX + 1) / w.toFloat(), (maxY + 1) / h.toFloat()))
    }
    return found.sortedByDescending { it.first }.take(2).map { it.second }.sortedBy { it.left }
}

/**
 * Where the alien image goes on a w×h screen (before float/tilt/lean). Normally it covers the screen like a
 * still wallpaper; on narrow phones that would cut the eyes in half, so it shrinks until ~85% of the face's
 * width shows, with the eyes a little above the middle and black below. Home's eye spots use this too.
 */
fun alienPlace(w: Float, h: Float, iw: Int, ih: Int): RectF {
    val cover = maxOf(w / iw, h / ih)
    val sc = minOf(cover, w / (ALIEN_KEEP_W * iw))
    val aw = iw * sc; val ah = ih * sc
    var t = if (sc < cover) h * 0.45f - ALIEN_EYES_Y * ah else (h - ah) / 2f
    t = t.coerceIn(minOf(0f, h - ah), maxOf(0f, h - ah))
    val l = (w - aw) / 2f
    return RectF(l, t, l + aw, t + ah)
}
private const val ALIEN_KEEP_W = 0.85f   // share of the face's width always on screen
private const val ALIEN_EYES_Y = 0.58f   // eye line, as a fraction of the image height

const val CLEAR_FACE_DROP = 0.08f

/** How far the set alien wallpaper's face is moved down (share of its height), for placing its eyes. */
fun alienFaceDrop(context: android.content.Context): Float = runCatching {
    if (android.app.WallpaperManager.getInstance(context).wallpaperInfo?.serviceName?.endsWith("AlienClearWallpaperService") == true) CLEAR_FACE_DROP else 0f
}.getOrDefault(0f)

/** Alien image size (width, height) — needed to place the eyes on screen the way the wallpaper does. */
fun alienSize(context: android.content.Context): Pair<Int, Int> {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.assets.open("alien.png").use { BitmapFactory.decodeStream(it, null, o) }
    return o.outWidth to o.outHeight
}

/** Red copy (art/derived/alien_red.png). */
class AlienRedWallpaperService : AlienWallpaperService() {
    override val asset = "alien_red.png"
    override val seam = 0xFFFFB8B0.toInt()
}

/** Gold copy (art/derived/alien_gold.png). */
class AlienGoldWallpaperService : AlienWallpaperService() {
    override val asset = "alien_gold.png"
    override val seam = 0xFFFFE0A0.toInt()
}

/** Ice copy (art/derived/alien_ice.png). */
class AlienIceWallpaperService : AlienWallpaperService() {
    override val asset = "alien_ice.png"
    override val seam = 0xFFFFFFFF.toInt()
}

/** Blue copy (art/derived/alien_blue.png). */
class AlienBlueWallpaperService : AlienWallpaperService() {
    override val asset = "alien_blue.png"
    override val seam = 0xFFB0C8FF.toInt()
}
