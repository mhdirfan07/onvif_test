package com.aranustech.onvifcam.capture

import android.content.Context
import android.hardware.usb.UsbDevice
import android.view.Surface
import com.aranustech.onvifcam.channel.EventBridge
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera

/**
 * Drives a USB webcam directly over USB (UVC protocol), bypassing Camera2 —
 * needed because most phones' camera HAL does not stably surface external
 * UVC devices through Camera2 (confirmed via `dumpsys media.camera` showing
 * the external device flicker ADD/REMOVE). [USBMonitor] owns the permission
 * request flow; once granted it hands back a UsbControlBlock we open the
 * UVCCamera with, rendering straight onto the encoder's input Surface.
 */
class UvcCaptureController(
    private val context: Context,
    private val targetDevice: UsbDevice,
) : CameraSource {
    override var onError: ((String) -> Unit)? = null

    private var monitor: USBMonitor? = null
    private var camera: UVCCamera? = null

    override fun open(targetSurface: Surface, width: Int, height: Int, fps: Int, onOpened: () -> Unit) {
        val usbMonitor = USBMonitor(context, object : USBMonitor.OnDeviceConnectListener {
            override fun onAttach(device: UsbDevice) {
                if (device.deviceId != targetDevice.deviceId) return
                EventBridge.log("INFO", "USB camera attached, requesting permission")
                monitor?.requestPermission(device)
            }

            override fun onDettach(device: UsbDevice) {
                // no-op: onDisconnect handles teardown once connected
            }

            override fun onConnect(device: UsbDevice, ctrlBlock: USBMonitor.UsbControlBlock, createNew: Boolean) {
                if (device.deviceId != targetDevice.deviceId) return
                try {
                    val cam = UVCCamera()
                    cam.open(ctrlBlock)
                    try {
                        EventBridge.log("INFO", "USB camera supported sizes: ${cam.getSupportedSize()}")
                    } catch (e: Exception) {
                        // diagnostic only
                    }

                    // Must match the encoder's configured input size: MediaCodec's
                    // input Surface is fixed to that size, and a mismatched UVC
                    // capture size can silently starve the encoder of usable
                    // frames. Many webcams only expose their higher resolutions
                    // (e.g. 720p) as MJPEG, not raw YUYV — the library's default
                    // mode — so try MJPEG at the target size first.
                    val attempts = listOf(
                        Triple(width, height, UVCCamera.FRAME_FORMAT_MJPEG),
                        Triple(width, height, UVCCamera.FRAME_FORMAT_YUYV),
                        Triple(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, UVCCamera.FRAME_FORMAT_MJPEG),
                        Triple(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, UVCCamera.FRAME_FORMAT_YUYV),
                    )
                    var sizeApplied = false
                    for ((w, h, format) in attempts) {
                        try {
                            cam.setPreviewSize(w, h, format)
                            EventBridge.log(
                                "INFO",
                                "USB camera preview size set to ${w}x$h (format=$format)" +
                                    if (w != width || h != height) " — requested ${width}x$height was unavailable" else "",
                            )
                            sizeApplied = true
                            break
                        } catch (e: IllegalArgumentException) {
                            EventBridge.log("WARN", "USB camera rejected ${w}x$h format=$format: ${e.message}")
                        }
                    }
                    if (!sizeApplied) {
                        EventBridge.log("ERROR", "USB camera accepted no preview size/format combination we tried")
                    }
                    cam.setPreviewDisplay(targetSurface)
                    cam.startPreview()
                    camera = cam
                    EventBridge.log("INFO", "USB camera streaming (${ctrlBlock.productName ?: device.deviceName})")
                    onOpened()
                } catch (e: Exception) {
                    onError?.invoke("Failed to open USB camera: ${e.message}")
                }
            }

            override fun onDisconnect(device: UsbDevice, ctrlBlock: USBMonitor.UsbControlBlock) {
                if (device.deviceId == targetDevice.deviceId) {
                    onError?.invoke("USB camera disconnected")
                }
            }

            override fun onCancel(device: UsbDevice) {
                if (device.deviceId == targetDevice.deviceId) {
                    onError?.invoke("USB camera permission denied")
                }
            }
        })
        monitor = usbMonitor
        usbMonitor.register()
    }

    override fun close() {
        try {
            camera?.stopPreview()
            camera?.destroy()
        } catch (e: Exception) {
            EventBridge.log("WARN", "Error closing USB camera: ${e.message}")
        }
        camera = null
        try {
            monitor?.unregister()
            monitor?.destroy()
        } catch (e: Exception) {
            // already torn down
        }
        monitor = null
    }
}
