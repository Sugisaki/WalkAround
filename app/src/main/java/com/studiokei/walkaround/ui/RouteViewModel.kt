package com.studiokei.walkaround.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studiokei.walkaround.data.database.AppDatabase
import com.studiokei.walkaround.data.model.AddressRecord
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RouteUiState(
    val groupedAddresses: List<SectionGroup> = emptyList(),
    val displayUnit: String = "km" // 距離の表示単位を追加
)

/**
 * 画面表示用のセクショングループ。
 * 
 * @property sectionId セクションのID
 * @property createdAtTimestamp セクションが作成された日時（UNIX時間：ミリ秒）
 * @property distanceMeters このセクションで歩いた距離（メートル）
 * @property steps このセクションの合計歩数
 * @property addresses このセクションに属する住所レコードのリスト
 */
data class SectionGroup(
    val sectionId: Long?,
    val createdAtTimestamp: Long?,
    val distanceMeters: Double?,
    val steps: Int,
    val addresses: List<AddressRecord>
)

/**
 * 経路履歴画面の状態を管理するViewModel。
 * 全ての走行セクションと、それに紐づく住所データを一括で管理します。
 */
class RouteViewModel(
    private val database: AppDatabase,
    private val sectionProcessor: SectionProcessor
) : ViewModel() {

    // セクションデータと設定データを組み合わせてUiStateを生成
    val uiState: StateFlow<RouteUiState> = combine(
        database.sectionDao().getSectionsWithAddresses(),
        database.settingsDao().getSettings()
    ) { sectionsWithAddresses, settings ->
        val groups = sectionsWithAddresses.map { item ->
            SectionGroup(
                sectionId = item.section.sectionId,
                createdAtTimestamp = item.section.createdAtTimestamp,
                distanceMeters = item.section.distanceMeters,
                // 各セグメントの歩数を合計
                steps = item.stepSegments.sumOf { it.steps },
                // 各セクション内の住所を時間順（新しい順）に並べ替える
                addresses = item.addresses.sortedByDescending { it.time }
            )
        }
        RouteUiState(
            groupedAddresses = groups,
            displayUnit = settings?.displayUnit ?: "km"
        )
    }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RouteUiState()
    )

    /**
     * 指定されたセクションの住所情報を解析し、丁目の変化点などを再生成します。
     * 
     * @param sectionId 対象のセクションID
     */
    fun updateSectionAddresses(sectionId: Long) {
        viewModelScope.launch {
            sectionProcessor.updateThoroughfareAddresses(sectionId)
        }
    }
}
