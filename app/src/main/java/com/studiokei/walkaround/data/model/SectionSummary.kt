package com.studiokei.walkaround.data.model

import androidx.room.Relation

/**
 * 走行セクションの概要情報を保持するデータモデル。
 * セクション一覧画面の表示用に使用されます。
 * リレーションシップ（@Relation）を使用して、開始・終了地点の住所データを取得します。
 */
data class SectionSummary(
    val sectionId: Long,
    val startTimeMillis: Long,
    val steps: Int,
    val trackPointCount: Int,
    val distanceMeters: Double? = null,

    // リレーション解決のためのキー
    val trackStartId: Long? = null,
    val trackEndId: Long? = null,

    // 開始トラックIDに紐づく住所レコードを取得
    @Relation(
        parentColumn = "trackStartId",
        entityColumn = "trackId"
    )
    val startAddress: AddressRecord? = null,

    // 終了トラックIDに紐づく住所レコードを取得
    @Relation(
        parentColumn = "trackEndId",
        entityColumn = "trackId"
    )
    val destinationAddress: AddressRecord? = null
) {
    /**
     * 出発地の市区町村以下の住所表示を取得します。
     * AddressRecord に集約された詳細な表示ロジック（丁目の切り捨て、枝番削除など）を再利用します。
     */
    fun startCityDisplay(): String? = startAddress?.cityDisplay()

    /**
     * 目的地の市区町村以下の住所表示を取得します。
     */
    fun destinationCityDisplay(): String? = destinationAddress?.cityDisplay()
}
