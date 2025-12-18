package com.studiokei.walkaround.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class TrackPoint(
    // 主キー。他のテーブルの startKey, endKey がこのIDを参照する
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // GPSデータを取得した時刻 (UNIX時間: ミリ秒単位)
    val time: Long,

    val latitude: Double,    // 緯度
    val longitude: Double,   // 経度
    val altitude: Double,    // 海抜高度 (メートル)
    val speed: Float,        // 速度 (m/s)
    val accuracy: Float,     // 水平方向の精度 (メートル)
    val verticalAccuracy: Float? = null, // 垂直方向の精度 (nullableとする場合)
    val heading: Float? = null      // 移動方向 (0-360度, nullableとする場合)
)
