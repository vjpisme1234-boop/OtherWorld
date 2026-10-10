package com.jp.otherworld.ui.wheel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject

/**
 * The user's wheel art. `floor` is the original with the painted slabs removed
 * (art/derived/floor_clean.png); `slabs` are the six front slabs cut from the original
 * (art/derived/slab_*.png); `ring` is the 12 slab outlines measured from the original,
 * keyed by angle around the ring (0° = facing you). Quads are TL, TR, BR, BL in image pixels.
 */
class WheelArt(
    val floor: Bitmap,
    val width: Int,
    val height: Int,
    val slabs: Map<String, Slab>,
    val ring: List<Key>
) {
    class Slab(val sprite: Bitmap, val left: Float, val top: Float, val quad: FloatArray)
    class Key(val theta: Float, val quad: FloatArray, val slab: Slab, val mirror: Boolean)
}

fun loadWheelArt(context: Context): WheelArt {
    val json = JSONObject(context.assets.open("wheel.json").bufferedReader().use { it.readText() })
    fun bitmap(name: String) = context.assets.open(name).use { BitmapFactory.decodeStream(it) }
    fun quad(o: JSONObject) = o.getJSONArray("quad").let { q -> FloatArray(8) { q.getDouble(it).toFloat() } }
    val slabs = json.getJSONArray("slots").let { arr ->
        (0 until arr.length()).associate { i ->
            val s = arr.getJSONObject(i)
            s.getString("id") to WheelArt.Slab(
                sprite = bitmap(s.getString("sprite")),
                left = s.getDouble("left").toFloat(),
                top = s.getDouble("top").toFloat(),
                quad = quad(s)
            )
        }
    }
    val ring = json.getJSONArray("ring").let { arr ->
        (0 until arr.length()).map { i ->
            val k = arr.getJSONObject(i)
            WheelArt.Key(k.getDouble("theta").toFloat(), quad(k), slabs.getValue(k.getString("slab")), k.getBoolean("mirror"))
        }.sortedBy { it.theta }
    }
    return WheelArt(bitmap(json.getString("floor")), json.getInt("width"), json.getInt("height"), slabs, ring)
}
