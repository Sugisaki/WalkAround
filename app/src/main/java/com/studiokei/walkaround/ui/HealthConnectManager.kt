package com.studiokei.walkaround.ui

import android.content.Context
import android.util.Log
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant

class HealthConnectManager(private val context: Context) {
    private var healthConnectClient: HealthConnectClient? = null

    // Google Playのポリシーに対応するため、一時的にヘルスコネクトを無効化します。
    // isAvailableが常にfalseを返すようにすることで、ヘルスコネクト関連の機能が呼び出されなくなります。
    // 後で機能を復活させる際は、この部分を元のコードに戻してください。
    val isAvailable: Boolean
        get() = false // HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    init {
        if (isAvailable) {
            healthConnectClient = HealthConnectClient.getOrCreate(context)
        }
    }

    fun requestPermissionsContract(): ActivityResultContract<Array<String>, Map<String, Boolean>> {
        return ActivityResultContracts.RequestMultiplePermissions()
    }

    suspend fun hasPermissions(): Boolean {
        if (!isAvailable) return false
        val permissions = setOf(HealthPermission.getReadPermission(StepsRecord::class))
        return try {
            healthConnectClient?.permissionController?.getGrantedPermissions()?.containsAll(permissions) ?: false
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "権限確認中にエラーが発生しました", e)
            false
        }
    }

    /**
     * 指定期間の歩数を取得します。
     * 複数のアプリから歩数データが提供されている場合、最も歩数が多いアプリの合計値を採用します。
     */
    suspend fun readSteps(start: Instant, end: Instant): Long {
        if (!isAvailable) {
            Log.w("HealthConnectManager", "ヘルスコネクトが利用不可です")
            return 0L
        }
        if (!hasPermissions()) {
            Log.w("HealthConnectManager", "ヘルスコネクトの読み取り権限がありません")
            return 0L
        }

        return try {
            // ----- 比較用: AggregateRequestによる重複排除ありの歩数をログ出力 -----
            try {
                val aggregateRequest = AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
                val aggregateResponse = healthConnectClient?.aggregate(aggregateRequest)
                val aggregateSteps = aggregateResponse?.get(StepsRecord.COUNT_TOTAL) ?: 0L
                Log.d("HealthConnectManager", "参考 (Aggregate/重複排除あり): $aggregateSteps 歩")
            } catch (e: Exception) {
                Log.w("HealthConnectManager", "Aggregateリクエストに失敗", e)
            }

            // ----- メインロジック: 個別レコードから最優先アプリの歩数を採用 -----

            // 1. 指定期間の個別レコードをすべて取得
            val rawRequest = ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
            val rawResponse = healthConnectClient?.readRecords(rawRequest)
            val records = rawResponse?.records ?: emptyList()

            if (records.isEmpty()) {
                Log.i("HealthConnectManager", "対象期間のレコードが見つかりませんでした。歩数0を返します。")
                return 0L
            }

            // 2. アプリ（パッケージ名）ごとに歩数を合算
            val stepsByPackage = records.groupBy { it.metadata.dataOrigin.packageName }
                .mapValues { entry -> entry.value.sumOf { it.count } }

            // 3. 各アプリの合計をログ出力
            stepsByPackage.forEach { (pkg, sum) ->
                Log.d("HealthConnectManager", "アプリ別合計: $pkg -> $sum 歩")
            }

            // 4. 最も歩数が多いアプリのエントリーを探す
            val maxEntry = stepsByPackage.maxByOrNull { it.value }
            val selectedSteps = maxEntry?.value ?: 0L
            val selectedPackage = maxEntry?.key ?: "none"

            Log.i("HealthConnectManager", "採用歩数 (最優先アプリ: $selectedPackage): $selectedSteps 歩")

            // 5. 採用した歩数を返す
            selectedSteps

        } catch (e: Exception) {
            Log.e("HealthConnectManager", "歩数取得処理中にエラーが発生しました", e)
            0L
        }
    }
}
