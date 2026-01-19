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
                // 各セクション内の住所を時間順（古い順）に並べ替えてからフィルタリング
                addresses = filterRepeatedAddresses(item.addresses.sortedBy { it.time })
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
     * 近接する住所をうろうろしている場合に住所の繰り返し表示を整理したい。
     * 重複する連続する住所パターンをフィルタリングするロジック:
     * 現在の行と2つ前の行の住所が同じで、かつ、1つ前の行と3つ前の行の住所が同じなら、
     * 現在の行と1つ前の行に非表示フラグを立てる。
     * 非表示にした行は、1つ前,2つ前,3つ前の対象から外す。
     *
     * @param addresses フィルタリング対象の住所レコードリスト（古い順にソートされていること）
     * @return フィルタリング後の住所レコードリスト
     */
    private fun filterRepeatedAddresses(addresses: List<AddressRecord>): List<AddressRecord> {
        if (addresses.size <= 3) { // 3つ前を参照するため、最低4要素が必要 (0,1,2,3)
            return addresses
        }

        val shouldDisplay = MutableList(addresses.size) { true }

        // shouldDisplayがtrueの要素のみを対象として、過去の要素を取得するヘルパー関数
        fun getVisibleCityDisplay(currentIndex: Int, offset: Int): String? {
            var count = 0
            for (i in currentIndex - 1 downTo 0) {
                if (shouldDisplay[i]) {
                    if (count == offset -1) { // offsetは1,2,3なので、-1する
                        return addresses[i].cityDisplay()
                    }
                    count++
                }
            }
            return null
        }

        // addressesリストを古い順にループ処理し、shouldDisplayフラグを決定
        for (i in 0 until addresses.size) {
            // 現在の行が表示対象外の場合は、比較対象から除外されるので何もしない
            if (!shouldDisplay[i]) continue

            val currentCity = addresses[i].cityDisplay() ?: continue

            // 3つ前の行まで存在するかチェック
            if (i < 3) continue // 3つ前を参照するには現在のインデックスが少なくとも3である必要がある

            val prev1City = getVisibleCityDisplay(i, 1)
            val prev2City = getVisibleCityDisplay(i, 2)
            val prev3City = getVisibleCityDisplay(i, 3)

            if (prev1City == null || prev2City == null || prev3City == null) {
                continue // 必要な過去の要素が見つからない場合はスキップ
            }
            
            // ロジックの適用
            if (currentCity == prev2City && prev1City == prev3City) {
                // 現在の行と1つ前の行に非表示フラグを立てる
                shouldDisplay[i] = false // 現在の行
                // 1つ前の行 (prev1City) の元のインデックスを見つける必要がある
                var prev1OriginalIndex = -1
                var count = 0
                for (j in i - 1 downTo 0) {
                    if (shouldDisplay[j]) {
                        if (count == 0) { // 1つ前の表示されるべき行
                            prev1OriginalIndex = j
                            break
                        }
                        count++
                    }
                }
                if (prev1OriginalIndex != -1) {
                    shouldDisplay[prev1OriginalIndex] = false
                }
            }
        }
        return addresses.filterIndexed { index, _ -> shouldDisplay[index] }
    }

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
