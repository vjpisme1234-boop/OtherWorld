package com.jp.otherworld.ui.wheel

import android.graphics.BlendMode
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ComposeShader
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.text.TextPaint
import android.text.TextUtils
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.jp.otherworld.data.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt

sealed class Panel {
    /** `dot` = the app has unread notifications. */
    data class App(val app: AppInfo, val icon: Bitmap, val dot: Boolean = false) : Panel()
    data class Drawer(val preview: List<Bitmap>) : Panel()
    /** The last apps opened, newest first. */
    data class Recent(val preview: List<Bitmap>) : Panel()
}

private const val STEP = 30f            // 12 slabs around the ring, as in the art
// Spin feel: drag moves the ring a bit more than the finger, flicks throw it harder, and it
// coasts on an exponential glide (rate COAST per second) that lands exactly on a panel.
private const val DRAG_GAIN = 1.25f
private const val FLING_GAIN = 1.6f
private const val COAST = 2.4f
// Opening a panel: how many half-turns it flips while growing (odd, so it ends on its back face).
private const val OPEN_HALF_TURNS = 5
private const val OPEN_MS = 760
// Panel content is laid out in a front slab's own pixel size, then warped onto its slab.
private const val PW = 300f
private const val PH = 640f

/**
 * The user's 12-slab glass ring. Every slab turns with the spin; panels ride the front half and the
 * wheel always settles with one panel centred in front. Tap a panel to flip it open to full screen, long-press gives the panel's app menu.
 */
