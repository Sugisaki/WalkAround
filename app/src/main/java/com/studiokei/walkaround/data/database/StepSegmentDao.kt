package com.studiokei.walkaround.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.studiokei.walkaround.data.model.StepSegment
import kotlinx.coroutines.flow.Flow

@Dao
interface StepSegmentDao {
    @Query("SELECT * FROM step_segments WHERE sectionId = :sectionId ORDER BY startTime ASC")
    fun getStepSegmentsForSection(sectionId: Long): Flow<List<StepSegment>>

    @Insert
    suspend fun insertStepSegment(stepSegment: StepSegment): Long

    @Insert
    suspend fun insertAllStepSegments(stepSegments: List<StepSegment>): List<Long>
}
