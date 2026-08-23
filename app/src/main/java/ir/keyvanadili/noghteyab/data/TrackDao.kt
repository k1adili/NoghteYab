package ir.keyvanadili.noghteyab.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {

    @Insert
    suspend fun insertTrack(track: TrackEntity): Long

    @Insert
    suspend fun insertPoint(point: TrackPointEntity): Long

    @Query("UPDATE tracks SET endTime = :endTime, distanceMeters = :distance WHERE id = :trackId")
    suspend fun finishTrack(trackId: Long, endTime: Long, distance: Double)

    @Query("SELECT * FROM tracks ORDER BY startTime DESC")
    fun getAllTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM track_points WHERE trackId = :trackId ORDER BY timestamp")
    suspend fun getPointsOnce(trackId: Long): List<TrackPointEntity>

    @Query("DELETE FROM track_points WHERE trackId = :trackId")
    suspend fun deletePointsForTrack(trackId: Long)

    @Delete
    suspend fun deleteTrack(track: TrackEntity)

    suspend fun deleteTrackWithPoints(track: TrackEntity) {
        deletePointsForTrack(track.id)
        deleteTrack(track)
    }
}
