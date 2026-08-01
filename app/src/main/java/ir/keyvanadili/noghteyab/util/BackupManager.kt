package ir.keyvanadili.noghteyab.util

import android.content.Context
import android.net.Uri
import ir.keyvanadili.noghteyab.data.GeoPoint
import org.json.JSONArray
import org.json.JSONObject

object BackupManager {

    fun toJson(points: List<GeoPoint>): String {
        val arr = JSONArray()
        for (p in points) {
            val obj = JSONObject()
            obj.put("name", p.name)
            obj.put("category", p.category)
            obj.put("latitude", p.latitude)
            obj.put("longitude", p.longitude)
            obj.put("timestamp", p.timestamp)
            obj.put("note", p.note)
            arr.put(obj)
        }
        val root = JSONObject()
        root.put("app", "NoghteYab")
        root.put("version", 1)
        root.put("points", arr)
        return root.toString(2)
    }

    fun fromJson(json: String): List<GeoPoint> {
        val root = JSONObject(json)
        val arr = root.optJSONArray("points") ?: JSONArray()
        val result = mutableListOf<GeoPoint>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            result.add(
                GeoPoint(
                    name = obj.optString("name", ""),
                    category = obj.optString("category", ""),
                    latitude = obj.optDouble("latitude", 0.0),
                    longitude = obj.optDouble("longitude", 0.0),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                    note = obj.optString("note", "")
                )
            )
        }
        return result
    }

    fun writeToUri(context: Context, uri: Uri, json: String) {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(json.toByteArray(Charsets.UTF_8))
        }
    }

    fun readFromUri(context: Context, uri: Uri): String {
        context.contentResolver.openInputStream(uri)?.use { input ->
            return input.readBytes().toString(Charsets.UTF_8)
        }
        return "{}"
    }

    fun defaultFileName(): String {
        val ts = System.currentTimeMillis()
        return "noghteyab-backup-$ts.json"
    }
}
