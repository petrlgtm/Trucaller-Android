package com.byron.trucaller.service

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Wraps [GeofencingClient] from Google Play Services to manage geofence
 * registration and removal.
 *
 * Geofences are monitored for ENTER, EXIT, and DWELL transitions (dwell
 * loitering delay: 30 seconds). Transition events are delivered to
 * [GeofenceBroadcastReceiver] via a [PendingIntent].
 */
class GeofencingService(private val context: Context) {

    companion object {
        private const val TAG = "GeofencingService"
        private const val DWELL_LOITERING_DELAY_MS = 30_000 // 30 seconds
        private const val GEOFENCE_EXPIRATION = Geofence.NEVER_EXPIRE
        private const val PENDING_INTENT_REQUEST_CODE = 7001
    }

    private val geofencingClient: GeofencingClient =
        LocationServices.getGeofencingClient(context)

    /**
     * Lazily-initialized PendingIntent that routes geofence transition
     * events to [GeofenceBroadcastReceiver].
     */
    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        PendingIntent.getBroadcast(
            context,
            PENDING_INTENT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    // ── Permission check ─────────────────────────────────────────────────

    private fun hasFineLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private fun hasBackgroundLocationPermission(): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            hasFineLocationPermission()
        }

    // ── Add geofence ─────────────────────────────────────────────────────

    /**
     * Registers a geofence with the given parameters.
     *
     * @param geofenceId Unique identifier for the geofence.
     * @param lat        Latitude of the geofence center.
     * @param lng        Longitude of the geofence center.
     * @param radiusMeters Radius in meters.
     * @return `true` if the geofence was added successfully.
     */
    @SuppressWarnings("MissingPermission")
    suspend fun addGeofence(
        geofenceId: String,
        lat: Double,
        lng: Double,
        radiusMeters: Float
    ): Boolean {
        if (!hasFineLocationPermission() || !hasBackgroundLocationPermission()) {
            Log.w(TAG, "Missing location permissions — cannot add geofence")
            return false
        }

        val geofence = Geofence.Builder()
            .setRequestId(geofenceId)
            .setCircularRegion(lat, lng, radiusMeters)
            .setExpirationDuration(GEOFENCE_EXPIRATION)
            .setTransitionTypes(
                Geofence.GEOFENCE_TRANSITION_ENTER
                        or Geofence.GEOFENCE_TRANSITION_EXIT
                        or Geofence.GEOFENCE_TRANSITION_DWELL
            )
            .setLoiteringDelay(DWELL_LOITERING_DELAY_MS)
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(
                GeofencingRequest.INITIAL_TRIGGER_ENTER
                        or GeofencingRequest.INITIAL_TRIGGER_DWELL
            )
            .addGeofence(geofence)
            .build()

        return suspendCancellableCoroutine { cont ->
            geofencingClient.addGeofences(request, geofencePendingIntent)
                .addOnSuccessListener {
                    Log.d(TAG, "Geofence added: $geofenceId")
                    cont.resume(true)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to add geofence: $geofenceId", e)
                    cont.resume(false)
                }
        }
    }

    // ── Remove geofence ──────────────────────────────────────────────────

    /**
     * Removes a single geofence by its request ID.
     *
     * @return `true` if the geofence was removed successfully.
     */
    suspend fun removeGeofence(geofenceId: String): Boolean {
        return suspendCancellableCoroutine { cont ->
            geofencingClient.removeGeofences(listOf(geofenceId))
                .addOnSuccessListener {
                    Log.d(TAG, "Geofence removed: $geofenceId")
                    cont.resume(true)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to remove geofence: $geofenceId", e)
                    cont.resume(false)
                }
        }
    }

    /**
     * Removes all geofences associated with this app's PendingIntent.
     *
     * @return `true` if all geofences were removed successfully.
     */
    suspend fun removeAllGeofences(): Boolean {
        return suspendCancellableCoroutine { cont ->
            geofencingClient.removeGeofences(geofencePendingIntent)
                .addOnSuccessListener {
                    Log.d(TAG, "All geofences removed")
                    cont.resume(true)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to remove all geofences", e)
                    cont.resume(false)
                }
        }
    }

    // ── Re-register geofences (e.g. after boot) ─────────────────────────

    /**
     * Re-registers a list of geofences. Useful after device reboot, since
     * the system clears registered geofences on restart.
     *
     * @param geofences List of geofence definitions to re-register.
     * @return Number of geofences successfully re-registered.
     */
    @SuppressWarnings("MissingPermission")
    suspend fun reRegisterGeofences(
        geofences: List<com.byron.trucaller.data.model.Geofence>
    ): Int {
        if (!hasFineLocationPermission() || !hasBackgroundLocationPermission()) {
            Log.w(TAG, "Missing location permissions — cannot re-register geofences")
            return 0
        }

        var successCount = 0
        for (fence in geofences) {
            if (!fence.isActive) continue
            val result = addGeofence(
                geofenceId = fence.id,
                lat = fence.latitude,
                lng = fence.longitude,
                radiusMeters = fence.radiusMeters.toFloat()
            )
            if (result) successCount++
        }
        return successCount
    }
}
