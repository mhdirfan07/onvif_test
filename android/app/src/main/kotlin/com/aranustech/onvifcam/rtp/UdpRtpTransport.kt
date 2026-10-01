package com.aranustech.onvifcam.rtp

import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class UdpRtpTransport(private val clientAddress: InetAddress, private val clientRtpPort: Int) : RtpTransport {
    private val rtpSocket = DatagramSocket()
    private val rtcpSocket = DatagramSocket()

    val localRtpPort: Int get() = rtpSocket.localPort
    val localRtcpPort: Int get() = rtcpSocket.localPort

    override fun sendRtpPacket(packet: ByteArray): Boolean {
        return try {
            rtpSocket.send(DatagramPacket(packet, packet.size, clientAddress, clientRtpPort))
            true
        } catch (e: IOException) {
            false
        }
    }

    override fun close() {
        rtpSocket.close()
        rtcpSocket.close()
    }
}