@Composable
fun GlassWheel(
    art: WheelArt, panels: List<Panel>, active: Boolean,
    jumpTo: Pair<Int, Long>? = null,      // (panel index, request id) — e.g. from the A–Z bar
    ringKey: Any? = null,                 // changes when the user switches rings: the wheel spins the new set in
    center: String = "MORPH",             // middle of the ring: MORPH (Earth → smoke → Mars), EARTH, MARS or NONE
    onLongPress: (Panel?) -> Unit, onOpen: (Panel) -> Unit
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val n = panels.size

    // Spin position in panels: panel j sits at angle (j - pos) * STEP. Integer = one panel centred.
    var pos by remember { mutableFloatStateOf(0f) }
    var spinJob by remember { mutableStateOf<Job?>(null) }

    var opening by remember { mutableStateOf<Int?>(null) }
    var openFrom by remember { mutableStateOf<FloatArray?>(null) }
    val openT = remember { Animatable(0f) }

    fun resetOpen() {
        opening = null
        scope.launch { openT.snapTo(0f) }
    }
    LaunchedEffect(active) { if (active) resetOpen() }
    val isActive by rememberUpdatedState(active)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME && isActive) resetOpen() }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    // Haptic tick as each panel passes the front.
    LaunchedEffect(Unit) {
        snapshotFlow { pos.roundToInt() }
            .distinctUntilChanged()
            .drop(1)
            .collect { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); com.jp.otherworld.data.Sounds.play("tick") }
    }

    val paints = remember { Paints() }

    // The spinning globe in the middle of the ring: frames cut from the user's globe video,
    // decoded off the main thread at the video's 30 fps while the wheel is showing.
    val context = view.context
    var globe by remember { mutableStateOf<Bitmap?>(null) }
    // How far into the Earth → Mars morph we are (seconds of its frames, + when that frame went up), for the
    // smoke rising out of the top of the globe: tied to the frames so it stays in step on slow phones too.
    var morphT by remember { mutableStateOf(-1f) }
    var morphFrameAt by remember { mutableStateOf(0L) }
    val puffs = remember { (0..2).mapNotNull { k -> runCatching { context.assets.open("smoke_puff_$k.webp").use { BitmapFactory.decodeStream(it) } }.getOrNull() } }
    // MORPH: each time the wheel opens the Earth goes up in smoke and becomes Mars, which then spins.
    LaunchedEffect(active, center) {
        if (!active || center == "NONE") { globe = null; return@LaunchedEffect }
        fun frames(dir: String) = context.assets.list(dir)?.sorted().orEmpty().map { "$dir/$it" }
        val (intro, loop) = withContext(Dispatchers.IO) {
            val mars = frames("mars")
            when {
                center == "EARTH" || mars.isEmpty() -> emptyList<String>() to frames("globe")
                center == "MARS" -> emptyList<String>() to mars
                // Earth spins ~2 s first (globe frames 0–59, same side as the morph starts on); the morph frames
                // carry on that spin under the smoke (baked: tools/smoke_over_spin.py, 60-frame head start).
                else -> (frames("globe").take(60) + frames("morph")) to mars
            }
        }
        if (loop.isEmpty()) return@LaunchedEffect
        val buffers = arrayOfNulls<Bitmap>(3)
        var k = 0
        val firstMorph = intro.indexOfFirst { it.startsWith("morph/") }
        // Frames run on the clock: the Earth intro at 30 fps, the morph and Mars at 24. A slow phone skips
        // frames instead of playing in slow motion, so the boom and the rising smoke stay on time.
        val introMs = intro.indexOfFirst { !it.startsWith("globe/") }.let { if (it < 0) intro.size else it } * 33.33f
        val restMs = if (intro.isEmpty() && loop.first().startsWith("globe/")) 33.33f else 41.67f   // Earth alone: 30 fps
        fun frameAt(ms: Float): Int =                                   // absolute frame number (loop not wrapped)
            if (ms < introMs) (ms / 33.33f).toInt() else (introMs / 33.33f).toInt() + ((ms - introMs) / restMs).toInt()
        val started = System.nanoTime()
        var last = -1
        var boomed = false
        // Cheaper phones: if loading frames can't keep up, switch to half-size frames (about 4× faster to load).
        var sample = 1
        var loadMs = 0f
        while (true) {
            val t0 = System.nanoTime()
            val p = maxOf(frameAt((t0 - started) / 1e6f), last + 1)
            val i = if (p < intro.size) p else intro.size + (p - intro.size) % loop.size
            val name = if (i < intro.size) intro[i] else loop[i - intro.size]
            val slot = k++ % 3
            val reuse = buffers[slot]
            val bmp = withContext(Dispatchers.Default) {
                context.assets.open(name).use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inMutable = true; inBitmap = reuse; inSampleSize = sample })
                }
            } ?: break
            loadMs = loadMs * 0.8f + (System.nanoTime() - t0) / 1e6f * 0.2f
            if (sample == 1 && k > 8 && loadMs > 28f) { sample = 2; buffers.fill(null) }
            buffers[slot] = bmp
            globe = bmp
            last = p
            // Seconds into the morph (on into Mars, so the last puffs can finish rising).
            if (firstMorph >= 0 && p >= firstMorph) {
                val since = p - firstMorph
                // The Earth bursts into smoke ~1.4 s into the morph: a low boom.
                if (!boomed && since >= 34) { boomed = true; if (since < 60) com.jp.otherworld.data.Sounds.play("boom") }
                if (since < 400) { morphT = since / 24f; morphFrameAt = System.nanoTime() }
            }
            // Wait for the next frame's time (at least a moment, so drawing gets a turn).
            val next = if ((p + 1) * 33.33f < introMs || restMs < 40f) 33L else 41L
            delay(maxOf(4L, next - (System.nanoTime() - t0) / 1_000_000))
        }
    }
    val ring = remember(art) { Ring(art) }
    var fit by remember { mutableStateOf(Fit(1f, 0f, 0f, RectF())) }

    fun spinTo(target: Float, velocity: Float = 0f) {
        spinJob?.cancel()
        spinJob = scope.launch {
            AnimationState(initialValue = pos, initialVelocity = velocity)
                .animateTo(target, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) { pos = value }
        }
    }

    // A critically damped spring with ω = COAST moves exactly like an exponential glide that
    // starts at the flick's speed, so the coast is seamless and still stops dead centre.
    fun coast(velocity: Float) {
        spinJob?.cancel()
        val target = (pos + velocity / COAST).roundToInt().toFloat()
        spinJob = scope.launch {
            AnimationState(initialValue = pos, initialVelocity = velocity)
                .animateTo(target, spring(dampingRatio = 1f, stiffness = COAST * COAST)) { pos = value }
        }
    }

    // Spin to a requested panel by the shortest way round, quickly, so sliding the A–Z bar feels live.
    LaunchedEffect(jumpTo) {
        val (index, _) = jumpTo ?: return@LaunchedEffect
        if (n == 0 || opening != null) return@LaunchedEffect
        val target = index + n * ((pos - index) / n).roundToInt()
        spinJob?.cancel()
        spinJob = scope.launch {
            AnimationState(initialValue = pos).animateTo(target.toFloat(), spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium)) { pos = value }
        }
    }

    // New ring: spin the new panels in from half a turn back, landing on the first one.
    var firstRing by remember { mutableStateOf(true) }
    LaunchedEffect(ringKey) {
        if (firstRing) { firstRing = false; return@LaunchedEffect }
        com.jp.otherworld.data.Sounds.play("ring")
        spinJob?.cancel()
        pos = -6f
        spinJob = scope.launch {
            AnimationState(initialValue = pos).animateTo(0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow)) { pos = value }
        }
    }

    fun open(j: Int) {
        if (opening != null || n == 0) return
        com.jp.otherworld.data.Sounds.play("open")
        spinJob?.cancel()
        openFrom = fit.toScreen(ring.quadAt((j - pos) * STEP))
        opening = j
        scope.launch {
            openT.snapTo(0f)
            openT.animateTo(1f, tween(OPEN_MS, easing = FastOutSlowInEasing))
            onOpen(panels[Math.floorMod(j, n)])
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .draggable(
                orientation = Orientation.Horizontal,
                enabled = opening == null,
                state = rememberDraggableState { dx -> pos -= dx * DRAG_GAIN / fit.spacing(ring) },
                onDragStarted = { spinJob?.cancel() },
                onDragStopped = { vx ->
                    coast(-vx * FLING_GAIN / fit.spacing(ring))
                }
            )
            .pointerInput(n) {
                fun hit(o: Offset): Int? = ring.slabs(pos)
                    .filter { cos(Math.toRadians(it.theta.toDouble())) > 0.3 }
                    .reversed()
                    .firstOrNull { inQuad(fit.toScreen(ring.quadAt(it.theta)), o.x, o.y) }?.j
                detectTapGestures(
                    // One tap opens the panel (no double-tap wait).
                    onTap = { o -> hit(o)?.let { open(it) } },
                    onLongPress = { o -> if (opening == null) onLongPress(hit(o)?.let { panels[Math.floorMod(it, n)] }) }
                )
            }
    ) {
        fit = Fit.of(art, size.width, size.height)
        drawIntoCanvas { c ->
            val canvas = c.nativeCanvas
            canvas.drawBitmap(art.floor, null, fit.floor, paints.bitmap)
            val slabs = ring.slabs(pos)
            // Floor reflections first, then the slabs back to front.
            for (s in slabs) if (s.j != opening) drawReflection(canvas, ring, fit, paints, s.theta)
            var globeDrawn = false
            for (s in slabs) {
                // The globe stands inside the ring: after the far slabs, before the near ones.
                if (!globeDrawn && cos(Math.toRadians(s.theta.toDouble())) >= 0.0) {
                    globe?.let { drawGlobe(canvas, it, fit, paints) }
                    if (morphT in 0f..20f) drawRisingSmoke(canvas, puffs, fit, paints, morphT + minOf((System.nanoTime() - morphFrameAt) / 1e9f, 0.042f))
                    globeDrawn = true
                }
                if (s.j == opening) continue
                val panel = if (n > 0) panels[Math.floorMod(s.j, n)] else null
                drawSlab(canvas, ring, fit, paints, s.theta, panel)
            }
            // …and seen through the clear glass in front of it.
            globe?.let { drawGlobe(canvas, it, fit, paints, throughGlass = true) }
            val from = openFrom
            val o = opening
            if (o != null && from != null) {
                drawOpening(canvas, ring.front, from, size.width, size.height, openT.value, panels[Math.floorMod(o, n)], paints)
            }
        }
    }
}

