package com.comp.traap.data.model

data class LocationData(
        val latitude: Double = 0.0,
        val longitude: Double = 0.0,
        val timestamp: Long = System.currentTimeMillis(),
        val accuracy: Float? = null,
        val deviceId: String = ""
) {
    fun toMap(): Map<String, Any> {
        return hashMapOf(
                "latitude" to latitude,
                "longitude" to longitude,
                "timestamp" to timestamp,
                "accuracy" to (accuracy ?: 0f),
                "deviceId" to deviceId
        )
    }
}
