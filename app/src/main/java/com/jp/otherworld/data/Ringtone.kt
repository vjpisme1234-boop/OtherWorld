package com.jp.otherworld.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings

/**
 * Other World's phone ringtone (assets/ringtone_radio_control.mp3, the user's "radio control" voice clip).
 * "Set" copies it into the phone's Ringtones (so it's also in the system ringtone list) and makes it the
 * ringtone. Making it the ringtone needs "Modify system settings", which the user allows once.
 */
object Ringtone {
    private const val ASSET = "ringtone_radio_control.mp3"
    private const val NAME = "Other World - Radio Control.mp3"
    private var player: MediaPlayer? = null

    val playing get() = player?.isPlaying == true

    /** Plays it once (or stops it if it's already playing). */
    fun togglePreview(context: Context, onDone: () -> Unit) {
        if (stop()) { onDone(); return }
        player = MediaPlayer().apply {
            context.assets.openFd(ASSET).use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            setOnCompletionListener { stop(); onDone() }
            prepare(); start()
        }
    }

    /** Stops the preview; true if one was playing. */
    fun stop(): Boolean {
        val p = player ?: return false
        runCatching { p.stop() }; p.release(); player = null
        return true
    }

    fun canSet(context: Context) = Settings.System.canWrite(context)

    /** Opens Android's "Modify system settings" page for Other World. */
    fun askPermission(context: Context) {
        context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Copies it into Ringtones (once) and sets it as the phone's ringtone. Returns false if that failed. */
    fun setAsRingtone(context: Context): Boolean = runCatching {
        val uri = installed(context) ?: install(context)
        RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, uri)
        true
    }.getOrDefault(false)

    /** True if the phone's ringtone is this one. */
    fun isCurrent(context: Context): Boolean = runCatching {
        val cur = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE) ?: return false
        cur == installed(context)
    }.getOrDefault(false)

    private fun installed(context: Context): Uri? {
        val col = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        context.contentResolver.query(col, arrayOf(MediaStore.Audio.Media._ID),
            "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.IS_RINGTONE} = 1", arrayOf(NAME), null)?.use { c ->
            if (c.moveToFirst()) return android.content.ContentUris.withAppendedId(col, c.getLong(0))
        }
        return null
    }

    private fun install(context: Context): Uri {
        val col = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, NAME)
            put(MediaStore.Audio.Media.TITLE, "Other World - Radio Control")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Ringtones/")
            put(MediaStore.Audio.Media.IS_RINGTONE, 1)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(col, values) ?: error("Couldn't add the ringtone")
        context.contentResolver.openOutputStream(uri)!!.use { out -> context.assets.open(ASSET).use { it.copyTo(out) } }
        context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        return uri
    }
}
