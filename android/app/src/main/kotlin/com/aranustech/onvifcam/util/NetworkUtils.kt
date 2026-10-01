package com.aranustech.onvifcam.util

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {
    fun getLocalIpv4Address(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces()?.asSequence()
                ?.filter { it.isUp && !it.isLoopback }
                ?.flatMap { it.inetAddresses.asSequence() }
                ?.filterIsInstance<Inet4Address>()
                ?.map { it.hostAddress }
                ?.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }
}