// ── The ring ─────────────────────────────────────────────────────────────────────

private class SlabAt(val j: Int, val theta: Float)

/** Slab outlines all the way round, smoothly interpolated between the 12 measured keyframes. */
private class Ring(art: WheelArt) {
    val keys = art.ring
    val front: WheelArt.Slab = art.slabs.getValue("C")

    /** The 12 slabs for spin `pos`, back to front. */
    fun slabs(pos: Float): List<SlabAt> {
        val first = ceil(pos - 6f).toInt()
        return (first until first + 12)
            .map { SlabAt(it, wrap((it - pos) * STEP)) }
            .sortedBy { cos(Math.toRadians(it.theta.toDouble())) }
    }

    /** Catmull-Rom through the keyframes, so slabs glide without kinks. */
    fun quadAt(theta: Float): FloatArray {
        val u = (wrap(theta) - keys[0].theta) / STEP
        val i = floor(u).toInt()
        val t = u - i
        val p0 = keys[Math.floorMod(i - 1, 12)].quad
        val p1 = keys[Math.floorMod(i, 12)].quad
        val p2 = keys[Math.floorMod(i + 1, 12)].quad
        val p3 = keys[Math.floorMod(i + 2, 12)].quad
        val t2 = t * t; val t3 = t2 * t
        return FloatArray(8) {
            0.5f * (2 * p1[it] + (-p0[it] + p2[it]) * t + (2 * p0[it] - 5 * p1[it] + 4 * p2[it] - p3[it]) * t2 +
                (-p0[it] + 3 * p1[it] - 3 * p2[it] + p3[it]) * t3)
        }
    }

