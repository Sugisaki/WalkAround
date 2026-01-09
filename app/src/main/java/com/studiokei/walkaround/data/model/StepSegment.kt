package com.studiokei.walkaround.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "step_segments",
    // どのセクションに属するかを示す外部キー
    foreignKeys = [
        ForeignKey(
            entity = Section::class,
            parentColumns = ["sectionId"],
            childColumns = ["sectionId"],
            onDelete = ForeignKey.CASCADE // Sectionが削除されたら関連するStepIntervalも削除
        )
    ],
    indices = [Index(value = ["sectionId"])]
)
data class StepSegment(
    // インターバルのユニークID
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // 所属する Section のID (外部キー)
    val sectionId: Long,

    // この区間で実際に計測された歩数 (通常は Settings.stepInterval と同値)
    val steps: Int,

    // 区間開始時刻 (UNIX時間: ミリ秒単位)
    val startTime: Long,

    // 区間終了時刻 (UNIX時間: ミリ秒単位)
    val endTime: Long
)
