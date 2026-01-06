package com.studiokei.walkaround.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.studiokei.walkaround.data.model.AddressRecord
import com.studiokei.walkaround.data.model.Section
import com.studiokei.walkaround.data.model.SectionSummary
import com.studiokei.walkaround.data.model.StepSegment
import kotlinx.coroutines.flow.Flow

/**
 * セクションとそのセクションに紐づく全ての住所レコード、および歩数データを保持するデータ構造。
 * Route画面での一覧表示に使用します。
 */
data class SectionWithAddresses(
    @Embedded val section: Section,
    @Relation(
        parentColumn = "sectionId",
        entityColumn = "sectionId"
    )
    val addresses: List<AddressRecord>,
    
    // セクションに紐づく歩数データを取得
    @Relation(
        parentColumn = "sectionId",
        entityColumn = "sectionId"
    )
    val stepSegments: List<StepSegment>
)

@Dao
interface SectionDao {
    /**
     * 全ての走行セクションの概要を取得します（Home画面用）。
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

    /**
     * 全ての走行セクションと、それぞれに紐づく住所レコードを取得します（Route画面用）。
     * 住所がないセクションも取得対象に含まれます。
     */
    @Transaction
    @Query("SELECT * FROM sections ORDER BY createdAtTimestamp DESC")
    fun getSectionsWithAddresses(): Flow<List<SectionWithAddresses>>

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
