package com.studiokei.walkaround.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.studiokei.walkaround.util.DateTimeFormatUtils
import java.time.Instant

/**
 * 経路履歴画面。
 * セクションごとにグループ化された住所録を表示します。
 * 
 * @param scrollToSectionId このIDが指定されている場合、そのセクションまで自動スクロールし、ハイライト表示します。
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

    // すでにスクロール済みのIDを保持し、リスト更新時の再スクロールを防ぐ
    var lastScrolledId by rememberSaveable { mutableStateOf<Long?>(null) }

    // scrollToSectionId が指定された場合に、該当アイテムまでスクロールする処理
    LaunchedEffect(scrollToSectionId, uiState.groupedAddresses) {
        if (scrollToSectionId != null && 
            scrollToSectionId != lastScrolledId && 
            uiState.groupedAddresses.isNotEmpty()
        ) {
            // 指定されたセクションIDを持つアイテムのインデックスを検索
            val index = uiState.groupedAddresses.indexOfFirst { it.sectionId == scrollToSectionId }
            if (index != -1) {
                // 該当アイテムまで即座にスクロール
                listState.scrollToItem(index)
                lastScrolledId = scrollToSectionId
                // スクロール完了を通知
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
                    displayUnit = uiState.displayUnit,
                    isHighlighted = group.sectionId == scrollToSectionId, // ハイライト判定
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
    displayUnit: String,
    isHighlighted: Boolean = false, // ハイライト引数を追加
    onUpdateClick: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                // ハイライト時はボーダーを表示
                if (isHighlighted) {
                    Modifier.border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.medium
                    )
                } else Modifier
            ),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1行目：日付とセクションID
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左側：日付表示
                val dateText = group.createdAtTimestamp?.let {
                    DateTimeFormatUtils.headerDateFormatter.format(Instant.ofEpochMilli(it))
                } ?: "日付不明"

                Text(
                    text = dateText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // 右側：セクションIDと更新ボタン
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (group.sectionId != null) {
                        // セクションIDを小さく表示
                        Text(
                            text = "Sec: ${group.sectionId}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        // 更新ボタンを少しコンパクトに
                        OutlinedButton(
                            onClick = onUpdateClick,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                "更新",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            // 2行目：距離と歩数
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左側：距離
                val distanceDisplay = group.distanceMeters?.let { meters ->
                    if (displayUnit == "mile") {
                        val miles = meters / 1609.34
                        "距離: %.2f mile".format(miles)
                    } else {
                        "距離: %.2f km".format(meters / 1000.0)
                    }
                } ?: "距離: ---"

                Text(
                    text = distanceDisplay,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 右側：歩数
                if (group.steps > 0) {
                    Text(
                        text = "歩数: ${group.steps}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 境界線を追加（細く薄い線）
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // 住所リスト
            // ViewModelでフィルタリング済みのリストを新しい順に表示
            group.addresses.reversed().forEachIndexed { index, record ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = DateTimeFormatUtils.dateTimeFormatter.format(Instant.ofEpochMilli(record.time)),
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
