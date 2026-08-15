package ir.keyvanadili.noghteyab.util

import android.content.Context
import android.net.Uri
import ir.keyvanadili.noghteyab.data.GeoPoint
import org.json.JSONArray
import org.json.JSONObject

data class BackupData(
    val points: List<GeoPoint>,
    val categories: List<String>
)

object BackupManager {

    fun toJson(points: List<GeoPoint>, categories: List<String>): String {
        val pointsArr = JSONArray()
        for (p in points) {
            val obj = JSONObject()
            obj.put("name", p.name)
            obj.put("category", p.category)
            obj.put("latitude", p.latitude)
            obj.put("longitude", p.longitude)
            obj.put("timestamp", p.timestamp)
            obj.put("note", p.note)
            pointsArr.put(obj)
        }

        val categoriesArr = JSONArray()
        for (c in categories) {
            categoriesArr.put(c)
        }

        val root = JSONObject()
        root.put("app", "NoghteYab")
        root.put("version", 2)
        root.put("points", pointsArr)
        root.put("categories", categoriesArr)
        return root.toString(2)
    }

    fun fromJson(json: String): BackupData {
        val root = JSONObject(json)

        val pointsArrJson = root.optJSONArray("points") ?: JSONArray()
        val points = mutableListOf<GeoPoint>()
        for (i in 0 until pointsArrJson.length()) {
            val obj = pointsArrJson.getJSONObject(i)
            points.add(
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

        val categoriesArrJson = root.optJSONArray("categories") ?: JSONArray()
        val categories = mutableListOf<String>()
        for (i in 0 until categoriesArrJson.length()) {
            categories.add(categoriesArrJson.optString(i, ""))
        }
        // Backward-compatible fallback: older backups (v1) had no "categories" key,
        // so recover distinct categories from the points themselves.
        if (categories.isEmpty()) {
            categories.addAll(points.mapNotNull { it.category.ifBlank { null } }.distinct())
        }

        return BackupData(points = points, categories = categories.filter { it.isNotBlank() }.distinct())
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
