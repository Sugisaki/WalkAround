package com.studiokei.walkaround.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.studiokei.walkaround.data.model.Section
import com.studiokei.walkaround.data.model.SectionSummary
import kotlinx.coroutines.flow.Flow

@Dao
interface SectionDao {
    /**
     * 全ての走行セクションの概要を取得します。
     * 住所情報の取得は SectionSummary クラスの @Relation アノテーションにより Room が自動で行います。
     * @Relation を含むクエリを安全に実行するため、@Transaction を付与しています。
     */
    @Transaction
    @Query("""
        SELECT 
            s.sectionId, 
            s.createdAtTimestamp AS startTimeMillis, 
            COALESCE(ss.steps, 0) AS steps,
            (SELECT COUNT(*) FROM tracks t WHERE t.time >= s.createdAtTimestamp AND (s.trackEndId IS NULL OR t.id <= s.trackEndId)) AS trackPointCount,
            s.distanceMeters,
            s.trackStartId,
            s.trackEndId
        FROM sections s
        LEFT JOIN step_segments ss ON s.sectionId = ss.sectionId
        ORDER BY s.createdAtTimestamp DESC
    """)
    fun getSectionSummaries(): Flow<List<SectionSummary>>

    @Query("SELECT * FROM sections WHERE sectionId = :sectionId")
    suspend fun getSectionById(sectionId: Long): Section?

    @Query("SELECT * FROM sections ORDER BY createdAtTimestamp DESC LIMIT 1")
    suspend fun getLastSection(): Section?

    /**
     * 本日の総歩数を取得します。
     */
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
