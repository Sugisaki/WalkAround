package com.studiokei.walkaround.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.studiokei.walkaround.data.model.TrackPoint
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackPointDao {
    @Query("SELECT * FROM tracks WHERE id BETWEEN :startId AND :endId ORDER BY time ASC")
    fun getTrackPointsForSection(startId: Long, endId: Long): Flow<List<TrackPoint>>

    @Query("SELECT * FROM tracks WHERE id BETWEEN :startId AND :endId AND accuracy <= :accuracyLimit ORDER BY time ASC")
    fun getAccurateTrackPointsForSection(startId: Long, endId: Long, accuracyLimit: Float): Flow<List<TrackPoint>>

    @Query("SELECT COUNT(*) FROM tracks")
    fun getTrackPointCount(): Flow<Int>

    @Insert
    suspend fun insertTrackPoint(trackPoint: TrackPoint): Long

    @Insert
    suspend fun insertAllTrackPoints(trackPoints: List<TrackPoint>): List<Long>

    @Query("DELETE FROM tracks WHERE id IN (:ids)")
    suspend fun deleteTrackPointsByIds(ids: List<Long>)

    @Query("SELECT * FROM tracks ORDER BY id DESC LIMIT 1")
    suspend fun getLastTrackPoint(): TrackPoint?

    @Query("SELECT * FROM tracks WHERE accuracy <= :accuracyLimit ORDER BY id DESC LIMIT 1")
    suspend fun getLastAccurateTrackPoint(accuracyLimit: Float): TrackPoint?

    @Query("DELETE FROM tracks WHERE id BETWEEN :startId AND :endId")
    suspend fun deleteByIdRange(startId: Long, endId: Long)
}
