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
import com.google.android.gms.tasks.CancellationTokenSource
import com.studiokei.walkaround.data.database.AppDatabase
import com.studiokei.walkaround.data.model.AddressRecord
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
    private val database: AppDatabase = AppDatabase.getDatabase(context)

    companion object {
        // 住所表示用のロケールを保持する変数
        private var cachedLocale: Locale? = null
    }

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

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? = suspendCancellableCoroutine { continuation ->
        val cts = CancellationTokenSource()
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
            cts.cancel()
            fusedLocationClient.removeLocationUpdates(callback)
        }
    }

    /**
     * 保持されているロケール（なければデフォルト）を使用して住所オブジェクトを取得する
     */
    suspend fun getLocaleAddress(latitude: Double, longitude: Double): Address? = withContext(Dispatchers.IO) {
        Log.d("LocationManager", "getLocaleAddress called for: $latitude, $longitude using locale: ${cachedLocale ?: "Default"}")
        val locale = cachedLocale ?: Locale.getDefault()
        val geocoder = Geocoder(context, locale)
        
        return@withContext try {
            val address = getAddressInternal(geocoder, latitude, longitude)
            Log.d("LocationManager", "Result getLocaleAddress : $address")
            address
        } catch (e: Exception) {
            Log.e("LocationManager", "Geocoder error", e)
            null
        }
    }

    /**
     * 現在地から国コードを特定し、住所表示用のロケールを更新・保存する
     */
    suspend fun updateCachedLocale(latitude: Double, longitude: Double) {
        Log.d("LocationManager", "updateCachedLocale called for: $latitude, $longitude")
        val initialGeocoder = Geocoder(context, Locale.getDefault())
        
        try {
            val firstAddress = getAddressInternal(initialGeocoder, latitude, longitude)
            firstAddress?.countryCode?.let { countryCode ->
                cachedLocale = Locale.Builder().setRegion(countryCode).build()
                Log.d("LocationManager", "Locale updated and cached: $cachedLocale")
            }
        } catch (e: Exception) {
            Log.e("LocationManager", "Failed to update cached locale", e)
        }
    }

    private suspend fun getAddressInternal(geocoder: Geocoder, lat: Double, lng: Double): Address? {
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
            Log.e("LocationManager", "getAddressInternal exception", e)
            null
        }
    }

    suspend fun saveAddressRecord(
        lat: Double?,
        lng: Double?,
        sectionId: Long? = null,
        trackId: Long? = null
    ) {
        withContext(Dispatchers.IO) {
            val time = System.currentTimeMillis()
            var addressLine: String? = null
            var name: String? = null

            if (lat != null && lng != null) {
                val address = getLocaleAddress(lat, lng)
                addressLine = address?.getAddressLine(0)
                
                // featureName が数字と記号だけの場合は name を null にする
                val fName = address?.featureName
                name = if (fName != null && fName.any { it.isLetter() }) {
                    fName
                } else {
                    null
                }
            }

            val record = AddressRecord(
                time = time,
                sectionId = sectionId,
                trackId = trackId,
                lat = lat,
                lng = lng,
                name = name,
                addressLine = addressLine
            )
            database.addressDao().insert(record)
            Log.d("LocationManager", "AddressRecord saved: $record")
        }
    }
}
