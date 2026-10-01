package com.aranustech.onvifcam.model

data class ServerConfig(
    val username: String,
    val password: String,
    val rtspPort: Int = 8554,
    val httpPort: Int = 8080,
    val deviceName: String = "OnvifCam",
    val location: String = "",
    val cameraId: String? = null,
) {
    companion object {
        fun fromMap(map: Map<*, *>): ServerConfig = ServerConfig(
            username = map["username"] as? String ?: "admin",
            password = map["password"] as? String ?: "admin",
            rtspPort = (map["rtspPort"] as? Number)?.toInt() ?: 8554,
            httpPort = (map["httpPort"] as? Number)?.toInt() ?: 8080,
            deviceName = map["deviceName"] as? String ?: "OnvifCam",
            location = map["location"] as? String ?: "",
            cameraId = map["cameraId"] as? String,
        )
    }
}

data class CameraStatus(
    val running: Boolean,
    val ip: String?,
    val rtspPort: Int,
    val httpPort: Int,
    val rtspClients: Int = 0,
    val cameraOpen: Boolean = false,
    val lastError: String? = null,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "type" to "status",
        "running" to running,
        "ip" to ip,
        "rtspUrl" to if (ip != null) "rtsp://$ip:$rtspPort/onvif1" else null,
        "httpPort" to httpPort,
        "rtspClients" to rtspClients,
        "cameraOpen" to cameraOpen,
        "lastError" to lastError,
    )
}
