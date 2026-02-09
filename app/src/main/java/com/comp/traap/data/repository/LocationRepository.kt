package com.comp.traap.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.comp.traap.data.model.LocationData
import com.google.android.gms.location.*
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class LocationRepository(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(context)

    private val firebaseDatabase = FirebaseDatabase.getInstance()

    /** Get location updates as Flow Interval: 5 seconds */
    @SuppressLint("MissingPermission")
    fun getLocationUpdates(): Flow<LocationData> = callbackFlow {
        // Get device ID
        val deviceId =
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                        ?: "unknown"

        val locationRequest =
                LocationRequest.Builder(
                                Priority.PRIORITY_HIGH_ACCURACY,
                                5000L // 5 seconds
                        )
                        .apply {
                            setMinUpdateIntervalMillis(5000L)
                            setWaitForAccurateLocation(false)
                        }
                        .build()

        val locationCallback =
                object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        result.lastLocation?.let { location ->
                            val locationData =
                                    LocationData(
                                            latitude = location.latitude,
                                            longitude = location.longitude,
                                            timestamp = System.currentTimeMillis(),
                                            accuracy = location.accuracy,
                                            deviceId = deviceId
                                    )
                            trySend(locationData)
                        }
                    }
                }

        fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                context.mainLooper
        )

        awaitClose { fusedLocationClient.removeLocationUpdates(locationCallback) }
    }

    /** Send location data to Firebase */
    suspend fun sendLocationToFirebase(locationData: LocationData): Result<Unit> {
        return try {
            // Using fixed path "current" so new data replaces old data
            val ref = firebaseDatabase.getReference("locations").child("current")

            ref.setValue(locationData.toMap()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Stop location updates */
    fun stopLocationUpdates() {
        // Location updates will be stopped when flow is cancelled
    }
}
