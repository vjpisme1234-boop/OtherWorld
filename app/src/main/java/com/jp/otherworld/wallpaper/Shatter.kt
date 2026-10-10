package com.jp.otherworld.wallpaper

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/** How the alien breaks apart when the hidden drawer opens. */
enum class ShatterStyle(val tile: Int) {
    /** Fine glowing specks that swirl outward and up, like dust or smoke. */
    DUST(4),
    /** Glass-like fragments of the mesh that spin away and fall. */
    SHARDS(14),
    /** Embers that rise and burn out from white to orange to red. */
    SPARKS(4);
}

/**
 * The alien's own picture cut into small tiles, drawn in one call (textured triangles), each tile
 * moving on its own. Tiles start breaking away near the eyes (where the fingers are) and the rest
 * follow outward. progress 0 = whole face, 1 = gone; running it backwards puts the face back together.
 */
class Shatter(src: Bitmap, val style: ShatterStyle, eyes: List<Pair<Float, Float>>) {
    private val n: Int
    private val cx: FloatArray; private val cy: FloatArray      // tile centre in the picture
    private val dirX: FloatArray; private val dirY: FloatArray  // direction away from the nearest eye
    private val delay: FloatArray                               // 0..0.6: near the eyes first
    private val speed: FloatArray; private val spin: FloatArray; private val seed: FloatArray
    private val verts: FloatArray; private val texs: FloatArray; private val colors: IntArray
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
    // Sparks: once a tile ignites it's drawn as a glowing ember in its own colour (not the picture).
    private val emberPaint = Paint().apply { blendMode = android.graphics.BlendMode.PLUS }
    private val emberColors: IntArray

    init {
        val t = style.tile
        val w = src.width; val h = src.height
        val px = IntArray(w * h).also { src.getPixels(it, 0, w, 0, 0, w, h) }
        val origins = eyes.ifEmpty { listOf(w / 2f to h / 2f) }
        val maxD = hypot(w.toFloat(), h.toFloat())
        val list = ArrayList<FloatArray>()
        val rnd = Random(7)
        for (ty in 0 until h step t) for (tx in 0 until w step t) {
            // Skip tiles that are (nearly) black: on black they'd be invisible anyway.
            var lum = 0; var k = 0
            for (y in ty until minOf(ty + t, h) step 2) for (x in tx until minOf(tx + t, w) step 2) {
                val p = px[y * w + x]; lum += maxOf((p shr 16) and 255, (p shr 8) and 255, p and 255); k++
            }
            if (k == 0 || lum / k < 14) continue
            val mx = tx + t / 2f; val my = ty + t / 2f
            val (ex, ey) = origins.minBy { hypot(it.first - mx, it.second - my) }
            val d = hypot(mx - ex, my - ey).coerceAtLeast(0.001f)
            val a = kotlin.math.atan2(my - ey, mx - ex) + (rnd.nextFloat() - 0.5f) * 0.9f
            list += floatArrayOf(mx, my, cos(a), sin(a), (d / maxD * 1.3f).coerceAtMost(0.6f) + rnd.nextFloat() * 0.08f,
                0.6f + rnd.nextFloat() * 0.8f, (rnd.nextFloat() - 0.5f) * 2f, rnd.nextFloat() * 100f)
        }
        n = list.size
        cx = FloatArray(n) { list[it][0] }; cy = FloatArray(n) { list[it][1] }
        dirX = FloatArray(n) { list[it][2] }; dirY = FloatArray(n) { list[it][3] }
        delay = FloatArray(n) { list[it][4] }; speed = FloatArray(n) { list[it][5] }
        spin = FloatArray(n) { list[it][6] }; seed = FloatArray(n) { list[it][7] }
        verts = FloatArray(n * 12); colors = IntArray(n * 6); emberColors = IntArray(n * 6)
        // Texture corners never change: two triangles per tile, in picture pixels.
        texs = FloatArray(n * 12)
        val half = t / 2f
        for (i in 0 until n) {
            val l = cx[i] - half; val r = cx[i] + half; val tp = cy[i] - half; val b = cy[i] + half
            floatArrayOf(l, tp, r, tp, r, b, l, tp, r, b, l, b).copyInto(texs, i * 12)
        }
    }

