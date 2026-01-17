package com.studiokei.walkaround.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.fitness.FitnessLocal
import com.google.android.gms.fitness.LocalRecordingClient
import com.google.android.gms.fitness.data.LocalDataType
import com.google.android.gms.fitness.request.LocalDataReadRequest
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Google Fit Recording API を使って歩数データを管理するクラス。
 *
 * @param context アプリケーションコンテキスト。
 */
class FitnessHistoryManager(private val context: Context) {

    private val localRecordingClient = FitnessLocal.getLocalRecordingClient(context)

    /**
     * Fitness API (Recording API) がこのデバイスで利用可能かを確認します。
     * これには、Google Play Servicesのバージョンが適切であるかのチェックが含まれます。
     * @return trueの場合、利用可能です。
     */
    fun isGooglePlayServicesAvailable(): Boolean {
        val availability = GoogleApiAvailability.getInstance()
        val resultCode = availability.isGooglePlayServicesAvailable(context, LocalRecordingClient.LOCAL_RECORDING_CLIENT_MIN_VERSION_CODE)
        if (resultCode != ConnectionResult.SUCCESS) {
            val errorString = availability.getErrorString(resultCode)
            Log.w(TAG, "isFitnessApiAvailable: Google Play Services check failed. Result code: $resultCode ($errorString)")
            return false
        } else {
            Log.i(TAG, "isFitnessApiAvailable: Google Play Services check passed.")
        }

        Log.i(TAG, "isFitnessApiAvailable: All checks passed. API is available.")
        return true
    }

    /**
     * 身体活動データへのアクセス権限が付与されているかを確認します。
     * @return trueの場合、権限が付与されています。
     */
    fun hasActivityRecognitionPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 歩数データのバックグラウンド収集を開始（購読）します。
     */
    @RequiresPermission(Manifest.permission.ACTIVITY_RECOGNITION)
    suspend fun subscribeToSteps() {
        try {
            localRecordingClient.subscribe(LocalDataType.TYPE_STEP_COUNT_DELTA).await()
            Log.i(TAG, "Successfully subscribed to step count delta.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to subscribe to step count delta.", e)
            throw e
        }
    }

    /**
     * 指定された期間の歩数データを日別に集計して読み取ります。
     */
    suspend fun readDailySteps(days: Int = 7): List<Pair<String, Long>> {
        if (!hasActivityRecognitionPermission()) {
            Log.w(TAG, "Attempted to read steps without ACTIVITY_RECOGNITION permission.")
            return emptyList()
        }

        // 日付の境界（午前0時）に合わせるため、LocalDate を使用します。
        // 今日のデータも含まれるように、期間の終わりを明日の午前0時に設定します。
        val endTime = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault())
        // 指定された日数前の午前0時を開始時刻に設定します。
        val startTime = endTime.minusDays(days.toLong())

        val readRequest = LocalDataReadRequest.Builder()
            .aggregate(LocalDataType.TYPE_STEP_COUNT_DELTA)
            .bucketByTime(1, TimeUnit.DAYS)
            .setTimeRange(startTime.toEpochSecond(), endTime.toEpochSecond(), TimeUnit.SECONDS)
            .build()

        // (LocalDate, Long) のペアを格納するリスト。ソートのために日付オブジェクトを保持
        val stepData = mutableListOf<Pair<LocalDate, Long>>()

        try {
            val response = localRecordingClient.readData(readRequest).await()
            for (bucket in response.buckets) {
                for (dataSet in bucket.dataSets) {
                    for (dp in dataSet.dataPoints) {
                        // データポイントの開始時刻から LocalDate を取得
                        val date = LocalDateTime.ofInstant(
                            Instant.ofEpochSecond(dp.getStartTime(TimeUnit.SECONDS)),
                            ZoneId.systemDefault()
                        ).toLocalDate()
                        val steps = dp.getValue(dp.dataType.fields.first()).asInt().toLong()
                        if (steps > 0) {
                           stepData.add(date to steps)
                        }
                    }
                }
            }
            Log.i(TAG, "Successfully read daily steps: $stepData")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read daily steps from Recording API.", e)
            return emptyList()
        }

        // 日付の降順でソートしてから、表示用の文字列に変換
        val dateFormatter = DateTimeFormatter.ofPattern("M月d日(E)", Locale.JAPAN)
        return stepData.sortedByDescending { it.first }.map { (date, steps) ->
            date.format(dateFormatter) to steps
        }
    }

    companion object {
        private const val TAG = "FitnessHistoryManager"
    }
}
