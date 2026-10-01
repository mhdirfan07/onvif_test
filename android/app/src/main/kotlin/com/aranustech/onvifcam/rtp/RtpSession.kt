package com.aranustech.onvifcam.rtp

import java.util.UUID
import kotlin.random.Random

/** Per-connected-client RTP send state, registered on RTSP SETUP/PLAY, dropped on TEARDOWN or send failure. */
class RtpSession(private val transport: RtpTransport) {
    val id: String = UUID.randomUUID().toString()
    val ssrc: Int = Random.nextInt()

    private var seq: Int = Random.nextInt(0, 0xFFFF)
    private val timestampOffset: Long = Random.nextLong(0, 0xFFFFFFFFL)

    fun sendAccessUnit(payloads: List<Pair<ByteArray, Boolean>>, timestamp90k: Long): Boolean {
        val ts = (timestampOffset + timestamp90k) and 0xFFFFFFFFL
        for ((payload, isLast) in payloads) {
            val header = ByteArray(12)
            header[0] = 0x80.toByte()
            header[1] = ((if (isLast) 0x80 else 0x00) or 96).toByte()
            header[2] = ((seq shr 8) and 0xFF).toByte()
            header[3] = (seq and 0xFF).toByte()
            header[4] = ((ts shr 24) and 0xFF).toByte()
            header[5] = ((ts shr 16) and 0xFF).toByte()
            header[6] = ((ts shr 8) and 0xFF).toByte()
            header[7] = (ts and 0xFF).toByte()
            header[8] = ((ssrc shr 24) and 0xFF).toByte()
            header[9] = ((ssrc shr 16) and 0xFF).toByte()
            header[10] = ((ssrc shr 8) and 0xFF).toByte()
            header[11] = (ssrc and 0xFF).toByte()
            seq = (seq + 1) and 0xFFFF

            val packet = ByteArray(header.size + payload.size)
            System.arraycopy(header, 0, packet, 0, header.size)
            System.arraycopy(payload, 0, packet, header.size, payload.size)
            if (!transport.sendRtpPacket(packet)) return false
        }
        return true
    }

    fun close() {
        transport.close()
    }
}
