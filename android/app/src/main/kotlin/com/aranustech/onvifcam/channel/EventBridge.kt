package com.aranustech.onvifcam.channel

import android.os.Handler
import android.os.Looper
import android.util.Log
import io.flutter.plugin.common.EventChannel

/** Single process-wide sink for native -> Flutter push events (status/log/error). */
object EventBridge {
    private var sink: EventChannel.EventSink? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun attach(sink: EventChannel.EventSink) {
        this.sink = sink
    }

    fun detach() {
        this.sink = null
    }

    fun emit(payload: Map<String, Any?>) {
        val currentSink = sink ?: return
        mainHandler.post { currentSink.success(payload) }
    }

    fun log(level: String, message: String) {
        Log.d("OnvifCam", "[$level] $message")
        emit(
            mapOf(
                "type" to "log",
                "level" to level,
                "message" to message,
                "ts" to System.currentTimeMillis(),
            )
        )
    }
}
