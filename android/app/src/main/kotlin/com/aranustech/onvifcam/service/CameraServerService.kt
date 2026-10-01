package com.aranustech.onvifcam.service

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.aranustech.onvifcam.auth.DigestAuthenticator
import com.aranustech.onvifcam.capture.CameraSource
import com.aranustech.onvifcam.capture.CameraSourceRegistry
import com.aranustech.onvifcam.channel.EventBridge
import com.aranustech.onvifcam.encode.EncoderConfig
import com.aranustech.onvifcam.encode.H264Encoder
import com.aranustech.onvifcam.model.CameraStatus
import com.aranustech.onvifcam.model.ServerConfig
import com.aranustech.onvifcam.rtp.StreamFanOut
import com.aranustech.onvifcam.rtsp.RtspServer
import com.aranustech.onvifcam.util.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/**
 * Foreground service hosting the whole camera-server pipeline:
 * camera (Camera2 or USB/UVC) -> MediaCodec (H.264) -> RTP packetizer ->
 * RTSP server. WS-Discovery and the SOAP device/media services are added in
 * later milestones; this milestone is enough for a VMS to pull the stream
 * via a manually-entered RTSP URL.
 */
class CameraServerService : Service() {
    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var statusJob: Job? = null

    // Set by stop() to the thread doing (possibly slow/blocking) camera/codec
    // teardown; start() waits for it so a fast stop-then-start never races a
    // still-in-flight close() for the same camera/USB device or RTSP port.
    private var teardownThread: Thread? = null

    private var config: ServerConfig? = null
    private var running = false
    private var cameraOpen = false
    private var lastError: String? = null

    private var encoder: H264Encoder? = null
    private var cameraController: CameraSource? = null
    private var rtspServer: RtspServer? = null
    private val fanOut = StreamFanOut()
    private val paramSets = AtomicReference<Pair<ByteArray, ByteArray>?>(null)

    inner class LocalBinder : Binder() {
        fun getService(): CameraServerService = this@CameraServerService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    fun start(cfg: ServerConfig, callback: (CameraStatus) -> Unit) {
        startForeground(ForegroundNotification.NOTIFICATION_ID, ForegroundNotification.build(this, "Starting..."))
        val previousTeardown = teardownThread
        Thread {
            // Bounded wait: in the common case the previous stop's cleanup is
            // already done or finishes in well under this; if a device is
            // truly stuck we still proceed rather than block start forever.
            try {
                previousTeardown?.join(3000)
            } catch (e: InterruptedException) {
                // proceed anyway
            }
            doStart(cfg)
            mainHandler.post { callback(currentStatus()) }
        }.start()
    }

    private fun doStart(cfg: ServerConfig) {
        config = cfg
        running = true
        lastError = null
        EventBridge.log("INFO", "Starting camera server: rtsp port ${cfg.rtspPort}, http port ${cfg.httpPort}")

        val encoderConfig = EncoderConfig()
        val newEncoder = H264Encoder(encoderConfig)
        newEncoder.onParamSets = { sps, pps ->
            paramSets.set(Pair(sps, pps))
            EventBridge.log("INFO", "Encoder parameter sets ready (SPS ${sps.size}B, PPS ${pps.size}B)")
        }
        newEncoder.onAccessUnit = { nals, isKeyFrame, ptsUs ->
            val finalNals = if (isKeyFrame) {
                val params = paramSets.get()
                if (params != null) listOf(params.first, params.second) + nals else nals
            } else {
                nals
            }
            fanOut.sendAccessUnit(finalNals, ptsUs)
        }
        newEncoder.start()
        encoder = newEncoder

        val authenticator = DigestAuthenticator(realm = "OnvifCam")
        val newRtspServer = RtspServer(
            port = cfg.rtspPort,
            fanOut = fanOut,
            paramSetsProvider = { paramSets.get() },
            onClientAboutToPlay = { encoder?.requestKeyFrame() },
            authenticator = authenticator,
            credentials = { Pair(config?.username ?: "admin", config?.password ?: "admin") },
        )
        newRtspServer.start()
        rtspServer = newRtspServer

        val newCameraController = CameraSourceRegistry.create(this, cfg.cameraId)
        newCameraController.onError = { message ->
            lastError = message
            EventBridge.log("ERROR", message)
        }
        newCameraController.open(newEncoder.inputSurface, encoderConfig.width, encoderConfig.height, encoderConfig.fps) {
            cameraOpen = true
            EventBridge.log("INFO", "Camera opened at ${encoderConfig.width}x${encoderConfig.height}@${encoderConfig.fps}")
        }
        cameraController = newCameraController

        statusJob?.cancel()
        statusJob = serviceScope.launch {
            while (isActive && running) {
                val status = currentStatus()
                EventBridge.emit(status.toMap())
                updateNotification(status)
                delay(2000)
            }
        }
    }

    fun stop(): CameraStatus {
        running = false
        statusJob?.cancel()
        rtspServer?.stop()
        rtspServer = null
        paramSets.set(null)
        cameraOpen = false

        // Native camera/codec teardown can block for a long time (or forever)
        // if the underlying device already vanished (e.g. a USB webcam that
        // was unplugged) — a synchronous JNI call waiting on a dead USB
        // connection. Run it on a throwaway thread so it can never freeze
        // the caller, which runs on Flutter's platform/main thread.
        val controllerToClose = cameraController
        val encoderToStop = encoder
        cameraController = null
        encoder = null
        val t = Thread {
            try {
                controllerToClose?.close()
            } catch (e: Exception) {
                EventBridge.log("WARN", "Error closing camera during stop: ${e.message}")
            }
            try {
                encoderToStop?.stop()
            } catch (e: Exception) {
                EventBridge.log("WARN", "Error stopping encoder during stop: ${e.message}")
            }
        }
        teardownThread = t
        t.start()

        EventBridge.log("INFO", "Server stopped")
        val status = currentStatus()
        EventBridge.emit(status.toMap())
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        return status
    }

    fun currentStatus(): CameraStatus = CameraStatus(
        running = running,
        ip = NetworkUtils.getLocalIpv4Address(),
        rtspPort = config?.rtspPort ?: 8554,
        httpPort = config?.httpPort ?: 8080,
        rtspClients = rtspServer?.clientCount() ?: 0,
        cameraOpen = cameraOpen,
        lastError = lastError,
    )

    private fun updateNotification(status: CameraStatus) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val text = when {
            status.ip == null -> "Waiting for network..."
            !status.cameraOpen -> "Opening camera..."
            else -> "Streaming ready at rtsp://${status.ip}:${status.rtspPort}/onvif1 (${status.rtspClients} clients)"
        }
        manager.notify(ForegroundNotification.NOTIFICATION_ID, ForegroundNotification.build(this, text))
    }

    override fun onDestroy() {
        if (running) stop()
        serviceScope.cancel()
        super.onDestroy()
    }
}
