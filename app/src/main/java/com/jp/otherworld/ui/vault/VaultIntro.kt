package com.jp.otherworld.ui.vault

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView

/**
 * The hidden drawer's opening: the magnific video (assets/vault_open.mp4) of the alien going up in
 * smoke and the glass case appearing, full screen and cropped to fill it like the wallpaper.
 * `onFinished` fires when it ends (or can't play), so the drawer can fade in over its last frame.
 * Closing plays the reversed copy (vault_close*.mp4); `onFirstFrame` lets the drawer fade out under it.
 */
@Composable
fun VaultIntro(asset: String = "vault_open.mp4", onFirstFrame: () -> Unit = {}, onFinished: () -> Unit) {
    // Stay invisible (home still showing) until the first video frame is up, then fade in: no black flash.
    val fade = remember { Animatable(0f) }
    var firstFrame by remember { mutableStateOf(false) }
    val shown by rememberUpdatedState(onFirstFrame)
    LaunchedEffect(firstFrame) { if (firstFrame) { shown(); fade.animateTo(1f, tween(200)) } }
    val finished by rememberUpdatedState(onFinished)
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(Unit) { onDispose { player?.release(); player = null } }

    // Black behind the film: on wide screens it doesn't fill the sides.
    Box(Modifier.fillMaxSize().alpha(fade.value).background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                TextureView(ctx).apply {
                    // The film shows the cyan alien: tint it to match the alien colour that's set.
                    alienTint(ctx)?.let { setLayerType(android.view.View.LAYER_TYPE_HARDWARE, android.graphics.Paint().apply { colorFilter = android.graphics.ColorMatrixColorFilter(it) }) }
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                            val mp = MediaPlayer()
                            player = mp
                            val ok = runCatching {
                                ctx.assets.openFd(asset).use { mp.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                                mp.setSurface(Surface(st))
                                mp.setOnVideoSizeChangedListener { _, vw, vh -> cover(this@apply, vw, vh) }
                                mp.setOnCompletionListener { finished() }
                                mp.setOnErrorListener { _, _, _ -> finished(); true }
                                // The smoke film has its own sounds (boom, star, beam): silent when the phone is on
                                // silent/vibrate or the launcher's sounds are off, like the wheel's sounds.
                                val audio = ctx.getSystemService(android.media.AudioManager::class.java)
                                val loud = com.jp.otherworld.data.LauncherStore.state.value.sounds &&
                                    audio?.ringerMode == android.media.AudioManager.RINGER_MODE_NORMAL
                                mp.setVolume(if (loud) 1f else 0f, if (loud) 1f else 0f)
                                mp.setOnPreparedListener { it.start() }
                                mp.prepareAsync()
                            }.isSuccess
                            if (!ok) finished()
                        }
                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture) = true
                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) { firstFrame = true }
                    }
                }
            }
        )
    }
}

/** Place the video like the drawer's picture (see [vaultFit]), so the cube lines up on every screen. */
private fun cover(view: TextureView, vw: Int, vh: Int) {
    if (vw == 0 || vh == 0 || view.width == 0 || view.height == 0) return
    val w = view.width.toFloat(); val h = view.height.toFloat()
    val d = view.resources.displayMetrics.density
    val f = vaultFit(w, h, vw.toFloat(), vh.toFloat(), VAULT_TOP_DP * d, VAULT_BOTTOM_DP * d)
    // TextureView stretches the video to the view first; undo that, then scale and place it.
    view.setTransform(Matrix().apply { setScale(f.width / w, f.height / h); postTranslate(f.left, f.top) })
}

/**
 * Where the vault picture/film goes on a w×h screen: fills it like a crop on normal phones, but always keeps
 * the whole glass cube (and its base) in view, so wider, squarer and folding screens don't cut it off.
 * The cube stays between the drawer's header (`top`) and its bottom button (`bottom`).
 * Any spare space is left at the black top or sides, never under the bright floor.
 */
fun vaultFit(w: Float, h: Float, iw: Float, ih: Float, top: Float = 0f, bottom: Float = 0f): androidx.compose.ui.geometry.Rect {
    var s = maxOf(w / iw, h / ih)
    s = minOf(s, w / ((KEEP_R - KEEP_L) * iw), (h - top - bottom) / ((KEEP_B - KEEP_T) * ih))
    val dw = iw * s; val dh = ih * s
    var x = ((w - dw) / 2).within(-KEEP_L * dw, w - KEEP_R * dw)
    var y = ((h - dh) / 2).within(top - KEEP_T * dh, h - bottom - KEEP_B * dh)
    if (dh > h) y = y.coerceAtLeast(h - dh) else y = h - dh
    if (dw > w) x = x.within(w - dw, 0f)
    return androidx.compose.ui.geometry.Rect(x, y, x + dw, y + dh)
}

// coerceIn that tolerates lo a hair above hi (float rounding when the cube fits exactly).
private fun Float.within(lo: Float, hi: Float) = if (lo > hi) (lo + hi) / 2 else coerceIn(lo, hi)

// The part of the picture that must stay on screen: the cube, its base and a little room, as fractions.
private const val KEEP_L = 0.12f; private const val KEEP_R = 0.88f
private const val KEEP_T = 0.38f; private const val KEEP_B = 0.86f
// Room kept for the drawer's header (title + tabs) and its bottom button.
const val VAULT_TOP_DP = 175f; const val VAULT_BOTTOM_DP = 90f

/**
 * A colour filter turning the cyan alien into the colour of the alien wallpaper that's set (same hue
 * shifts the coloured copies were made with; whites stay white). Null for cyan or any other wallpaper.
 */
internal fun alienTint(context: android.content.Context): android.graphics.ColorMatrix? {
    val service = runCatching { android.app.WallpaperManager.getInstance(context).wallpaperInfo?.serviceName }.getOrNull() ?: return null
    if (!service.startsWith("com.jp.otherworld.wallpaper.Alien")) return null
    val name = service.substringAfterLast('.')
    if (name == "AlienIceWallpaperService") return android.graphics.ColorMatrix().apply { setSaturation(0.18f) }
    val deg = when (name) {
        "AlienGreenWallpaperService" -> -71f
        "AlienPinkWallpaperService" -> 133f
        "AlienPurpleWallpaperService" -> 86f
        "AlienRedWallpaperService" -> 174f
        "AlienGoldWallpaperService" -> -162f
        "AlienBlueWallpaperService" -> 39f
        else -> return null
    }
    val r = Math.toRadians(deg.toDouble()); val a = Math.cos(r).toFloat(); val b = Math.sin(r).toFloat()
    // Standard hue rotation (keeps brightness, so white stays white).
    return android.graphics.ColorMatrix(floatArrayOf(
        0.213f + 0.787f * a - 0.213f * b, 0.715f - 0.715f * a - 0.715f * b, 0.072f - 0.072f * a + 0.928f * b, 0f, 0f,
        0.213f - 0.213f * a + 0.143f * b, 0.715f + 0.285f * a + 0.140f * b, 0.072f - 0.072f * a - 0.283f * b, 0f, 0f,
        0.213f - 0.213f * a - 0.787f * b, 0.715f - 0.715f * a + 0.715f * b, 0.072f + 0.928f * a + 0.072f * b, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    ))
}