    /**
     * The real cut-out to show at this angle: between two keyframes, the one facing you more.
     * So a resting slab is always one untouched cut-out; the swap to the next happens mid-turn,
     * as the slab passes a keyframe angle, with a short blend. Returns (cut-out, blend-in, its alpha).
     */
    fun glassAt(theta: Float): Triple<WheelArt.Key, WheelArt.Key?, Float> {
        val u = (wrap(theta) - keys[0].theta) / STEP
        val i = floor(u).toInt()
        val t = u - i
        val main = facing(i)
        val w = 0.12f
        val (other, a) = when {
            t < w -> facing(i - 1) to 0.5f * (1f - t / w)
            t > 1f - w -> facing(i + 1) to 0.5f * (1f - (1f - t) / w)
            else -> null to 0f
        }
        return Triple(main, other?.takeIf { it !== main }, a)
    }

    private fun facing(i: Int): WheelArt.Key {
        val a = keys[Math.floorMod(i, 12)]; val b = keys[Math.floorMod(i + 1, 12)]
        val ca = cos(Math.toRadians(a.theta.toDouble())); val cb = cos(Math.toRadians(b.theta.toDouble()))
        return if (ca >= cb - 1e-4) a else b
    }

    private fun wrap(a: Float): Float { var x = a % 360f; if (x < -180f) x += 360f; if (x >= 180f) x -= 360f; return x }
}

/**
 * Full screen: the ring (all 12 slabs plus their reflections) is made as large as fits, and the
 * floor is scaled about the same centre until it covers the whole screen — no black bars.
 */
