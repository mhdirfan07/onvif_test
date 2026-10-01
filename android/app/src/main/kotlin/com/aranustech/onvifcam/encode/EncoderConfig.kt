package com.aranustech.onvifcam.encode

data class EncoderConfig(
    val width: Int = 1280,
    val height: Int = 720,
    val bitrate: Int = 2_000_000,
    val fps: Int = 30,
)
