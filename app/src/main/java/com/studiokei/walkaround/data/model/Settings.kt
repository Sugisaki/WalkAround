package com.studiokei.walkaround.data.model

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

    // ダークモードの有効/無効
    val isDarkMode: Boolean,

    // 通知機能の有効/無効
    val isNotificationEnabled: Boolean,

    // 通知や音声案内の音量設定 (0.0〜1.0)
    val volume: Float
)
