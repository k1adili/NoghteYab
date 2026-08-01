package ir.keyvanadili.noghteyab.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GeoPointDao {

    @Insert
    suspend fun insert(point: GeoPoint): Long

    @Update
    suspend fun update(point: GeoPoint)

    @Delete
    suspend fun delete(point: GeoPoint)

    @Query("SELECT * FROM points ORDER BY timestamp DESC")
    fun getAll(): Flow<List<GeoPoint>>

    @Query("""
        SELECT * FROM points
        WHERE name LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%'
        ORDER BY timestamp DESC
    """)
    fun search(query: String): Flow<List<GeoPoint>>

    @Query("SELECT * FROM points WHERE id = :id")
    suspend fun getById(id: Long): GeoPoint?

    @Query("SELECT DISTINCT category FROM points WHERE category != '' ORDER BY category")
    fun getCategories(): Flow<List<String>>

    @Query("SELECT * FROM points ORDER BY timestamp DESC")
    suspend fun getAllOnce(): List<GeoPoint>
}
