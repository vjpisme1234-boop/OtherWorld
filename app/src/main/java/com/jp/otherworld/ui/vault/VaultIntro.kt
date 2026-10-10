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
 */
@Composable
fun VaultIntro(asset: String = "vault_open.mp4", onFinished: () -> Unit) {
    // Stay invisible (home still showing) until the first video frame is up, then fade in: no black flash.
    val fade = remember { Animatable(0f) }
    var firstFrame by remember { mutableStateOf(false) }
    LaunchedEffect(firstFrame) { if (firstFrame) fade.animateTo(1f, tween(200)) }
    val finished by rememberUpdatedState(onFinished)
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(Unit) { onDispose { player?.release(); player = null } }

    Box(Modifier.fillMaxSize().alpha(fade.value)) {
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

/** Scale the video to cover the view (centred, cropped), like the wallpaper fills the screen. */
private fun cover(view: TextureView, vw: Int, vh: Int) {
    if (vw == 0 || vh == 0 || view.width == 0 || view.height == 0) return
    val w = view.width.toFloat(); val h = view.height.toFloat()
    val s = maxOf(w / vw, h / vh)
    // TextureView stretches the video to the view first; undo that, then scale to cover.
    val sx = vw * s / w; val sy = vh * s / h
    view.setTransform(Matrix().apply { setScale(sx, sy, w / 2f, h / 2f) })
}

/**
 * A colour filter turning the cyan alien into the colour of the alien wallpaper that's set (same hue
 * shifts the coloured copies were made with; whites stay white). Null for cyan or any other wallpaper.
 */
private fun alienTint(context: android.content.Context): android.graphics.ColorMatrix? {
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
