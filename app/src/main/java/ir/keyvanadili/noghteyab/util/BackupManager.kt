package ir.keyvanadili.noghteyab.util

import android.content.Context
import android.net.Uri
import ir.keyvanadili.noghteyab.data.GeoPoint
import org.json.JSONArray
import org.json.JSONObject

data class TrackPointBackup(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
)

data class TrackBackup(
    val name: String,
    val startTime: Long,
    val endTime: Long?,
    val distanceMeters: Double,
    val points: List<TrackPointBackup>
)

data class BackupData(
    val points: List<GeoPoint>,
    val categories: List<String>,
    val tracks: List<TrackBackup>
)

object BackupManager {

    fun toJson(points: List<GeoPoint>, categories: List<String>, tracks: List<TrackBackup>): String {
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

        val tracksArr = JSONArray()
        for (t in tracks) {
            val trackObj = JSONObject()
            trackObj.put("name", t.name)
            trackObj.put("startTime", t.startTime)
            if (t.endTime != null) trackObj.put("endTime", t.endTime)
            trackObj.put("distanceMeters", t.distanceMeters)
            val pointsArrJson = JSONArray()
            for (p in t.points) {
                val pObj = JSONObject()
                pObj.put("latitude", p.latitude)
                pObj.put("longitude", p.longitude)
                pObj.put("timestamp", p.timestamp)
                pointsArrJson.put(pObj)
            }
            trackObj.put("points", pointsArrJson)
            tracksArr.put(trackObj)
        }

        val root = JSONObject()
        root.put("app", "NoghteYab")
        root.put("version", 3)
        root.put("points", pointsArr)
        root.put("categories", categoriesArr)
        root.put("tracks", tracksArr)
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

        val tracksArrJson = root.optJSONArray("tracks") ?: JSONArray()
        val tracks = mutableListOf<TrackBackup>()
        for (i in 0 until tracksArrJson.length()) {
            val tObj = tracksArrJson.getJSONObject(i)
            val trackPointsArr = tObj.optJSONArray("points") ?: JSONArray()
            val trackPoints = mutableListOf<TrackPointBackup>()
            for (j in 0 until trackPointsArr.length()) {
                val pObj = trackPointsArr.getJSONObject(j)
                trackPoints.add(
                    TrackPointBackup(
                        latitude = pObj.optDouble("latitude", 0.0),
                        longitude = pObj.optDouble("longitude", 0.0),
                        timestamp = pObj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
            tracks.add(
                TrackBackup(
                    name = tObj.optString("name", ""),
                    startTime = tObj.optLong("startTime", System.currentTimeMillis()),
                    endTime = if (tObj.has("endTime")) tObj.optLong("endTime") else null,
                    distanceMeters = tObj.optDouble("distanceMeters", 0.0),
                    points = trackPoints
                )
            )
        }

        return BackupData(
            points = points,
            categories = categories.filter { it.isNotBlank() }.distinct(),
            tracks = tracks
        )
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
