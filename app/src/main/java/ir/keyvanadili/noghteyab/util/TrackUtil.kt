package ir.keyvanadili.noghteyab.util

import ir.keyvanadili.noghteyab.data.TrackPointEntity
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object TrackUtil {

    private const val EARTH_RADIUS_METERS = 6371000.0

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    fun totalDistanceMeters(points: List<TrackPointEntity>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            total += haversineMeters(prev.latitude, prev.longitude, curr.latitude, curr.longitude)
        }
        return total
    }

    fun formatDistance(meters: Double): String {
        return if (meters >= 1000) "%.2f کیلومتر".format(meters / 1000.0)
        else "%.0f متر".format(meters)
    }
}
