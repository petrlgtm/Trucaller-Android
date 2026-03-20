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
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build()
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
     * Gets the most accurate location possible.
     * 1. First tries getCurrentLocation() API (forces GPS fix, most accurate)
     * 2. Falls back to location updates with high accuracy
     * 3. Last resort: lastLocation cache
     * Waits up to 15 seconds for an accurate GPS fix.
     */
    suspend fun getCurrentLocation(): LocationInfo {
        if (!hasLocationPermission()) return LocationInfo.unknown()

        return try {
            // Method 1: Force a fresh GPS fix (most accurate)
            val fresh = withTimeoutOrNull(10_000L) { requestCurrentLocation() }
            if (fresh != null && fresh.accuracy <= 50f) return fresh

            // Method 2: Request location updates and wait for accurate result
            val updated = withTimeoutOrNull(10_000L) { requestLocationUpdates() }
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
     * Uses the getCurrentLocation API which forces the device to get a fresh GPS fix.
     * This is the most accurate single-shot method available.
     */
    @SuppressWarnings("MissingPermission")
    private suspend fun requestCurrentLocation(): LocationInfo? {
        if (!hasLocationPermission()) return null

        return suspendCancellableCoroutine { cont ->
            val cancellationToken = CancellationTokenSource()

            fusedClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationToken.token
            )
                .addOnSuccessListener { location ->
                    if (location != null) {
                        val geo = reverseGeocode(location.latitude, location.longitude)
                        cont.resume(
                            LocationInfo(
                                latitude = location.latitude,
                                longitude = location.longitude,
                                city = geo.first,
                                country = geo.second,
                                accuracy = location.accuracy
                            )
                        )
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
     * Requests location updates with high accuracy and picks the most accurate result.
     */
    @SuppressWarnings("MissingPermission")
    private suspend fun requestLocationUpdates(): LocationInfo? {
        if (!hasLocationPermission()) return null

        val maxUpdates = 5
        return suspendCancellableCoroutine { cont ->
            var bestResult: LocationInfo? = null
            var resumed = false
            var updateCount = 0

            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
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
                        cont.resume(info)
                    } else if (updateCount >= maxUpdates && !resumed) {
                        // All updates received without hitting accuracy threshold;
                        // resume with the best result we collected.
                        resumed = true
                        cont.resume(bestResult)
                    }
                }
            }

            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())

            cont.invokeOnCancellation {
                fusedClient.removeLocationUpdates(callback)
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
                        cont.resume(
                            LocationInfo(
                                latitude = location.latitude,
                                longitude = location.longitude,
                                city = geo.first,
                                country = geo.second,
                                accuracy = location.accuracy
                            )
                        )
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
