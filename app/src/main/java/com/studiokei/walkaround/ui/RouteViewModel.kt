package com.studiokei.walkaround.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studiokei.walkaround.data.database.AppDatabase
import com.studiokei.walkaround.data.model.AddressRecord
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RouteUiState(
    val groupedAddresses: List<SectionGroup> = emptyList()
)

data class SectionGroup(
    val sectionId: Long?,
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

    // セクションを主軸としてデータを取得するように変更。
    // これにより、住所データがまだ記録されていない古いセクションも表示対象に含まれます。
    val uiState: StateFlow<RouteUiState> = database.sectionDao().getSectionsWithAddresses()
        .map { sectionsWithAddresses ->
            val groups = sectionsWithAddresses.map { item ->
                SectionGroup(
                    sectionId = item.section.sectionId,
                    // 各セクション内の住所を時間順（新しい順）に並べ替える
                    addresses = item.addresses.sortedByDescending { it.time }
                )
            }
            RouteUiState(groups)
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
