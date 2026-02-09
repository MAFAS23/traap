package com.comp.traap.service

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.comp.traap.R
import com.comp.traap.data.repository.AudioRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch

class AudioService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var audioRepository: AudioRepository

    private var listenerJob: Job? = null
    private var recordingJob: Job? = null
    private var audioRecord: AudioRecord? = null

    private var isRecording = false

    // Audio configuration
    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    override fun onCreate() {
        super.onCreate()
        audioRepository = AudioRepository(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startAudioService()
            ACTION_STOP -> stopAudioService()
        }
        return START_STICKY // Auto-restart if killed
    }

    private fun startAudioService() {
        // Check microphone permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
                        PackageManager.PERMISSION_GRANTED
        ) {
            android.util.Log.e(TAG, "RECORD_AUDIO permission not granted")
            stopSelf()
            return
        }

        // Start foreground service with notification
        val notification = createNotification("Monitoring mic control...")
        startForeground(NOTIFICATION_ID, notification)

        // Initialize mic control in Firebase
        serviceScope.launch { audioRepository.initializeMicControl() }

        // Start listening to Firebase mic control changes
        listenerJob =
                serviceScope.launch {
                    audioRepository
                            .getMicControlUpdates()
                            .catch { e ->
                                android.util.Log.e(
                                        TAG,
                                        "Error listening to mic control: ${e.message}"
                                )
                            }
                            .collect { micControl ->
                                android.util.Log.d(
                                        TAG,
                                        "Mic control update: enabled=${micControl.enabled}, status=${micControl.status}"
                                )

                                if (micControl.enabled && !isRecording) {
                                    startRecording()
                                } else if (!micControl.enabled && isRecording) {
                                    stopRecording()
                                }
                            }
                }
    }

    private fun startRecording() {
        if (isRecording) return

        android.util.Log.d(TAG, "Starting audio recording...")

        try {
            // Initialize AudioRecord
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
                            PackageManager.PERMISSION_GRANTED
            ) {
                android.util.Log.e(TAG, "RECORD_AUDIO permission not granted")
                return
            }

            audioRecord =
                    AudioRecord(
                            MediaRecorder.AudioSource.MIC,
                            sampleRate,
                            channelConfig,
                            audioFormat,
                            bufferSize
                    )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                android.util.Log.e(TAG, "AudioRecord initialization failed")
                updateNotification("Error: AudioRecord init failed")
                updateFirebaseStatus("error")
                return
            }

            audioRecord?.startRecording()
            isRecording = true

            // Update Firebase status
            updateFirebaseStatus("recording")
            updateNotification("Recording audio...")

            // Start recording in background
            recordingJob =
                    serviceScope.launch {
                        val audioData = ByteArray(bufferSize)

                        while (isRecording) {
                            val readSize = audioRecord?.read(audioData, 0, bufferSize) ?: 0

                            if (readSize > 0) {
                                // TODO: Process or stream audio data here
                                // For now, we just read and discard (no streaming yet)
                                android.util.Log.v(TAG, "Read $readSize bytes of audio data")
                            }
                        }
                    }

            android.util.Log.d(TAG, "Audio recording started successfully")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error starting recording: ${e.message}")
            isRecording = false
            updateFirebaseStatus("error")
            updateNotification("Error: ${e.message}")
        }
    }

    private fun stopRecording() {
        if (!isRecording) return

        android.util.Log.d(TAG, "Stopping audio recording...")

        isRecording = false
        recordingJob?.cancel()

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

            updateFirebaseStatus("stopped")
            updateNotification("Monitoring mic control...")

            android.util.Log.d(TAG, "Audio recording stopped successfully")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error stopping recording: ${e.message}")
        }
    }

    private fun stopAudioService() {
        stopRecording()
        listenerJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateFirebaseStatus(status: String) {
        serviceScope.launch { audioRepository.updateMicStatus(status) }
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Microphone Service")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                    NotificationChannel(
                                    CHANNEL_ID,
                                    "Microphone Control",
                                    NotificationManager.IMPORTANCE_LOW
                            )
                            .apply {
                                description = "Microphone recording service"
                                setShowBadge(false)
                            }

            val notificationManager =
                    getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
        listenerJob?.cancel()
        recordingJob?.cancel()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "AudioService"
        const val CHANNEL_ID = "audio_service_channel"
        const val NOTIFICATION_ID = 1002
        const val ACTION_START = "ACTION_START_AUDIO_SERVICE"
        const val ACTION_STOP = "ACTION_STOP_AUDIO_SERVICE"

        fun startService(context: Context) {
            val intent = Intent(context, AudioService::class.java).apply { action = ACTION_START }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, AudioService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }
    }
}
