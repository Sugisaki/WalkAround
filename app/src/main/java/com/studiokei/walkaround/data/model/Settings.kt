package com.studiokei.walkaround.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class Settings(
    // 唯一のレコードであることを保証するため、プライマリキーを1に固定
    @PrimaryKey val id: Int = 1,

    // ログを記録する歩数の間隔 (N歩ごと)。例: 500
    val stepInterval: Int,

    // 距離表示に使う単位 ("km" または "mile")
    val displayUnit: String,

    // ダークモードの有効/無効 (手動設定用)
    val isDarkMode: Boolean,

    // 通知機能の有効/無効
    val isNotificationEnabled: Boolean,

    // 通知や音声案内の音量設定 (0.0〜1.0)
    val volume: Float,

    // メディアンフィルタの窓サイズ (0, 3, 5, 7, 9, 11, 13, 15, 17, 19)
    // 0の場合はフィルタを適用しない
    val medianWindowSize: Int = 7,

    // 位置情報の許容精度 (m)
    @ColumnInfo(defaultValue = "20.0")
    val locationAccuracyLimit: Float = 20.0f,

    // システムのテーマ設定に従うかどうか
    @ColumnInfo(defaultValue = "1")
    val followSystemTheme: Boolean = true,

    // 音声による住所案内を有効にするかどうか
    @ColumnInfo(defaultValue = "1")
    val isVoiceEnabled: Boolean = true
)