private data class Fit(val scale: Float, val ox: Float, val oy: Float, val floor: RectF) {
    fun toScreen(q: FloatArray) = FloatArray(8) { if (it % 2 == 0) q[it] * scale + ox else q[it] * scale + oy }
    /** Screen px of drag per panel: the gap between the centre slab and its neighbour. */
    fun spacing(ring: Ring): Float {
        val a = ring.quadAt(0f); val b = ring.quadAt(STEP)
        return ((b[0] + b[2]) - (a[0] + a[2])) / 2f * scale
    }
    companion object {
        // The ring's extent in the art, reflections included.
        private val RING = RectF(270f, 170f, 1780f, 1152f)
        fun of(art: WheelArt, w: Float, h: Float): Fit {
            if (w <= 0f || h <= 0f) return Fit(1f, 0f, 0f, RectF())
            val sc = minOf(w * 0.96f / RING.width(), h * 0.92f / RING.height())
            val ox = w / 2f - RING.centerX() * sc
            val oy = h / 2f - RING.centerY() * sc
            // Floor: same centre, scaled up (never down) until it covers the screen.
            val cx = RING.centerX(); val cy = RING.centerY()
            val fs = maxOf(sc, w / (2f * minOf(cx, art.width - cx)), h / (2f * minOf(cy, art.height - cy)))
            val floor = RectF(w / 2f - cx * fs, h / 2f - cy * fs, w / 2f + (art.width - cx) * fs, h / 2f + (art.height - cy) * fs)
            return Fit(sc, ox, oy, floor)
        }
    }
}

private fun inQuad(q: FloatArray, x: Float, y: Float): Boolean {
    var sign = 0
    for (k in 0 until 4) {
        val ax = q[k * 2]; val ay = q[k * 2 + 1]
        val bx = q[(k * 2 + 2) % 8]; val by = q[(k * 2 + 3) % 8]
        val cross = (bx - ax) * (y - ay) - (by - ay) * (x - ax)
        val sg = if (cross > 0) 1 else if (cross < 0) -1 else 0
        if (sg != 0) { if (sign == 0) sign = sg else if (sg != sign) return false }
    }
    return true
}

private fun smooth(e0: Float, e1: Float, x: Float): Float {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}

// ── Drawing ──────────────────────────────────────────────────────────────────────

