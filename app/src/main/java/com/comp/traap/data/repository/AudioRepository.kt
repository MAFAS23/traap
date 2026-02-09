package com.comp.traap.data.repository

import android.content.Context
import android.provider.Settings
import com.comp.traap.data.model.MicControlData
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AudioRepository(private val context: Context) {

    private val firebaseDatabase = FirebaseDatabase.getInstance()
    private val deviceId =
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                    ?: "unknown"

    /** Listen to microphone control changes from Firebase Path: mic_control/{deviceId} */
    fun getMicControlUpdates(): Flow<MicControlData> = callbackFlow {
        val ref = firebaseDatabase.getReference("mic_control").child(deviceId)

        val listener =
                object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.exists()) {
                            val data = snapshot.value as? Map<String, Any>
                            if (data != null) {
                                val micControl = MicControlData.fromMap(data, deviceId)
                                trySend(micControl)
                            }
                        } else {
                            // Initialize with default data if not exists
                            val defaultData = MicControlData(deviceId = deviceId)
                            ref.setValue(defaultData.toMap())
                            trySend(defaultData)
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        android.util.Log.e(TAG, "Firebase listener cancelled: ${error.message}")
                        close(error.toException())
                    }
                }

        ref.addValueEventListener(listener)

        awaitClose { ref.removeEventListener(listener) }
    }

    /** Update microphone status in Firebase */
    suspend fun updateMicStatus(status: String): Result<Unit> {
        return try {
            val ref = firebaseDatabase.getReference("mic_control").child(deviceId)
            val updates =
                    hashMapOf<String, Any>(
                            "status" to status,
                            "lastUpdate" to System.currentTimeMillis()
                    )
            ref.updateChildren(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to update mic status: ${e.message}")
            Result.failure(e)
        }
    }

    /** Initialize mic control data in Firebase */
    suspend fun initializeMicControl(): Result<Unit> {
        return try {
            val ref = firebaseDatabase.getReference("mic_control").child(deviceId)
            val defaultData = MicControlData(deviceId = deviceId)
            ref.setValue(defaultData.toMap()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to initialize mic control: ${e.message}")
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "AudioRepository"
    }
}
