package com.studiokei.walkaround.ui

import android.location.Location
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.studiokei.walkaround.data.database.AppDatabase
import com.studiokei.walkaround.data.model.Section
import com.studiokei.walkaround.data.model.TrackPoint
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

        // 住所の補完
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
                trackId = firstPoint.id
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
                    trackId = lastPoint.id
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