    /**
     * Draws the face at `progress` (0..1) with the picture placed at (left, top) scaled by `scale`.
     * `width` is the screen width, so distances look the same on any phone.
     */
    fun draw(c: Canvas, progress: Float, left: Float, top: Float, scale: Float, width: Float, now: Long) {
        val half = style.tile / 2f * scale
        val p = progress * 1.6f
        for (i in 0 until n) {
            val local = ((p - delay[i]) / 1f).coerceIn(0f, 1f)
            var x = left + cx[i] * scale; var y = top + cy[i] * scale
            var size = 1f; var angle = 0f; var alpha = 1f
            var r = 255; var g = 255; var b = 255
            if (local > 0f) {
                val e = local * local * (3 - 2 * local)   // smooth start and finish
                when (style) {
                    ShatterStyle.DUST -> {
                        val dist = width * 0.55f * speed[i] * e
                        val swirl = sin(local * PI).toFloat() * width * 0.06f * spin[i]
                        x += dirX[i] * dist - dirY[i] * swirl
                        y += dirY[i] * dist + dirX[i] * swirl - width * 0.3f * e
                        size = 1f - 0.7f * local
                        alpha = 1f - local
                    }
                    ShatterStyle.SHARDS -> {
                        val dist = width * 0.9f * speed[i] * local * local
                        x += dirX[i] * dist
                        y += dirY[i] * dist + width * 0.5f * local * local
                        angle = spin[i] * local * PI.toFloat() * 2f
                        alpha = 1f - local * local
                    }
                    ShatterStyle.SPARKS -> {
                        x += dirX[i] * width * 0.12f * e + sin(seed[i] + local * 9f) * width * 0.02f
                        y -= width * (0.5f + 0.5f * speed[i]) * e
                        size = 1f - 0.5f * local
                        // Burns from white to orange to red, flickering, then out.
                        r = 255; g = (255 - 200 * local).toInt().coerceIn(40, 255); b = (255 - 400 * local).toInt().coerceIn(0, 255)
                        alpha = (1f - local) * (0.65f + 0.35f * sin(seed[i] + now * 0.03f))
                    }
                }
            }
            val hs = half * size
            val ca = cos(angle); val sa = sin(angle)
            // Corners (-1,-1) (1,-1) (1,1) (-1,1) rotated and placed.
            fun cxOf(u: Float, v: Float) = x + (u * ca - v * sa) * hs
            fun cyOf(u: Float, v: Float) = y + (u * sa + v * ca) * hs
            val x0 = cxOf(-1f, -1f); val y0 = cyOf(-1f, -1f)
            val x1 = cxOf(1f, -1f); val y1 = cyOf(1f, -1f)
            val x2 = cxOf(1f, 1f); val y2 = cyOf(1f, 1f)
            val x3 = cxOf(-1f, 1f); val y3 = cyOf(-1f, 1f)
            val o = i * 12
            verts[o] = x0; verts[o + 1] = y0; verts[o + 2] = x1; verts[o + 3] = y1; verts[o + 4] = x2; verts[o + 5] = y2
            verts[o + 6] = x0; verts[o + 7] = y0; verts[o + 8] = x2; verts[o + 9] = y2; verts[o + 10] = x3; verts[o + 11] = y3
            val a8 = (alpha.coerceIn(0f, 1f) * 255).toInt()
            if (style == ShatterStyle.SPARKS) {
                // Not yet lit: the face as it is. Lit: an ember (the picture tile is hidden).
                colors.fill(if (local > 0f) 0 else -1, i * 6, i * 6 + 6)
                emberColors.fill(if (local > 0f) (a8 shl 24) or (r shl 16) or (g shl 8) or b else 0, i * 6, i * 6 + 6)
            } else colors.fill((a8 shl 24) or 0xFFFFFF, i * 6, i * 6 + 6)
        }
        c.drawVertices(Canvas.VertexMode.TRIANGLES, n * 12, verts, 0, texs, 0, colors, 0, null, 0, 0, paint)
        if (style == ShatterStyle.SPARKS)
            c.drawVertices(Canvas.VertexMode.TRIANGLES, n * 12, verts, 0, null, 0, emberColors, 0, null, 0, 0, emberPaint)
    }
}
