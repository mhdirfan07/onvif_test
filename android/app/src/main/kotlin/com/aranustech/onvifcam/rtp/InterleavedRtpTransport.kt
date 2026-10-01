package com.aranustech.onvifcam.rtp

import java.io.IOException
import java.io.OutputStream

/**
 * RFC 2326 §10.12 interleaved binary framing: '$' + channel + 2-byte length + payload,
 * multiplexed onto the same TCP connection as the RTSP request/response text.
 * [writeLock] must be the same lock the connection handler uses for its textual
 * responses, since both share one [OutputStream].
 */
class InterleavedRtpTransport(
    private val output: OutputStream,
    private val channel: Int,
    private val writeLock: Any,
) : RtpTransport {
    override fun sendRtpPacket(packet: ByteArray): Boolean {
        return try {
            synchronized(writeLock) {
                val frameHeader = byteArrayOf(
                    0x24.toByte(),
                    channel.toByte(),
                    ((packet.size shr 8) and 0xFF).toByte(),
                    (packet.size and 0xFF).toByte(),
                )
                output.write(frameHeader)
                output.write(packet)
                output.flush()
            }
            true
        } catch (e: IOException) {
            false
        }
    }

    override fun close() {
        // Shared connection socket is closed by RtspConnectionHandler.
    }
}
