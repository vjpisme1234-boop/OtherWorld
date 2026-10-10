package com.jp.otherworld.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume

data class WeatherNow(val temp: Int, val code: Int, val hi: Int, val lo: Int, val unit: String, val at: Long)

/**
 * Weather for the glass widget, from Open-Meteo (free, no account). Uses the phone's rough
 * (city-level) location only; refreshed at most every 30 minutes and kept between restarts.
 */
object Weather {
    private val _now = MutableStateFlow<WeatherNow?>(null)
    val now: StateFlow<WeatherNow?> = _now
    private const val EVERY_MS = 30 * 60 * 1000L

    fun hasPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun prefs(context: Context) = context.getSharedPreferences("weather", Context.MODE_PRIVATE)

    /** Loads the last saved reading, then fetches a new one if it's old. */
    suspend fun refresh(context: Context, force: Boolean = false) {
        if (_now.value == null) prefs(context).getString("last", null)?.let { s ->
            runCatching { JSONObject(s).let { WeatherNow(it.getInt("t"), it.getInt("c"), it.getInt("hi"), it.getInt("lo"), it.getString("u"), it.getLong("at")) } }
                .getOrNull()?.let { _now.value = it }
        }
        val last = _now.value
        if (!force && last != null && System.currentTimeMillis() - last.at < EVERY_MS) return
        if (!hasPermission(context)) return
        val loc = location(context) ?: return
        val f = Locale.getDefault().country in setOf("US", "LR", "MM")
        val fresh = withContext(Dispatchers.IO) {
            runCatching {
                val url = URL(
                    "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f".format(Locale.US, loc.latitude, loc.longitude) +
                        "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto" +
                        if (f) "&temperature_unit=fahrenheit" else ""
                )
                val conn = (url.openConnection() as HttpURLConnection).apply { connectTimeout = 8000; readTimeout = 8000 }
                val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val cur = o.getJSONObject("current"); val day = o.getJSONObject("daily")
                WeatherNow(
                    Math.round(cur.getDouble("temperature_2m")).toInt(), cur.getInt("weather_code"),
                    Math.round(day.getJSONArray("temperature_2m_max").getDouble(0)).toInt(),
                    Math.round(day.getJSONArray("temperature_2m_min").getDouble(0)).toInt(),
                    if (f) "°F" else "°C", System.currentTimeMillis()
                )
            }.getOrNull()
        } ?: return
        _now.value = fresh
        prefs(context).edit().putString("last", JSONObject().put("t", fresh.temp).put("c", fresh.code).put("hi", fresh.hi)
            .put("lo", fresh.lo).put("u", fresh.unit).put("at", fresh.at).toString()).apply()
    }

    @Suppress("MissingPermission")
    private suspend fun location(context: Context): Location? {
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER).forEach { p ->
            runCatching { lm.getLastKnownLocation(p) }.getOrNull()?.let { return it }
        }
        if (!lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) return null
        return withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine { cont ->
                runCatching {
                    lm.getCurrentLocation(LocationManager.NETWORK_PROVIDER, null, context.mainExecutor) { cont.resume(it) }
                }.onFailure { cont.resume(null) }
            }
        }
    }

    /** Words and a symbol for an Open-Meteo weather code. */
    fun describe(code: Int): Pair<String, String> = when (code) {
        0 -> "Clear" to "☀"
        1, 2 -> "Partly cloudy" to "⛅"
        3 -> "Cloudy" to "☁"
        45, 48 -> "Fog" to "🌫"
        in 51..57 -> "Drizzle" to "🌦"
        in 61..67 -> "Rain" to "🌧"
        in 71..77 -> "Snow" to "❄"
        in 80..82 -> "Showers" to "🌦"
        in 85..86 -> "Snow showers" to "🌨"
        in 95..99 -> "Thunderstorm" to "⛈"
        else -> "—" to "·"
    }
}
