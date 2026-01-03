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
                val sectionManager = SectionManager(database, locationManager)
                RouteViewModel(database, sectionManager)
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

            group.addresses.forEach { record ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = formatter.format(Instant.ofEpochMilli(record.time)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    // cityDisplay() を使用して、市町村以下の住所を表示
                    Text(
                        text = record.cityDisplay() ?: record.name ?: "不明な住所",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