private class Paints {
    val bitmap = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    // Only the glass's bright edges and glints, screened back over the content — the clear
    // body of the glass is left alone so it stays as dark and clear as in the art.
    val sheen = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
        blendMode = BlendMode.SCREEN
        colorFilter = android.graphics.ColorMatrixColorFilter(floatArrayOf(
            1.6f, 0f, 0f, 0f, -150f,
            0f, 1.6f, 0f, 0f, -150f,
            0f, 0f, 1.6f, 0f, -150f,
            0f, 0f, 0f, 1f, 0f
        ))
    }
    val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFEAF6FF.toInt(); textSize = 30f; textAlign = Paint.Align.CENTER
        setShadowLayer(6f, 0f, 0f, 0xFF0A2A55.toInt())
    }
    val fade = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    val shade = Paint(Paint.ANTI_ALIAS_FLAG)
    val matrix = Matrix()
    val reflection = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    val path = Path()
    val globe = Paint(Paint.FILTER_BITMAP_FLAG).apply { blendMode = BlendMode.SCREEN }
    val smoke = Paint(Paint.FILTER_BITMAP_FLAG).apply { blendMode = BlendMode.SCREEN }
    private val shaders = HashMap<WheelArt.Slab, BitmapShader>()
    fun spriteShader(slab: WheelArt.Slab) = shaders.getOrPut(slab) { BitmapShader(slab.sprite, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
}

/** A real slab cut-out warped onto `target`; mirrored when we're seeing that glass from behind. */
private fun drawSprite(canvas: Canvas, slab: WheelArt.Slab, mirror: Boolean, target: FloatArray, alpha: Float, paint: Paint, m: Matrix) {
    if (alpha <= 0.01f) return
    val q = slab.quad
    val src = FloatArray(8) { if (it % 2 == 0) q[it] - slab.left else q[it] - slab.top }
    val from = if (!mirror) src else floatArrayOf(src[2], src[3], src[0], src[1], src[6], src[7], src[4], src[5])
    m.setPolyToPoly(from, 0, target, 0, 4)
    paint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
    canvas.drawBitmap(slab.sprite, m, paint)
}

/** One real slab cut-out, solid; only while passing a keyframe does the next one blend in. */
private fun drawGlass(canvas: Canvas, ring: Ring, target: FloatArray, theta: Float, alpha: Float, paint: Paint, m: Matrix) {
    val (main, other, otherAlpha) = ring.glassAt(theta)
    drawSprite(canvas, main.slab, main.mirror, target, alpha, paint, m)
    if (other != null) drawSprite(canvas, other.slab, other.mirror, target, alpha * otherAlpha, paint, m)
}

private fun depthAlpha(theta: Float): Float {
    val c = cos(Math.toRadians(theta.toDouble())).toFloat()
    return 0.45f + 0.55f * smooth(-0.6f, 0.5f, c)   // far side of the ring is dimmer, like the art
}

private fun drawSlab(canvas: Canvas, ring: Ring, fit: Fit, paints: Paints, theta: Float, panel: Panel?) {
    val target = fit.toScreen(ring.quadAt(theta))
    drawGlass(canvas, ring, target, theta, depthAlpha(theta), paints.bitmap, paints.matrix)

    // Panels show on the front half only, fading as they turn away.
    val c = cos(Math.toRadians(theta.toDouble())).toFloat()
    val ca = smooth(0.25f, 0.75f, c)
    if (panel == null || ca <= 0.01f) return
    drawContent(canvas, panel, target, ca, paints)
    drawGlass(canvas, ring, target, theta, ca, paints.sheen, paints.matrix)   // real glass highlights over it
}

/** The globe at the ring's centre, standing on the floor; screened so its black falls away. */
private fun drawGlobe(canvas: Canvas, frame: Bitmap, fit: Fit, paints: Paints, throughGlass: Boolean = false) {
    val cx = 1046f; val bottom = 900f; val w = 680f
    paints.globe.alpha = if (throughGlass) 150 else 255
    val h = w * frame.height / frame.width
    val r = RectF(cx - w / 2, bottom - h, cx + w / 2, bottom)
    val q = fit.toScreen(floatArrayOf(r.left, r.top, r.right, r.top, r.right, r.bottom, r.left, r.bottom))
    canvas.drawBitmap(frame, null, RectF(q[0], q[1], q[4], q[5]), paints.globe)
}

/**
 * Smoke rising out of the top of the globe while the Earth turns to smoke (the video's smoke is cut off by
 * the globe's round frame). Soft puffs cut from that smoke drift up, spread, grow and fade; screened like
 * the globe so their black falls away. `t` = seconds since the morph began.
 */
private fun drawRisingSmoke(canvas: Canvas, puffs: List<Bitmap>, fit: Fit, paints: Paints, t: Float) {
    if (puffs.isEmpty() || t < 1.4f || t > 6.5f) return
    val cx = 1046f; val w = 680f; val h = w * 432f / 480f; val top = 900f - h
    for (i in 0 until 14) {
        val born = 1.5f + i * 0.2f
        val p = (t - born) / 2.6f
        if (p <= 0f || p >= 1f) continue
        val r1 = kotlin.math.sin(i * 12.9898f) * 0.5f; val r2 = kotlin.math.sin(i * 78.233f) * 0.5f
        val x = cx + r1 * w * 0.5f + r2 * w * 0.35f * p
        val y = top + h * 0.22f - p * h * 0.85f
        val size = w * (0.32f + 0.5f * p)
        val alpha = (minOf(p / 0.15f, 1f) * (1f - p) * (1f - p) * 230).toInt()
        val l = x - size / 2; val tp = y - size / 2; val rt = x + size / 2; val bt = y + size / 2
        val q = fit.toScreen(floatArrayOf(l, tp, rt, tp, rt, bt, l, bt))
        paints.smoke.alpha = alpha
        canvas.save()
        canvas.rotate(r2 * 120f * p, (q[0] + q[4]) / 2, (q[1] + q[5]) / 2)
        canvas.drawBitmap(puffs[i % puffs.size], null, RectF(q[0], q[1], q[4], q[5]), paints.smoke)
        canvas.restore()
    }
}

/** The slab mirrored onto the floor under it, fading out downwards. */
private fun drawReflection(canvas: Canvas, ring: Ring, fit: Fit, paints: Paints, theta: Float) {
    val c = cos(Math.toRadians(theta.toDouble())).toFloat()
    if (c < -0.4f) return
    val q = fit.toScreen(ring.quadAt(theta))
    val depth = 0.55f
    // Upside-down copy hanging from the slab's bottom edge: its top corners drop below the floor line.
    val tlx = q[6] + (q[6] - q[0]) * depth; val tly = q[7] + (q[7] - q[1]) * depth
    val trx = q[4] + (q[4] - q[2]) * depth; val try_ = q[5] + (q[5] - q[3]) * depth
    val target = floatArrayOf(tlx, tly, trx, try_, q[4], q[5], q[6], q[7])
    val top = minOf(q[5], q[7]); val bottom = maxOf(tly, try_)
    val left = minOf(tlx, q[6]) - 4f; val right = maxOf(trx, q[4]) + 4f
    // Fade baked into the paint (bitmap shader masked by a gradient) — no offscreen layer per slab.
    val (a, _, _) = ring.glassAt(theta)
    val q0 = a.slab.quad
    val src = FloatArray(8) { if (it % 2 == 0) q0[it] - a.slab.left else q0[it] - a.slab.top }
    val from = if (!a.mirror) src else floatArrayOf(src[2], src[3], src[0], src[1], src[6], src[7], src[4], src[5])
    paints.matrix.setPolyToPoly(from, 0, target, 0, 4)
    val shader = paints.spriteShader(a.slab).apply { setLocalMatrix(paints.matrix) }
    val alpha = (0.32f * depthAlpha(theta) * 255).toInt().coerceIn(0, 255)
    val fade = LinearGradient(0f, top, 0f, bottom, alpha shl 24 or 0xFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
    paints.reflection.shader = ComposeShader(shader, fade, PorterDuff.Mode.DST_IN)
    paints.path.rewind()
    paints.path.moveTo(target[0], target[1]); paints.path.lineTo(target[2], target[3])
    paints.path.lineTo(target[4], target[5]); paints.path.lineTo(target[6], target[7]); paints.path.close()
    canvas.drawPath(paints.path, paints.reflection)
}

/** Content laid out in front-slab pixels (PW×PH), warped onto `target`. */
private fun drawContent(canvas: Canvas, panel: Panel, target: FloatArray, alpha: Float, paints: Paints) {
    paints.matrix.setPolyToPoly(floatArrayOf(0f, 0f, PW, 0f, PW, PH, 0f, PH), 0, target, 0, 4)
    val save = canvas.save()
    canvas.concat(paints.matrix)
    val a = (alpha * 255).toInt()
    paints.bitmap.alpha = a
    paints.label.alpha = a
    when (panel) {
        is Panel.App -> {
            canvas.drawBitmap(panel.icon, null, RectF(70f, 170f, 230f, 330f), paints.bitmap)
            if (panel.dot) drawDot(canvas, 224f, 176f, alpha)
            label(canvas, panel.app.label, 410f, paints.label)
        }
        is Panel.Drawer -> {
            val cell = 66f; val gap = 14f
            val x0 = (PW - (cell * 3 + gap * 2)) / 2f; val y0 = 150f
            panel.preview.take(9).forEachIndexed { k, bmp ->
                val x = x0 + (k % 3) * (cell + gap); val y = y0 + (k / 3) * (cell + gap)
                canvas.drawBitmap(bmp, null, RectF(x, y, x + cell, y + cell), paints.bitmap)
            }
            label(canvas, "All apps", 440f, paints.label)
        }
        is Panel.Recent -> {
            // Newest big in the middle, the others small around it.
            val big = panel.preview.firstOrNull()
            if (big != null) canvas.drawBitmap(big, null, RectF(95f, 150f, 205f, 260f), paints.bitmap)
            panel.preview.drop(1).take(4).forEachIndexed { k, bmp ->
                val x = 40f + k * 60f
                canvas.drawBitmap(bmp, null, RectF(x, 285f, x + 48f, 333f), paints.bitmap)
            }
            label(canvas, "Recent", 440f, paints.label)
        }
    }
    canvas.restoreToCount(save)
}

private val dotGlow = Paint(Paint.ANTI_ALIAS_FLAG)
private val dotCore = Paint(Paint.ANTI_ALIAS_FLAG)

/** The unread dot: a cyan glow with a bright core, like the home icons. */
private fun drawDot(canvas: Canvas, x: Float, y: Float, alpha: Float) {
    dotGlow.shader = android.graphics.RadialGradient(x, y, 26f, 0xCC7FE9FF.toInt(), 0x007FE9FF, Shader.TileMode.CLAMP)
    dotGlow.alpha = (alpha * 255).toInt()
    canvas.drawCircle(x, y, 26f, dotGlow)
    dotCore.color = 0xFFE6FCFF.toInt(); dotCore.alpha = (alpha * 255).toInt()
    canvas.drawCircle(x, y, 9f, dotCore)
}

private fun label(canvas: Canvas, text: String, y: Float, paint: Paint) {
    val t = TextUtils.ellipsize(text, TextPaint(paint), PW - 50f, TextUtils.TruncateAt.END).toString()
    canvas.drawText(t, PW / 2f, y, paint)
}

/** Flip on the vertical axis while growing from the slab to the whole screen. */
private fun drawOpening(
    canvas: Canvas, slab: WheelArt.Slab, from: FloatArray, w: Float, h: Float, t: Float, panel: Panel, paints: Paints
) {
    val full = floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h)
    val q = FloatArray(8) { from[it] + (full[it] - from[it]) * t }
    val cx = (q[0] + q[2] + q[4] + q[6]) / 4f
    val k = cos(PI * OPEN_HALF_TURNS * t).toFloat()
    if (abs(k) < 0.02f) return
    // |k| narrows the panel through the flip; past halfway we see its back, drawn un-mirrored.
    val face = FloatArray(8) { if (it % 2 == 0) cx + (q[it] - cx) * abs(k) else q[it] }
    drawSprite(canvas, slab, false, face, 1f, paints.bitmap, paints.matrix)
    if (k > 0f) {
        drawContent(canvas, panel, face, 1f, paints)
    } else {
        val path = Path().apply {
            moveTo(face[0], face[1]); lineTo(face[2], face[3]); lineTo(face[4], face[5]); lineTo(face[6], face[7]); close()
        }
        paints.shade.color = 0xFF000000.toInt()
        paints.shade.alpha = (-k * 170).toInt()
        canvas.drawPath(path, paints.shade)
        val icon = (panel as? Panel.App)?.icon
        if (icon != null) {
            val fcx = (face[0] + face[2] + face[4] + face[6]) / 4f
            val fcy = (face[1] + face[3] + face[5] + face[7]) / 4f
            val r = minOf(w, h) * 0.12f * (0.6f + 0.4f * t)
            paints.bitmap.alpha = 255
            canvas.drawBitmap(icon, null, RectF(fcx - r, fcy - r, fcx + r, fcy + r), paints.bitmap)
        }
    }
}
