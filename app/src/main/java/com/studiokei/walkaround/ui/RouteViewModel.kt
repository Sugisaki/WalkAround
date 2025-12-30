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
            val grouped = records.groupBy { it.sectionId }
                .map { (sectionId, addresses) ->
                    SectionGroup(sectionId, addresses)
                }
            RouteUiState(grouped)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = RouteUiState()
        )
}
