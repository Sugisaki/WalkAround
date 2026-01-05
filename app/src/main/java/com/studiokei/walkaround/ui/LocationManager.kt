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
    private val prefs = context.getSharedPreferences("location_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val PREF_KEY_COUNTRY_CODE = "cached_country_code"
        // メモリ上のキャッシュ（パフォーマンスのため）
        private var memoryCachedLocale: Locale? = null
    }

    private fun getCachedLocale(): Locale? {
        memoryCachedLocale?.let { return it }
        
        val countryCode = prefs.getString(PREF_KEY_COUNTRY_CODE, null)
        return if (countryCode != null) {
            Locale.Builder().setRegion(countryCode).build().also {
                memoryCachedLocale = it
            }
        } else {
            null
        }
    }

    private fun setCachedLocale(countryCode: String) {
        prefs.edit().putString(PREF_KEY_COUNTRY_CODE, countryCode).apply()
        memoryCachedLocale = Locale.Builder().setRegion(countryCode).build()
        Log.d("LocationManager", "Locale updated and persisted: $memoryCachedLocale")
    }

    @SuppressLint("MissingPermission") // Permissions are handled in HomeScreen
    fun requestLocationUpdates(): Flow<Location> = callbackFlow {
        Log.d("LocationManager", "requestLocationUpdates called")
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000L // 1 秒ごとに位置情報が欲しい
        )
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(1000L) // 更新は最短でも1秒あけて
            .setMinUpdateDistanceMeters(2f)    // 2メートル以上動かないと更新しないで
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
        val locale = getCachedLocale() ?: Locale.getDefault()
        Log.d("LocationManager", "getLocaleAddress called for: $latitude, $longitude using locale: $locale")
        val geocoder = Geocoder(context, locale)
        
        return@withContext try {
            val address = getAddressInternal(geocoder, latitude, longitude)
            if (address != null) {
                Log.d("LocationManager", """
                    Result getLocaleAddress:
                      Country: ${address.countryName} (${address.countryCode})
                      AdminArea: ${address.adminArea}
                      Locality: ${address.locality}
                      SubLocality: ${address.subLocality}
                      Thoroughfare: ${address.thoroughfare}
                      SubThoroughfare: ${address.subThoroughfare}
                      FeatureName: ${address.featureName}
                      PostalCode: ${address.postalCode}
                      AddressLine(0): ${address.getAddressLine(0)}
                """.trimIndent())
            } else {
                Log.d("LocationManager", "Result getLocaleAddress : null")
            }
            address
        } catch (e: Exception) {
            Log.e("LocationManager", "Geocoder error", e)
            null
        }
    }

    /**
     * 住所オブジェクトから比較用のキー（丁目レベル）を生成します。
     * 比較用の文字列は、市区町村 + 町村 + 丁目/番地
     */
    fun getAddressKey(address: Address?): String {
        val locality = address?.locality ?: ""
        val subLocality = address?.subLocality ?: ""
        val thoroughfare = address?.thoroughfare ?: address?.subThoroughfare ?: ""
        return "$locality$subLocality$thoroughfare"
    }

    /**
     * 住所を取得し、前回保存されたキーと異なる場合にのみ保存します。
     * @return 新しい住所キー。変化がなかった場合や取得失敗時は null。
     */
    suspend fun saveAddressIfThoroughfareChanged(
        lat: Double,
        lng: Double,
        sectionId: Long?,
        trackId: Long?,
        timestamp: Long,
        lastAddressKey: String?
    ): String? {
        val address = getLocaleAddress(lat, lng) ?: return null // 取得失敗時は判定をスキップ
        val currentKey = getAddressKey(address)
        
        Log.d("LocationManager", "Comparing address keys: current='$currentKey', last='$lastAddressKey'")
        
        if (currentKey != lastAddressKey) {
            Log.d("LocationManager", "Address key changed. Saving address.")
            saveAddressRecord(
                lat = lat,
                lng = lng,
                sectionId = sectionId,
                trackId = trackId,
                timestamp = timestamp
            )
            return currentKey
        }
        return null
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
                setCachedLocale(countryCode)
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
        trackId: Long? = null,
        timestamp: Long? = null
    ) {
        withContext(Dispatchers.IO) {
            val time = timestamp ?: System.currentTimeMillis()
            if (lat != null && lng != null) {
                val address = getLocaleAddress(lat, lng)
                val record = AddressRecord(
                    time = time,
                    sectionId = sectionId,
                    trackId = trackId,
                    lat = lat,
                    lng = lng,
                    address = address
                )
                database.addressDao().insert(record)
                Log.d("LocationManager", "AddressRecord saved: $record")
            } else {
                val record = AddressRecord(
                    time = time,
                    sectionId = sectionId,
                    trackId = trackId,
                    lat = lat,
                    lng = lng,
                    address = null
                )
                database.addressDao().insert(record)
                Log.d("LocationManager", "AddressRecord saved (null location): $record")
            }
        }
    }
}
