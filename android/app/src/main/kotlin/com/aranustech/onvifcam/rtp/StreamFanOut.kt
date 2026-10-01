package com.aranustech.onvifcam.rtp

import com.aranustech.onvifcam.channel.EventBridge
import java.util.concurrent.ConcurrentHashMap

/** Fans out one encoded access unit to every connected RTSP client's RtpSession. */
class StreamFanOut {
    private val sessions = ConcurrentHashMap<String, RtpSession>()

    fun addClient(session: RtpSession) {
        sessions[session.id] = session
        EventBridge.log("INFO", "RTSP client connected (${sessions.size} total)")
    }

    fun removeClient(id: String) {
        sessions.remove(id)?.close()
        EventBridge.log("INFO", "RTSP client disconnected (${sessions.size} total)")
    }

    fun sessionCount(): Int = sessions.size

    fun sendAccessUnit(nals: List<ByteArray>, ptsUs: Long) {
        if (sessions.isEmpty()) return
        val timestamp90k = ptsUs * 90000L / 1_000_000L
        val payloads = RtpPacketizer.buildPayloads(nals)
        for (session in sessions.values.toList()) {
            if (!session.sendAccessUnit(payloads, timestamp90k)) {
                removeClient(session.id)
            }
        }
    }
}
