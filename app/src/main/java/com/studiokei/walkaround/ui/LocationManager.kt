package com.studiokei.walkaround.ui

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

class LocationManager(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission") // Permissions are handled in HomeScreen
    fun requestLocationUpdates(): Flow<Location> = callbackFlow {
        Log.d("LocationManager", "requestLocationUpdates called")
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000L // 1 second interval
        )
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(500L) // Minimum interval of 0.5 seconds
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let {
                    Log.d("LocationManager", "New location received: $it")
                    trySend(it)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )

        awaitClose {
            Log.d("LocationManager", "Stopping location updates")
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    /**
     * キャッシュを避けて、強制的に最新の位置を1回だけ取得する
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? = suspendCancellableCoroutine { continuation ->
        // getCurrentLocation はキャッシュを返すことがあるため、
        // 確実に最新を取得したい場合は短時間の LocationRequest (1回限定) を使用する
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 0)
            .setMaxUpdates(1)
            .setDurationMillis(10000) // 最大10秒待機
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation
                if (!continuation.isCompleted) {
                    continuation.resume(location)
                }
                // 明示的に解除（setMaxUpdates(1)により自動でも解除されますが念のため）
                fusedLocationClient.removeLocationUpdates(this)
            }
        }

        fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
            .addOnFailureListener { e ->
                Log.e("LocationManager", "Forced location update failed", e)
                if (!continuation.isCompleted) continuation.resume(null)
            }

        continuation.invokeOnCancellation {
            fusedLocationClient.removeLocationUpdates(callback)
        }
    }

    /**
     * 指定された座標の住所を取得する。
     * まずデフォルトのLocaleで国コードを取得し、可能であればその国の言語設定で住所を取得し直す。
     */
    suspend fun getAddressFromLocation(latitude: Double, longitude: Double): String? = withContext(Dispatchers.IO) {
        // 1. まず国コードを取得するためにデフォルトのLocaleを使用
        val initialGeocoder = Geocoder(context, Locale.getDefault())
        
        return@withContext try {
            val firstAddress = getFirstAddress(initialGeocoder, latitude, longitude)
            
            if (firstAddress != null) {
                val countryCode = firstAddress.countryCode
                Log.d("LocationManager", "Country code detected: $countryCode")
                
                // 2. 国コードが取得できれば、その国のLocaleで再取得を試みる
                // 非推奨のコンストラクタを避け、Locale.Builder を使用
                val targetLocale = if (countryCode != null) {
                    Locale.Builder().setRegion(countryCode).build()
                } else {
                    Locale.getDefault()
                }
                
                val targetGeocoder = Geocoder(context, targetLocale)
                val finalAddress = getFirstAddress(targetGeocoder, latitude, longitude)
                
                val result = finalAddress?.getAddressLine(0) ?: firstAddress.getAddressLine(0)
                Log.d("LocationManager", "Final address: $result")
                result
            } else {
                Log.w("LocationManager", "No address found for these coordinates")
                "住所が見つかりませんでした"
            }
        } catch (e: Exception) {
            Log.e("LocationManager", "Geocoder error", e)
            "住所の取得に失敗しました"
        }
    }

    private suspend fun getFirstAddress(geocoder: Geocoder, lat: Double, lng: Double): Address? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: List<Address>) {
                            continuation.resume(addresses.getOrNull(0))
                        }
                        override fun onError(errorMessage: String?) {
                            Log.e("LocationManager", "GeocodeListener error: $errorMessage")
                            continuation.resume(null)
                        }
                    })
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(lat, lng, 1)?.getOrNull(0)
            }
        } catch (e: Exception) {
            Log.e("LocationManager", "getFirstAddress exception", e)
            null
        }
    }
}
