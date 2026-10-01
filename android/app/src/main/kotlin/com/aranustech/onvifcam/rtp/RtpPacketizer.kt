package com.aranustech.onvifcam.rtp

/** RFC 6184 H.264-over-RTP packetization: single-NAL packets, FU-A fragmentation for large NALs. */
object RtpPacketizer {
    const val MAX_PAYLOAD = 1200

    /** Returns RTP payloads for one access unit, each paired with whether it's the last packet (marker bit). */
    fun buildPayloads(nals: List<ByteArray>, maxPayload: Int = MAX_PAYLOAD): List<Pair<ByteArray, Boolean>> {
        val packets = mutableListOf<ByteArray>()
        for (nal in nals) {
            if (nal.isEmpty()) continue
            if (nal.size <= maxPayload) {
                packets.add(nal)
            } else {
                packets.addAll(fragment(nal, maxPayload))
            }
        }
        return packets.mapIndexed { index, payload -> Pair(payload, index == packets.size - 1) }
    }

    private fun fragment(nal: ByteArray, maxPayload: Int): List<ByteArray> {
        val header = nal[0].toInt() and 0xFF
        val nalType = header and 0x1F
        val nri = header and 0x60
        val fuIndicator = (nri or 28).toByte()
        val payloadData = nal.copyOfRange(1, nal.size)
        val chunkSize = (maxPayload - 2).coerceAtLeast(1)

        val result = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < payloadData.size) {
            val end = (offset + chunkSize).coerceAtMost(payloadData.size)
            val isFirst = offset == 0
            val isLast = end == payloadData.size
            var fuHeader = nalType
            if (isFirst) fuHeader = fuHeader or 0x80
            if (isLast) fuHeader = fuHeader or 0x40
            val packet = ByteArray(2 + (end - offset))
            packet[0] = fuIndicator
            packet[1] = fuHeader.toByte()
            System.arraycopy(payloadData, offset, packet, 2, end - offset)
            result.add(packet)
            offset = end
        }
        return result
    }
}
