package com.studiokei.walkaround.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sections",
    // TrackPointテーブルのIDを参照する外部キーを定義
    foreignKeys = [
        ForeignKey(
            entity = TrackPoint::class,
            parentColumns = ["id"],
            childColumns = ["trackStartId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = TrackPoint::class,
            parentColumns = ["id"],
            childColumns = ["trackEndId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("trackStartId"),
        Index("trackEndId")
    ]
)
data class Section(
    // セクションのユニークID
    @PrimaryKey(autoGenerate = true)
    val sectionId: Long = 0,

    // セクション開始時の TrackPoint.id (外部キー)
    val trackStartId: Long? = null,

    // セクション終了時の TrackPoint.id (外部キー)
    val trackEndId: Long? = null,

    // このセクションで歩いた総距離 (メートル)
    val distanceMeters: Double? = null,

    // このセクションの所要時間 (秒)
    val durationSeconds: Long? = null,

    // 平均速度 (km/h)
    val averageSpeedKmh: Double? = null,

    // セクションが作成/完了した時刻 (UNIX時間: ミリ秒単位)
    val createdAtTimestamp: Long? = null
)
