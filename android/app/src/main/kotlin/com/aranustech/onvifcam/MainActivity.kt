package com.aranustech.onvifcam

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.aranustech.onvifcam.capture.CameraSourceRegistry
import com.aranustech.onvifcam.channel.ControlMethodChannel
import com.aranustech.onvifcam.channel.EventStreamHandler
import com.aranustech.onvifcam.model.CameraStatus
import com.aranustech.onvifcam.model.ServerConfig
import com.aranustech.onvifcam.service.CameraServerService
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel

private const val CONTROL_CHANNEL = "com.aranustech.onvifcam/control"
private const val EVENT_CHANNEL = "com.aranustech.onvifcam/events"

class MainActivity : FlutterActivity() {
    private var boundService: CameraServerService? = null
    private var pendingAction: ((CameraServerService) -> Unit)? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as CameraServerService.LocalBinder).getService()
            boundService = service
            pendingAction?.invoke(service)
            pendingAction = null
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            boundService = null
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            CONTROL_CHANNEL,
        ).setMethodCallHandler(ControlMethodChannel(this))
        EventChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            EVENT_CHANNEL,
        ).setStreamHandler(EventStreamHandler())
    }

    fun startCameraServer(config: ServerConfig, onResult: (CameraStatus) -> Unit) {
        val intent = Intent(this, CameraServerService::class.java)
        startForegroundService(intent)
        withBoundService { service ->
            service.start(config, onResult)
        }
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun stopCameraServer(onResult: (CameraStatus) -> Unit) {
        val service = boundService
        if (service == null) {
            onResult(CameraStatus(running = false, ip = null, rtspPort = 0, httpPort = 0))
            return
        }
        val status = service.stop()
        try {
            unbindService(serviceConnection)
        } catch (e: IllegalArgumentException) {
            // already unbound
        }
        boundService = null
        onResult(status)
    }

    fun queryStatus(onResult: (CameraStatus) -> Unit) {
        val service = boundService
        if (service != null) {
            onResult(service.currentStatus())
        } else {
            onResult(CameraStatus(running = false, ip = null, rtspPort = 0, httpPort = 0))
        }
    }

    fun getAvailableCameras(): List<Map<String, Any?>> = CameraSourceRegistry.listAvailable(this)

    private fun withBoundService(action: (CameraServerService) -> Unit) {
        val service = boundService
        if (service != null) {
            action(service)
        } else {
            pendingAction = action
        }
    }
}
