package com.aranustech.onvifcam.channel

import com.aranustech.onvifcam.MainActivity
import com.aranustech.onvifcam.model.ServerConfig
import com.aranustech.onvifcam.util.NetworkUtils
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class ControlMethodChannel(private val activity: MainActivity) : MethodChannel.MethodCallHandler {
    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "startServer" -> {
                val args = call.arguments as? Map<*, *>
                if (args == null) {
                    result.error("bad_args", "startServer requires a config map", null)
                    return
                }
                activity.startCameraServer(ServerConfig.fromMap(args)) { status ->
                    result.success(status.toMap())
                }
            }
            "stopServer" -> activity.stopCameraServer { status -> result.success(status.toMap()) }
            "getStatus" -> activity.queryStatus { status -> result.success(status.toMap()) }
            "getLocalIpAddress" -> result.success(NetworkUtils.getLocalIpv4Address())
            "getAvailableCameras" -> result.success(activity.getAvailableCameras())
            else -> result.notImplemented()
        }
    }
}
