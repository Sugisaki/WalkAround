package com.studiokei.walkaround.ui

import android.location.Location
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.studiokei.walkaround.data.database.AppDatabase
import com.studiokei.walkaround.data.model.Section
import com.studiokei.walkaround.data.model.TrackPoint
import com.studiokei.walkaround.util.Constants
import com.studiokei.walkaround.util.applyMedianFilter
import kotlinx.coroutines.flow.first

class SectionManager(
    private val database: AppDatabase,
    private val locationManager: LocationManager
) {

    /**
     * セクションの表示に必要なデータ（軌跡）を準備し、必要に応じて住所の補完や距離の計算・保存を行います。
     */
    suspend fun prepareSectionAndGetTrack(section: Section): List<LatLng> {
        val settings = database.settingsDao().getSettings().first()
        val accuracyLimit = settings?.locationAccuracyLimit ?: 20.0f
        val windowSize = settings?.medianWindowSize ?: 7

        // 精度でフィルタリングされた地点を取得
        val accurateTrackPoints = if (section.trackStartId != null && section.trackEndId != null) {
            database.trackPointDao().getAccurateTrackPointsForSection(
                section.trackStartId,
                section.trackEndId,
                accuracyLimit
            ).first()
        } else {
            emptyList()
        }

        if (accurateTrackPoints.isEmpty()) return emptyList()

        // 住所の補完 (開始・終了地点)
        ensureAddressesSaved(section, accurateTrackPoints)

        // 軌跡の生成と平滑化
        val rawLatLngs = accurateTrackPoints.map { LatLng(it.latitude, it.longitude) }
        val filteredTrack = if (windowSize > 0 && rawLatLngs.size > 1) {
            applyMedianFilter(rawLatLngs, windowSize = windowSize)
        } else {
            rawLatLngs
        }

        // 距離の計算と保存 (未保存の場合のみ)
        if ((section.distanceMeters == null || section.distanceMeters == 0.0) && filteredTrack.size > 1) {
            val distance = calculateDistance(filteredTrack)
            Log.d("SectionManager", "Updating distance for section ${section.sectionId}: $distance m")
            database.sectionDao().updateSection(section.copy(distanceMeters = distance))
        }

        return filteredTrack
    }

    /**
     * セクションの全TrackPointを走査し、住所の変化点となる住所を取得・保存します。
     * 実行前に、当該セクションの既存住所データをすべて削除します。
     */
    suspend fun updateThoroughfareAddresses(sectionId: Long) {
        val section = database.sectionDao().getSectionById(sectionId) ?: return
        val startId = section.trackStartId ?: return
        val endId = section.trackEndId ?: return

        // 既存の住所データを削除してクリーンアップ
        Log.d("SectionManager", "Clearing existing address records for section $sectionId before update.")
        database.addressDao().deleteAddressesBySection(sectionId)

        // 全トラックデータを取得
        val allPoints = database.trackPointDao().getTrackPointsForSection(startId, endId).first()
        if (allPoints.isEmpty()) return

        Log.d("SectionManager", "Starting thoroughfare analysis for section $sectionId. Total points: ${allPoints.size}")

        var lastProcessedTime = 0L
        var lastAddressKey: String? = null
        var lastSavedTrackId: Long = -1

        // 1. 最初の住所を取得・保存 (trackStartId)
        val firstPoint = allPoints.first()
        val firstAddress = locationManager.getLocaleAddress(firstPoint.latitude, firstPoint.longitude)
        lastAddressKey = locationManager.getAddressKey(firstAddress)
        lastProcessedTime = firstPoint.time
        
        locationManager.saveAddressRecord(
            lat = firstPoint.latitude,
            lng = firstPoint.longitude,
            sectionId = sectionId,
            trackId = firstPoint.id,
            timestamp = firstPoint.time
        )
        lastSavedTrackId = firstPoint.id

        // 2. 続くデータを繰り返し処理
        for (i in 1 until allPoints.size) {
            val point = allPoints[i]
            
            // 前のデータから指定時間以上経過しているか
            if (point.time - lastProcessedTime >= Constants.ADDRESS_PROCESS_INTERVAL_MS) {
                val newKey = locationManager.saveAddressIfThoroughfareChanged(
                    lat = point.latitude,
                    lng = point.longitude,
                    sectionId = sectionId,
                    trackId = point.id,
                    timestamp = point.time,
                    lastAddressKey = lastAddressKey
                )
                
                if (newKey != null) {
                    lastAddressKey = newKey
                    lastSavedTrackId = point.id
                }
                lastProcessedTime = point.time
            }
        }

        // 3. 最後の住所を保存 (trackEndId)
        val lastPoint = allPoints.last()
        if (lastSavedTrackId != lastPoint.id) {
            Log.d("SectionManager", "Saving final address for section $sectionId at point ${lastPoint.id}.")
            locationManager.saveAddressRecord(
                lat = lastPoint.latitude,
                lng = lastPoint.longitude,
                sectionId = sectionId,
                trackId = lastPoint.id,
                timestamp = lastPoint.time
            )
        }

        Log.d("SectionManager", "Thoroughfare analysis completed for section $sectionId")
    }

    /**
     * 最初と最後の地点の住所が保存されているか確認し、なければ取得・保存します。
     */
    private suspend fun ensureAddressesSaved(section: Section, accuratePoints: List<TrackPoint>) {
        if (accuratePoints.isEmpty()) return

        val firstPoint = accuratePoints.first()
        val firstAddress = database.addressDao().getAddressBySectionAndTrack(section.sectionId, firstPoint.id)
        if (firstAddress == null) {
            Log.d("SectionManager", "Fetching start address for section ${section.sectionId}")
            locationManager.saveAddressRecord(
                lat = firstPoint.latitude,
                lng = firstPoint.longitude,
                sectionId = section.sectionId,
                trackId = firstPoint.id,
                timestamp = firstPoint.time
            )
        }

        if (accuratePoints.size > 1) {
            val lastPoint = accuratePoints.last()
            val lastAddress = database.addressDao().getAddressBySectionAndTrack(section.sectionId, lastPoint.id)
            if (lastAddress == null) {
                Log.d("SectionManager", "Fetching end address for section ${section.sectionId}")
                locationManager.saveAddressRecord(
                    lat = lastPoint.latitude,
                    lng = lastPoint.longitude,
                    sectionId = section.sectionId,
                    trackId = lastPoint.id,
                    timestamp = lastPoint.time
                )
            }
        }
    }

    /**
     * 地点のリストから総移動距離を計算します。
     */
    private fun calculateDistance(points: List<LatLng>): Double {
        var totalDistance = 0.0
        val result = FloatArray(1)
        for (i in 0 until points.size - 1) {
            Location.distanceBetween(
                points[i].latitude, points[i].longitude,
                points[i + 1].latitude, points[i + 1].longitude,
                result
            )
            totalDistance += result[0]
        }
        return totalDistance
    }
}
