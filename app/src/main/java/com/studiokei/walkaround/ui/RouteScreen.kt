package com.studiokei.walkaround.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.studiokei.walkaround.data.database.AppDatabase
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun RouteScreen(
    modifier: Modifier = Modifier,
    onSectionClick: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: RouteViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val database = AppDatabase.getDatabase(context)
                val locationManager = LocationManager(context)
                // SectionManager から SectionService への改名に対応
                val sectionService = SectionService(database, locationManager)
                RouteViewModel(database, sectionService)
            }
        }
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    Scaffold(modifier = modifier) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(uiState.groupedAddresses) { group ->
                SectionBlock(
                    group = group,
                    formatter = dateTimeFormatter,
                    onUpdateClick = { group.sectionId?.let { viewModel.updateSectionAddresses(it) } },
                    onClick = { group.sectionId?.let { onSectionClick(it) } }
                )
            }
        }
    }
}

@Composable
fun SectionBlock(
    group: SectionGroup,
    formatter: DateTimeFormatter,
    onUpdateClick: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val sectionTitle = if (group.sectionId != null) {
                    "セクション ${group.sectionId}"
                } else {
                    "セクション外"
                }
                
                Text(
                    text = sectionTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (group.sectionId != null) {
                    OutlinedButton(onClick = onUpdateClick) {
                        Text("更新")
                    }
                }
            }

            // 住所リストをループして表示。
            // ViewModel側で降順（新しい順）に並んでいるため、最初の要素(index=0)が最新地点となる。
            group.addresses.forEachIndexed { index, record ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = formatter.format(Instant.ofEpochMilli(record.time)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    
                    // 最新の到達地点には🔴、それ以外の通過地点には⬆️を表示。
                    val icon = if (index == 0) "🔴 " else "⬆️ "
                    
                    // 最新の地点(index=0)は詳細な住所(addressDisplay)を、
                    // それ以外の経過地点は簡略化された住所(cityDisplay)を表示する。
                    val addressText = if (index == 0) {
                        record.addressDisplay() ?: record.name ?: "不明な住所"
                    } else {
                        record.cityDisplay() ?: record.name ?: "不明な住所"
                    }
                    
                    Text(
                        text = "$icon$addressText",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
