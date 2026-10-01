package com.aranustech.onvifcam.capture

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager

private const val USB_ID_PREFIX = "usb:"

/** Merges Camera2 devices and USB (UVC) webcams into one selectable camera list. */
object CameraSourceRegistry {
    fun listAvailable(context: Context): List<Map<String, Any?>> {
        val camera2 = CameraCaptureController.listAvailable(context)
        val usb = listUsbVideoDevices(context).map { device ->
            mapOf(
                "id" to "$USB_ID_PREFIX${device.deviceName}",
                "type" to "usb",
                "facing" to "external",
                "label" to (device.productName ?: device.deviceName),
            )
        }
        return camera2 + usb
    }

    fun create(context: Context, cameraId: String?): CameraSource {
        if (cameraId != null && cameraId.startsWith(USB_ID_PREFIX)) {
            val deviceName = cameraId.removePrefix(USB_ID_PREFIX)
            val device = listUsbVideoDevices(context).firstOrNull { it.deviceName == deviceName }
            if (device != null) {
                return UvcCaptureController(context, device)
            }
            // Fall through to Camera2 with no preference if the USB device vanished.
        }
        return CameraCaptureController(context, cameraId?.takeUnless { it.startsWith(USB_ID_PREFIX) })
    }

    private fun listUsbVideoDevices(context: Context): List<UsbDevice> {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        return usbManager.deviceList.values.filter { device ->
            (0 until device.interfaceCount).any { i ->
                device.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_VIDEO
            }
        }
    }
}
