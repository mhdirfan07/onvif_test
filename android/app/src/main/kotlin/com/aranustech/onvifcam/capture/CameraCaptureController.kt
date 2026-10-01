package com.aranustech.onvifcam.capture

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.HandlerThread
import android.util.Range
import android.view.Surface
import com.aranustech.onvifcam.channel.EventBridge

/** Owns the single Camera2 capture session; frames are pushed straight into the encoder's input Surface. */
class CameraCaptureController(
    private val context: Context,
    private val preferredCameraId: String?,
) : CameraSource {
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private val cameraThread = HandlerThread("CameraThread").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)

    override var onError: ((String) -> Unit)? = null

    @SuppressLint("MissingPermission")
    override fun open(targetSurface: Surface, width: Int, height: Int, fps: Int, onOpened: () -> Unit) {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = preferredCameraId?.takeIf { manager.cameraIdList.contains(it) } ?: pickCameraId(manager)
        if (cameraId == null) {
            onError?.invoke("No camera available on this device")
            return
        }
        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) {
                cameraDevice = device
                createSession(device, targetSurface, fps, onOpened)
            }

            override fun onDisconnected(device: CameraDevice) {
                device.close()
                cameraDevice = null
            }

            override fun onError(device: CameraDevice, error: Int) {
                device.close()
                cameraDevice = null
                onError?.invoke("Camera device error code $error")
            }
        }, cameraHandler)
    }

    private fun pickCameraId(manager: CameraManager): String? {
        val ids = manager.cameraIdList
        for (id in ids) {
            val chars = manager.getCameraCharacteristics(id)
            if (chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) return id
        }
        return ids.firstOrNull()
    }

    private fun createSession(device: CameraDevice, targetSurface: Surface, fps: Int, onOpened: () -> Unit) {
        try {
            device.createCaptureSession(
                listOf(targetSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        try {
                            val request = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                                addTarget(targetSurface)
                                set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                                set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(fps, fps))
                            }
                            session.setRepeatingRequest(request.build(), null, cameraHandler)
                            onOpened()
                        } catch (e: Exception) {
                            onError?.invoke("Failed to start capture: ${e.message}")
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        onError?.invoke("Capture session configuration failed")
                    }
                },
                cameraHandler,
            )
        } catch (e: Exception) {
            onError?.invoke("Failed to create capture session: ${e.message}")
        }
    }

    override fun close() {
        try {
            captureSession?.close()
            cameraDevice?.close()
        } catch (e: Exception) {
            EventBridge.log("WARN", "Error closing camera: ${e.message}")
        }
        captureSession = null
        cameraDevice = null
        cameraThread.quitSafely()
    }

    companion object {
        /** Enumerable without CAMERA permission: id list + facing are static characteristics. */
        fun listAvailable(context: Context): List<Map<String, Any?>> {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            return manager.cameraIdList.map { id ->
                val chars = manager.getCameraCharacteristics(id)
                val facing = when (chars.get(CameraCharacteristics.LENS_FACING)) {
                    CameraCharacteristics.LENS_FACING_FRONT -> "front"
                    CameraCharacteristics.LENS_FACING_BACK -> "back"
                    CameraCharacteristics.LENS_FACING_EXTERNAL -> "external"
                    else -> "unknown"
                }
                mapOf("id" to id, "type" to "camera2", "facing" to facing)
            }
        }
    }
}
