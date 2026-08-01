package ir.keyvanadili.noghteyab.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "points")
data class GeoPoint(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val note: String = ""
)
