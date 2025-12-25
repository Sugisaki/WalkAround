package com.studiokei.walkaround.util

import com.google.android.gms.maps.model.LatLng

/**
 * 軌跡データにメディアンフィルタを適用して平滑化します。
 * 各点の緯度・経度を、その周囲の窓（ウィンドウ）内のメディアン（中央値）で置き換えます。
 */
fun applyMedianFilter(points: List<LatLng>, windowSize: Int = 7): List<LatLng> {
    if (points.size < 2) return points
    
    val result = mutableListOf<LatLng>()
    val halfWindow = windowSize / 2
    
    for (i in points.indices) {
        val start = (i - halfWindow).coerceAtLeast(0)
        val end = (i + halfWindow).coerceAtMost(points.size - 1)
        
        val window = points.subList(start, end + 1)
        
        val sortedLats = window.map { it.latitude }.sorted()
        val sortedLngs = window.map { it.longitude }.sorted()
        
        val medianLat = sortedLats[sortedLats.size / 2]
        val medianLng = sortedLngs[sortedLngs.size / 2]
        
        result.add(LatLng(medianLat, medianLng))
    }
    
    return result
}
