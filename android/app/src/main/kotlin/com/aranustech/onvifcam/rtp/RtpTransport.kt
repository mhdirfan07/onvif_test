package com.aranustech.onvifcam.rtp

/** Delivers raw RTP packets to one connected client, over UDP or interleaved-TCP. */
interface RtpTransport {
    fun sendRtpPacket(packet: ByteArray): Boolean
    fun close()
}
