package com.studiokei.walkaround.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

/**
 * 経路履歴画面。
 * セクションごとにグループ化された住所録を表示します。
 * 
 * @param scrollToSectionId このIDが指定されている場合、そのセクションまで自動スクロールします。
 * @param onScrollFinished スクロール完了時に呼び出されるコールバック。
 * @param onSectionClick セクションがクリックされた際（地図表示など）の処理。
 */
@Composable
fun RouteScreen(
    modifier: Modifier = Modifier,
    scrollToSectionId: Long? = null,
    onScrollFinished: () -> Unit = {},
    onSectionClick: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: RouteViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val database = AppDatabase.getDatabase(context)
                val locationManager = LocationManager(context)
                val sectionProcessor = SectionProcessor(database, locationManager)
                RouteViewModel(database, sectionProcessor)
            }
        }
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    // リストの状態を保持し、スクロール操作を可能にする
    val listState = rememberLazyListState()

    val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    // scrollToSectionId が指定された場合に、該当アイテムまでスクロールする処理
    LaunchedEffect(scrollToSectionId, uiState.groupedAddresses) {
        if (scrollToSectionId != null && uiState.groupedAddresses.isNotEmpty()) {
            // 指定されたセクションIDを持つアイテムのインデックスを検索
            val index = uiState.groupedAddresses.indexOfFirst { it.sectionId == scrollToSectionId }
            if (index != -1) {
                // 該当アイテムまで即座にスクロール
                listState.scrollToItem(index)
                // スクロール完了を通知してIDをリセットさせる
                onScrollFinished()
            }
        }
    }

    Scaffold(modifier = modifier) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            state = listState, // 状態を紐付け
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(uiState.groupedAddresses) { _, group ->
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

            group.addresses.forEachIndexed { index, record ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = formatter.format(Instant.ofEpochMilli(record.time)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    
                    // 最新の地点には🔴、それ以外には⬆️を表示。
                    val icon = if (index == 0) "🔴 " else "⬆️ "
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
