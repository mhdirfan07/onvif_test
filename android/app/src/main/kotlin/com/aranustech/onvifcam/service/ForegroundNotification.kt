package com.aranustech.onvifcam.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.aranustech.onvifcam.MainActivity

object ForegroundNotification {
    const val CHANNEL_ID = "onvifcam_service"
    const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Camera server",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Keeps the ONVIF camera server running in the background"
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun build(context: Context, contentText: String): Notification {
        ensureChannel(context)
        val openAppIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(context, CHANNEL_ID)
            .setContentTitle("OnvifCam server running")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .build()
    }
}
