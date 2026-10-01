package com.aranustech.onvifcam.rtp

/** Splits an Annex-B byte stream (0x000001 / 0x00000001 start codes) into individual NAL units. */
object NalUnitUtil {
    fun splitAnnexB(data: ByteArray): List<ByteArray> {
        val markers = mutableListOf<Pair<Int, Int>>() // (nalStartOffset, markerLength)
        var i = 0
        val n = data.size
        while (i + 2 < n) {
            if (data[i] == 0.toByte() && data[i + 1] == 0.toByte()) {
                if (data[i + 2] == 1.toByte()) {
                    markers.add(Pair(i + 3, 3))
                    i += 3
                    continue
                } else if (i + 3 < n && data[i + 2] == 0.toByte() && data[i + 3] == 1.toByte()) {
                    markers.add(Pair(i + 4, 4))
                    i += 4
                    continue
                }
            }
            i++
        }
        val result = mutableListOf<ByteArray>()
        for (idx in markers.indices) {
            val start = markers[idx].first
            val end = if (idx + 1 < markers.size) {
                markers[idx + 1].first - markers[idx + 1].second
            } else {
                n
            }
            if (end > start) result.add(data.copyOfRange(start, end))
        }
        return result
    }

    fun nalType(nal: ByteArray): Int = if (nal.isEmpty()) -1 else nal[0].toInt() and 0x1F
}
