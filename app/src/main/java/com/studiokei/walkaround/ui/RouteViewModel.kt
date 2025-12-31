package com.studiokei.walkaround.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studiokei.walkaround.data.database.AppDatabase
import com.studiokei.walkaround.data.model.AddressRecord
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class RouteUiState(
    val groupedAddresses: List<SectionGroup> = emptyList()
)

data class SectionGroup(
    val sectionId: Long?,
    val addresses: List<AddressRecord>
)

class RouteViewModel(
    private val database: AppDatabase
) : ViewModel() {

    val uiState: StateFlow<RouteUiState> = database.addressDao().getAllAddressRecords()
        .map { records ->
            // sectionId ごとにグループ化し、sectionId の降順（新しい順）で並べ替える
            // sectionId が null のものは最後に配置
            val grouped = records.groupBy { it.sectionId }
                .entries
                .sortedWith(compareByDescending<Map.Entry<Long?, List<AddressRecord>>> { it.key }.thenBy { 0 })
                .map { (sectionId, addresses) ->
                    // 各グループ内の住所も時間順（新しい順）に並んでいることを確認
                    SectionGroup(sectionId, addresses.sortedByDescending { it.time })
                }
            RouteUiState(grouped)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = RouteUiState()
        )
}
