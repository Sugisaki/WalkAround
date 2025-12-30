package com.studiokei.walkaround.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.studiokei.walkaround.data.model.Section
import com.studiokei.walkaround.data.model.SectionSummary
import kotlinx.coroutines.flow.Flow

@Dao
interface SectionDao {
    @Query("""
        SELECT 
            s.sectionId, 
            s.createdAtTimestamp as startTimeMillis, 
            COALESCE(ss.steps, 0) as steps,
            (SELECT COUNT(*) FROM tracks t WHERE t.time >= s.createdAtTimestamp AND (s.trackEndId IS NULL OR t.id <= s.trackEndId)) as trackPointCount,
            sar.addressLine as startAddressLine,
            sar.adminArea as startAdminArea,
            CASE WHEN dar.id != sar.id THEN dar.addressLine ELSE NULL END as destinationAddressLine,
            CASE WHEN dar.id != sar.id THEN dar.adminArea ELSE NULL END as destinationAdminArea
        FROM sections s
        LEFT JOIN step_segments ss ON s.sectionId = ss.sectionId
        LEFT JOIN (
            SELECT sectionId, addressLine, adminArea, id
            FROM address_records
            WHERE id IN (SELECT MIN(id) FROM address_records WHERE sectionId IS NOT NULL GROUP BY sectionId)
        ) sar ON s.sectionId = sar.sectionId
        LEFT JOIN (
            SELECT sectionId, addressLine, adminArea, id
            FROM address_records
            WHERE id IN (SELECT MAX(id) FROM address_records WHERE sectionId IS NOT NULL GROUP BY sectionId)
        ) dar ON s.sectionId = dar.sectionId
        ORDER BY s.createdAtTimestamp DESC
    """)
    fun getSectionSummaries(): Flow<List<SectionSummary>>

    @Query("SELECT * FROM sections WHERE sectionId = :sectionId")
    suspend fun getSectionById(sectionId: Long): Section?

    @Query("SELECT * FROM sections ORDER BY createdAtTimestamp DESC LIMIT 1")
    suspend fun getLastSection(): Section?

    @Query("""
        SELECT SUM(COALESCE(ss.steps, 0)) 
        FROM sections s
        JOIN step_segments ss ON s.sectionId = ss.sectionId
        WHERE s.createdAtTimestamp >= :startOfDay
    """)
    fun getTodayTotalSteps(startOfDay: Long): Flow<Int?>

    @Insert
    suspend fun insertSection(section: Section): Long

    @Update
    suspend fun updateSection(section: Section)

    @Delete
    suspend fun deleteSection(section: Section)
}
