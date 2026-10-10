package com.jp.otherworld.data

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings

/**
 * An installed app. `icon` is its own icon; `custom` is the user's glass icon for it (if any);
 * `glassed` is the own icon set inside the glass tile.
 */
data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: Bitmap,
    val custom: Bitmap?,
    val glassed: Bitmap,
    /** The category the app declares to Android (ApplicationInfo.CATEGORY_*), for the wheel's rings. */
    val category: Int = -1
) {
    fun iconFor(style: IconStyle): Bitmap = when (style) {
        IconStyle.CUSTOM_GLASS -> custom ?: glassed
        IconStyle.ORIGINAL_IN_GLASS -> glassed
    }
}

private const val ICON_PX = 192

// Adapted from FoldLauncher's loadApps(): every launchable app, sorted by name. Call off the main thread.
fun loadApps(context: Context, glass: (Bitmap) -> Bitmap): List<AppInfo> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
    return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        .filter { it.activityInfo.packageName != context.packageName }
        .distinctBy { it.activityInfo.packageName }
        .map { ri ->
            val own = drawableToBitmap(ri.loadIcon(pm), ICON_PX)
            AppInfo(
                label = ri.loadLabel(pm).toString(),
                packageName = ri.activityInfo.packageName,
                icon = own,
                custom = customIcon(context, ri.activityInfo.packageName),
                glassed = glass(own),
                category = ri.activityInfo.applicationInfo.category
            )
        }
        .sortedBy { it.label.lowercase() }
}

/**
 * Your own icon for an app: "<package>.png" in the app's files/icons folder on the phone
 * (Android/data/com.jp.otherworld/files/icons). Kept on the device, not in the app itself.
 */
fun customIcon(context: Context, pkg: String): Bitmap? {
    val f = java.io.File(context.getExternalFilesDir("icons"), "$pkg.png")
    return if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
}

fun hasCustomIcons(context: Context): Boolean =
    context.getExternalFilesDir("icons")?.listFiles { f -> f.name.endsWith(".png") }?.isNotEmpty() == true

fun drawableToBitmap(drawable: Drawable, px: Int): Bitmap {
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    drawable.setBounds(0, 0, px, px)
    drawable.draw(Canvas(bmp))
    return bmp
}

/** Launch with a fade only — the panel's flip-and-grow is the real transition. */
fun launchApp(context: Context, pkg: String) {
    val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val opts = ActivityOptions.makeCustomAnimation(context, android.R.anim.fade_in, 0).toBundle()
    if (runCatching { context.startActivity(intent, opts) }.isSuccess) LauncherStore.opened(pkg)
}

fun openAppInfo(context: Context, pkg: String) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

fun uninstallApp(context: Context, pkg: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
