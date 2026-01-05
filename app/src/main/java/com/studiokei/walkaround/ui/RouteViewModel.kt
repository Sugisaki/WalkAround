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
 * 保存された住所データをセクションごとにグループ化して提供します。
 */
class RouteViewModel(
    private val database: AppDatabase,
    private val sectionService: SectionService
) : ViewModel() {

    val uiState: StateFlow<RouteUiState> = database.addressDao().getAllAddressRecords()
        .map { records ->
            // 住所レコードをセクションIDごとにグループ化し、有効なセクションのみを抽出する
            val grouped = records.groupBy { it.sectionId }
                // sectionId が null のもの（どの走行セクションにも属さない住所）を除外
                .filterKeys { it != null }
                .entries
                // sectionId の降順（新しい走行セクション順）で並べ替える
                .sortedByDescending { it.key }
                .map { (sectionId, addresses) ->
                    // 各グループ内の住所も、記録時刻の降順（新しい順）に並べ替えて保持
                    SectionGroup(sectionId, addresses.sortedByDescending { it.time })
                }
            RouteUiState(grouped)
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
            sectionService.updateThoroughfareAddresses(sectionId)
        }
    }
}
