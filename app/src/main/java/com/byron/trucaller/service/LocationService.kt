package com.byron.trucaller.service

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

data class LocationInfo(
    val latitude: Double,
    val longitude: Double,
    val city: String,
    val country: String,
    val accuracy: Float
) {
    companion object {
        fun unknown() = LocationInfo(0.0, 0.0, "Unknown", "Unknown", 0f)
    }
}

class LocationService(private val context: Context) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    /** Tracks any active location callback so it can be cleaned up. */
    private var activeCallback: LocationCallback? = null

    /** Simple cache to prevent redundant GPS hits within a short window (2 mins) */
    private var cachedLocation: Pair<LocationInfo, Long>? = null
    private val CACHE_EXPIRY_MS = 120_000L // 2 minutes

    companion object {
        const val REQUEST_CHECK_SETTINGS = 9001

        fun hasBackgroundLocationPermission(context: Context): Boolean {
            return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasBackgroundLocationPermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Before Android Q, foreground permission covers background
            hasLocationPermission()
        }
    }

    fun isLocationEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /**
     * Prompts the user to enable location via a system dialog (no need to leave the app).
     * Call from an Activity context. Returns true if location is already enabled.
     */
    fun requestLocationSettings(activity: Activity) {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 10_000).build()
        val settingsRequest = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .setAlwaysShow(true)
            .build()

        val settingsClient: SettingsClient = LocationServices.getSettingsClient(activity)
        settingsClient.checkLocationSettings(settingsRequest)
            .addOnFailureListener { exception ->
                if (exception is ResolvableApiException) {
                    try {
                        exception.startResolutionForResult(activity, REQUEST_CHECK_SETTINGS)
                    } catch (_: IntentSender.SendIntentException) { }
                }
            }
    }

    /**
     * Gets the most accurate location possible — used for stolen device tracking.
     * Uses HIGH_ACCURACY priority to force GPS fix.
     * 1. First tries getCurrentLocation() API (forces GPS fix, most accurate)
     * 2. Falls back to location updates with high accuracy
     * 3. Last resort: lastLocation cache
     * Waits up to 15 seconds for an accurate GPS fix.
     */
    suspend fun getCurrentLocation(): LocationInfo {
        if (!hasLocationPermission()) return LocationInfo.unknown()

        // Check cache first to save battery
        cachedLocation?.let { (info, timestamp) ->
            if (System.currentTimeMillis() - timestamp < CACHE_EXPIRY_MS) {
                return info
            }
        }

        return try {
            // Method 1: Force a fresh GPS fix (most accurate)
            val fresh = withTimeoutOrNull(10_000L) { requestCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY) }
            if (fresh != null && fresh.accuracy <= 50f) return fresh

            // Method 2: Request location updates and wait for accurate result
            val updated = withTimeoutOrNull(10_000L) { requestLocationUpdates(Priority.PRIORITY_HIGH_ACCURACY) }
            if (updated != null && updated.accuracy <= 100f) return updated

            // Method 3: Use last known location as fallback
            val last = getLastKnownLocation()
            if (last != null) return last

            // Return the best we got, even if not perfect
            fresh ?: updated ?: LocationInfo.unknown()
        } catch (e: Exception) {
            Log.w("LocationService", "Failed to get location", e)
            LocationInfo.unknown()
        }
    }

    /**
     * Gets a battery-friendly location — used for background/periodic location updates.
     * Uses BALANCED_POWER_ACCURACY (Wi-Fi + cell tower, ~100m accuracy).
     * Falls back to last known location if a fresh fix is unavailable.
     */
    suspend fun getBackgroundLocation(): LocationInfo {
        if (!hasLocationPermission()) return LocationInfo.unknown()

        return try {
            val fresh = withTimeoutOrNull(8_000L) { requestCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY) }
            if (fresh != null) return fresh

            val last = getLastKnownLocation()
            last ?: LocationInfo.unknown()
        } catch (e: Exception) {
            Log.w("LocationService", "Failed to get background location", e)
            LocationInfo.unknown()
        }
    }

    /**
     * Releases any active location callback to prevent battery drain.
     * Call this from service onDestroy or when location tracking is no longer needed.
     */
    fun stopLocationUpdates() {
        activeCallback?.let { callback ->
            fusedClient.removeLocationUpdates(callback)
            activeCallback = null
            Log.d("LocationService", "Location updates stopped and callback released")
        }
    }

    /**
     * Uses the getCurrentLocation API which forces the device to get a fresh fix.
     * @param priority one of [Priority.PRIORITY_HIGH_ACCURACY] or [Priority.PRIORITY_BALANCED_POWER_ACCURACY]
     */
    @SuppressWarnings("MissingPermission")
    private suspend fun requestCurrentLocation(priority: Int): LocationInfo? {
        if (!hasLocationPermission()) return null

        return suspendCancellableCoroutine { cont ->
            val cancellationToken = CancellationTokenSource()

            fusedClient.getCurrentLocation(
                priority,
                cancellationToken.token
            )
                .addOnSuccessListener { location ->
                    if (location != null) {
                        val geo = reverseGeocode(location.latitude, location.longitude)
                        val info = LocationInfo(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            city = geo.first,
                            country = geo.second,
                            accuracy = location.accuracy
                        )
                        cachedLocation = info to System.currentTimeMillis()
                        cont.resume(info)
                    } else {
                        cont.resume(null)
                    }
                }
                .addOnFailureListener {
                    cont.resume(null)
                }

            cont.invokeOnCancellation {
                cancellationToken.cancel()
            }
        }
    }

    /**
     * Requests location updates and picks the most accurate result.
     * @param priority one of [Priority.PRIORITY_HIGH_ACCURACY] or [Priority.PRIORITY_BALANCED_POWER_ACCURACY]
     */
    @SuppressWarnings("MissingPermission")
    private suspend fun requestLocationUpdates(priority: Int): LocationInfo? {
        if (!hasLocationPermission()) return null

        val maxUpdates = 5
        return suspendCancellableCoroutine { cont ->
            var bestResult: LocationInfo? = null
            var resumed = false
            var updateCount = 0

            val request = LocationRequest.Builder(priority, 1000)
                .setWaitForAccurateLocation(true)
                .setMinUpdateIntervalMillis(500)
                .setMaxUpdates(maxUpdates)
                .setMinUpdateDistanceMeters(0f)
                .build()

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val location = result.lastLocation ?: return
                    val geo = reverseGeocode(location.latitude, location.longitude)
                    val info = LocationInfo(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        city = geo.first,
                        country = geo.second,
                        accuracy = location.accuracy
                    )

                    // Keep the most accurate result
                    if (bestResult == null || location.accuracy < (bestResult?.accuracy ?: Float.MAX_VALUE)) {
                        bestResult = info
                    }

                    updateCount++

                    // If we got a very accurate fix (< 20m), return immediately
                    if (location.accuracy <= 20f && !resumed) {
                        resumed = true
                        fusedClient.removeLocationUpdates(this)
                        activeCallback = null
                        cachedLocation = info to System.currentTimeMillis()
                        cont.resume(info)
                    } else if (updateCount >= maxUpdates && !resumed) {
                        // All updates received without hitting accuracy threshold;
                        // resume with the best result we collected.
                        resumed = true
                        activeCallback = null
                        cont.resume(bestResult)
                    }
                }
            }

            activeCallback = callback
            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())

            cont.invokeOnCancellation {
                fusedClient.removeLocationUpdates(callback)
                activeCallback = null
                // Note: Cannot resume here as the continuation is already cancelled.
                // The bestResult is returned via the maxUpdates check above or
                // via the accuracy threshold check, both of which fire before timeout.
            }
        }
    }

    @SuppressWarnings("MissingPermission")
    private suspend fun getLastKnownLocation(): LocationInfo? {
        if (!hasLocationPermission()) return null

        return suspendCancellableCoroutine { cont ->
            fusedClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        val geo = reverseGeocode(location.latitude, location.longitude)
                        val info = LocationInfo(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            city = geo.first,
                            country = geo.second,
                            accuracy = location.accuracy
                        )
                        cachedLocation = info to System.currentTimeMillis()
                        cont.resume(info)
                    } else {
                        cont.resume(null)
                    }
                }
                .addOnFailureListener {
                    cont.resume(null)
                }
        }
    }

    /**
     * Reverse geocodes lat/lng to city, district, and country.
     */
    private fun reverseGeocode(latitude: Double, longitude: Double): Pair<String, String> {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            
            // On API 33+, we should ideally use the callback-based API.
            // However, since this is called from suspend functions, we keep 
            // the blocking call for structural simplicity while acknowledging the deprecation.
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                // Build a detailed location string
                val parts = listOfNotNull(
                    address.subLocality,      // neighborhood
                    address.locality,          // city
                    address.subAdminArea,      // district
                    address.adminArea          // state/region
                ).distinct()
                val city = if (parts.isNotEmpty()) parts.joinToString(", ") else "Unknown"
                val country = address.countryName ?: "Unknown"
                Pair(city, country)
            } else {
                Pair("Unknown", "Unknown")
            }
        } catch (e: Exception) {
            Pair("Unknown", "Unknown")
        }
    }
}
