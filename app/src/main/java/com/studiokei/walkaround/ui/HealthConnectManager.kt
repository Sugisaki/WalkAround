package com.studiokei.walkaround.ui

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
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
        // Since the custom contract is still failing, we revert to a standard one for now.
        // This part needs to be fixed once the root cause of the 'PermissionController' error is found.
        return ActivityResultContracts.RequestMultiplePermissions()
    }

    suspend fun hasPermissions(): Boolean {
        if (!isAvailable) return false
        val permissions = setOf(HealthPermission.getReadPermission(StepsRecord::class))
        return healthConnectClient?.permissionController?.getGrantedPermissions()?.containsAll(permissions) ?: false
    }

    suspend fun readSteps(start: Instant, end: Instant): Long {
        if (!isAvailable || !hasPermissions()) return 0L

        val request = ReadRecordsRequest(
            recordType = StepsRecord::class,
            timeRangeFilter = TimeRangeFilter.between(start, end)
        )
        val response = healthConnectClient?.readRecords(request)
        return response?.records?.sumOf { it.count } ?: 0L
    }
}
