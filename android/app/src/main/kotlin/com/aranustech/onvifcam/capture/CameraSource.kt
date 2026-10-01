package com.aranustech.onvifcam.capture

import android.view.Surface

/** Common contract for any video frame source feeding the encoder's input surface. */
interface CameraSource {
    var onError: ((String) -> Unit)?
    fun open(targetSurface: Surface, width: Int, height: Int, fps: Int, onOpened: () -> Unit)
    fun close()
}
