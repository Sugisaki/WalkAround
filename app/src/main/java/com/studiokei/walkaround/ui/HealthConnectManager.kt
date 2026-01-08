package com.studiokei.walkaround.ui

import android.content.Context
import android.util.Log
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant

class HealthConnectManager(private val context: Context) {
    private var healthConnectClient: HealthConnectClient? = null

    val isAvailable: Boolean
        get() = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

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
     * AggregateRequestを使用することで重複排除された総計を取得します。
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
            val request = AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
            val response = healthConnectClient?.aggregate(request)
            val steps = response?.get(StepsRecord.COUNT_TOTAL) ?: 0L
            Log.d("HealthConnectManager", "歩数取得成功: $steps (期間: $start - $end)")
            steps
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "歩数の集計中にエラーが発生しました", e)
            0L
        }
    }
}
