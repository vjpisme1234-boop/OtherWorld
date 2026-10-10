package com.jp.otherworld.ui.home

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jp.otherworld.ui.common.*
import com.jp.otherworld.wallpaper.AlienGreenWallpaperService
import com.jp.otherworld.wallpaper.AlienPinkWallpaperService
import com.jp.otherworld.wallpaper.AlienPurpleWallpaperService
import com.jp.otherworld.wallpaper.AlienWallpaperService
import com.jp.otherworld.wallpaper.GlobeWallpaperService
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

/** Sets the phone's real wallpaper from a photo or the user's art, on home, lock screen or both. */
@Composable
fun WallpaperPicker(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Where the chosen picture comes from; asks home/lock/both next.
    var source by remember { mutableStateOf<(() -> InputStream?)?>(null) }
    var busy by remember { mutableStateOf(false) }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) source = { context.contentResolver.openInputStream(uri) }
    }
    fun thumb(name: String) = context.assets.open(name).use { android.graphics.BitmapFactory.decodeStream(it) }.asImageBitmap()
    val art = remember { thumb("glass_wheel.png") }
    val globe = remember { thumb("globe_thumb.jpg") }
    val alien = remember { thumb("alien.png") }
    val alienGreen = remember { thumb("alien_green.png") }
    val alienPink = remember { thumb("alien_pink.png") }
    val alienPurple = remember { thumb("alien_purple.png") }
    val alienRed = remember { thumb("alien_red.png") }
    val alienGold = remember { thumb("alien_gold.png") }
    val alienIce = remember { thumb("alien_ice.png") }
    val alienBlue = remember { thumb("alien_blue.png") }

    fun apply(flags: Int) {
        val open = source ?: return
        busy = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { open()?.use { WallpaperManager.getInstance(context).setStream(it, null, true, flags) } != null }.getOrDefault(false)
            }
            busy = false
            Toast.makeText(context, if (ok) "Wallpaper set" else "Couldn't set that wallpaper", Toast.LENGTH_SHORT).show()
            onDone()
        }
    }

    val chosen = source
    if (chosen == null) {
        GlassSheet(onDone) {
            GlassLabel("Wallpaper", 22.sp, weight = FontWeight.Light)
            GlassLabel("Other World (animated)", 14.sp, GlassDim)
            // All the animated ones in one row you can slide sideways.
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val w = Modifier.width(96.dp)
                LiveThumb(globe, "Globe", w) { setLiveWallpaper(context, GlobeWallpaperService::class.java); onDone() }
                LiveThumb(alien, "Alien", w) { setLiveWallpaper(context, AlienWallpaperService::class.java); onDone() }
                LiveThumb(alienGreen, "Green", w) { setLiveWallpaper(context, AlienGreenWallpaperService::class.java); onDone() }
                LiveThumb(alienPink, "Pink", w) { setLiveWallpaper(context, AlienPinkWallpaperService::class.java); onDone() }
                LiveThumb(alienPurple, "Purple", w) { setLiveWallpaper(context, AlienPurpleWallpaperService::class.java); onDone() }
                LiveThumb(alienRed, "Red", w) { setLiveWallpaper(context, com.jp.otherworld.wallpaper.AlienRedWallpaperService::class.java); onDone() }
                LiveThumb(alienGold, "Gold", w) { setLiveWallpaper(context, com.jp.otherworld.wallpaper.AlienGoldWallpaperService::class.java); onDone() }
                LiveThumb(alienIce, "Ice", w) { setLiveWallpaper(context, com.jp.otherworld.wallpaper.AlienIceWallpaperService::class.java); onDone() }
                LiveThumb(alienBlue, "Blue", w) { setLiveWallpaper(context, com.jp.otherworld.wallpaper.AlienBlueWallpaperService::class.java); onDone() }
            }
            GlassLabel("Slide for more →", 12.sp, GlassDim)
            GlassMenuItem("Photos") { photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            GlassLabel("Your art", 14.sp, GlassDim)
            Image(
                art, "Glass wheel art", contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp))
                    .clickable { source = { context.assets.open("glass_wheel.png") } }
            )
            GlassMenuItem("Android wallpaper & style") {
                runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper")) }
                onDone()
            }
        }
    } else {
        GlassSheet(onDone) {
            GlassLabel(if (busy) "Setting wallpaper…" else "Set wallpaper on", 20.sp)
            if (!busy) {
                GlassMenuItem("Home screen") { apply(WallpaperManager.FLAG_SYSTEM) }
                GlassMenuItem("Lock screen") { apply(WallpaperManager.FLAG_LOCK) }
                GlassMenuItem("Both") { apply(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK) }
            }
        }
    }
}

@Composable
private fun LiveThumb(img: ImageBitmap, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            img, label, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(14.dp)).background(Color.Black)
        )
        Spacer(Modifier.height(4.dp))
        GlassLabel(label, 13.sp)
    }
}

/** Opens Android's live-wallpaper preview for one of ours; the user confirms there (Android requires it). */
fun setLiveWallpaper(context: Context, service: Class<*>) {
    val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(context, service))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, "This phone can't preview live wallpapers", Toast.LENGTH_SHORT).show() }
}
