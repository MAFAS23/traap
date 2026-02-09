package com.comp.traap.data.model

data class MicControlData(
        val enabled: Boolean = false,
        val status: String = "stopped", // "recording", "stopped", "error"
        val lastUpdate: Long = System.currentTimeMillis(),
        val deviceId: String = ""
) {
    fun toMap(): Map<String, Any> {
        return hashMapOf(
                "enabled" to enabled,
                "status" to status,
                "lastUpdate" to lastUpdate,
                "deviceId" to deviceId
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>, deviceId: String): MicControlData {
            return MicControlData(
                    enabled = map["enabled"] as? Boolean ?: false,
                    status = map["status"] as? String ?: "stopped",
                    lastUpdate = map["lastUpdate"] as? Long ?: System.currentTimeMillis(),
                    deviceId = deviceId
            )
        }
    }
}
