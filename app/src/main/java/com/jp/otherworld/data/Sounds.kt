package com.jp.otherworld.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaRecorder
import android.media.SoundPool
import android.os.Build
import android.os.SystemClock
import java.io.File

/**
 * The wheel's glass sounds (art/generated/sounds): tick as panels pass, open when one flips open,
 * ring when switching rings, drawer when the wheel opens. Each can be switched off, has its own
 * volume, and can be the user's own recording (take 4, "Mine"). Silent whenever the phone is on
 * vibrate or silent, or sounds are off in Home settings.
 */
object Sounds {
    val KINDS = listOf("tick", "open", "ring", "drawer", "boom")
    /** The take number of the user's own on-phone recording (built-in takes are 1..4). */
    const val MINE = 9
    const val MAX_RECORD_MS = 3000
    // The takes are levelled to the same loudness (art/generated/sounds/normalized); these are starting volumes.
    private val DEFAULT_VOLUME = mapOf("tick" to 0.25f, "ring" to 0.25f, "open" to 0.25f, "drawer" to 0.25f, "boom" to 0.7f)
    /** Take used until the user picks one: the set the user chose on their phone (2026-10-10). */
    fun defaultPick(kind: String) = when (kind) { "ring" -> 3; else -> 1 }

    /** Built-in takes for a sound: 1–3 generated, plus 4 where the user gave one (Wheel opens). */
    fun takes(kind: String): List<Int> = (1..4).filter { "${kind}_$it" in ids }

    private var pool: SoundPool? = null
    private val ids = HashMap<String, Int>()     // "tick_2" → sound id
    private lateinit var audio: AudioManager
    private lateinit var dir: File
    private var lastTick = 0L

    fun init(context: Context) {
        if (pool != null) return
        audio = context.getSystemService(AudioManager::class.java)
        dir = File(context.filesDir, "sounds").apply { mkdirs() }
        val p = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        ).build()
        pool = p
        for (k in KINDS) {
            for (n in 1..4) for (ext in listOf("wav", "m4a")) {
                runCatching { context.assets.openFd("sounds/${k}_$n.$ext").use { ids["${k}_$n"] = p.load(it, 1) } }
            }
            loadMine(k)
        }
    }

    fun defaultVolume(kind: String) = DEFAULT_VOLUME[kind] ?: 0.35f
    fun volume(kind: String) = LauncherStore.state.value.soundVolume[kind] ?: defaultVolume(kind)

    /** The user's own recording for a sound, if they made one. */
    fun mineFile(kind: String) = File(dir, "${kind}_mine.m4a")
    fun hasMine(kind: String) = ::dir.isInitialized && mineFile(kind).exists()

    private fun loadMine(kind: String) {
        val p = pool ?: return
        ids.remove("${kind}_$MINE")?.let { p.unload(it) }
        val f = mineFile(kind)
        if (f.exists()) ids["${kind}_$MINE"] = p.load(f.absolutePath, 1)
    }

    /** Plays the user's chosen take of a sound, or (`take` / `test`) one to try out, ignoring the switches. */
    fun play(kind: String, take: Int? = null, test: Boolean = false) {
        val p = pool ?: return
        val state = LauncherStore.state.value
        if (take == null && !test) {
            if (!state.sounds || kind in state.soundOff || audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
            if (kind == "tick") {
                // A fast spin passes many panels: don't stack ticks closer than 45 ms.
                val now = SystemClock.uptimeMillis()
                if (now - lastTick < 45) return
                lastTick = now
            }
        }
        val id = ids["${kind}_${take ?: state.soundPick[kind] ?: defaultPick(kind)}"] ?: ids["${kind}_${defaultPick(kind)}"] ?: return
        val v = volume(kind)
        p.play(id, v, v, 1, 0, 1f)
    }

    // ── Recording your own ──
    private var recorder: MediaRecorder? = null
    private var recording: String? = null

    /** Starts recording the user's own version of a sound (stops by itself after 3 s). Needs RECORD_AUDIO. */
    fun startRecording(context: Context, kind: String, onStopped: () -> Unit): Boolean {
        stopRecording()
        val tmp = File(dir, "${kind}_rec.m4a")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        return runCatching {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(44100); r.setAudioEncodingBitRate(128_000)
            r.setMaxDuration(MAX_RECORD_MS)
            r.setOutputFile(tmp.absolutePath)
            r.setOnInfoListener { _, what, _ -> if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) { stopRecording(); onStopped() } }
            r.prepare(); r.start()
            recorder = r; recording = kind
            true
        }.getOrElse { r.release(); false }
    }

    /** Stops recording; a good take becomes that sound's "Mine" and is chosen. */
    fun stopRecording() {
        val r = recorder ?: return
        val kind = recording ?: return
        recorder = null; recording = null
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        val tmp = File(dir, "${kind}_rec.m4a")
        if (ok && tmp.length() > 0) {
            tmp.renameTo(mineFile(kind))
            loadMine(kind)
            LauncherStore.update { it.copy(soundPick = it.soundPick + (kind to MINE), soundOff = it.soundOff - kind) }
        } else tmp.delete()
    }

    fun deleteMine(kind: String) {
        mineFile(kind).delete()
        ids.remove("${kind}_$MINE")?.let { pool?.unload(it) }
        LauncherStore.update { if (it.soundPick[kind] == MINE) it.copy(soundPick = it.soundPick - kind) else it }
    }
}
