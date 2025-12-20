package com.studiokei.walkaround.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.studiokei.walkaround.data.model.TrackPoint
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackPointDao {
    @Query("SELECT * FROM tracks WHERE id BETWEEN :startId AND :endId ORDER BY time ASC")
    fun getTrackPointsBetweenIds(startId: Long, endId: Long): Flow<List<TrackPoint>>

    @Query("SELECT COUNT(*) FROM tracks")
    fun getTrackPointCount(): Flow<Int>

    @Insert
    suspend fun insertTrackPoint(trackPoint: TrackPoint): Long

    @Insert
    suspend fun insertAllTrackPoints(trackPoints: List<TrackPoint>): List<Long>

    @Query("DELETE FROM tracks WHERE id IN (:ids)")
    suspend fun deleteTrackPointsByIds(ids: List<Long>)
}
