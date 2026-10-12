package com.jp.otherworld.ui.home

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jp.otherworld.ui.vault.alienTint

/**
 * After a missed call, the first time home is opened: the alien whispers "I was just trying to tell you…
 * you have a phone call" and smirks (assets/missed_call_alien.mp4). Full screen, whole face shown, tinted
 * to the alien wallpaper's colour; sound only when the phone isn't on silent/vibrate. Tap or Back to stop.
 */
@Composable
fun MissedCallVideo(onDone: () -> Unit) {
    val done by rememberUpdatedState(onDone)
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(Unit) { onDispose { player?.release(); player = null } }
    Dialog(onDismissRequest = { done() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black)
                .clickable(remember { MutableInteractionSource() }, indication = null) { done() }
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    TextureView(ctx).apply {
                        alienTint(ctx)?.let { setLayerType(android.view.View.LAYER_TYPE_HARDWARE, android.graphics.Paint().apply { colorFilter = android.graphics.ColorMatrixColorFilter(it) }) }
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                val mp = MediaPlayer(); player = mp
                                val ok = runCatching {
                                    ctx.assets.openFd("missed_call_alien.mp4").use { mp.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                                    mp.setSurface(Surface(st))
                                    mp.setOnVideoSizeChangedListener { _, vw, vh -> fit(this@apply, vw, vh) }
                                    mp.setOnCompletionListener { done() }
                                    mp.setOnErrorListener { _, _, _ -> done(); true }
                                    val audio = ctx.getSystemService(android.media.AudioManager::class.java)
                                    val loud = com.jp.otherworld.data.LauncherStore.state.value.sounds &&
                                        audio?.ringerMode == android.media.AudioManager.RINGER_MODE_NORMAL
                                    if (!loud) mp.setVolume(0f, 0f)
                                    mp.setOnPreparedListener { it.start() }
                                    mp.prepareAsync()
                                }.isSuccess
                                if (!ok) done()
                            }
                            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture) = true
                            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                        }
                    }
                }
            )
        }
    }
}

/** Whole video visible, centred (the face shouldn't be cropped). */
private fun fit(view: TextureView, vw: Int, vh: Int) {
    if (vw == 0 || vh == 0 || view.width == 0 || view.height == 0) return
    val w = view.width.toFloat(); val h = view.height.toFloat()
    val s = minOf(w / vw, h / vh)
    view.setTransform(Matrix().apply { setScale(vw * s / w, vh * s / h, w / 2f, h / 2f) })
}
