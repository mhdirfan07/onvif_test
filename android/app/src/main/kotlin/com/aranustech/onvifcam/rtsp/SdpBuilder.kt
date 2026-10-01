package com.aranustech.onvifcam.rtsp

import android.util.Base64

object SdpBuilder {
    fun build(sps: ByteArray, pps: ByteArray, sessionName: String = "OnvifCam"): String {
        val spropParams = Base64.encodeToString(sps, Base64.NO_WRAP) + "," + Base64.encodeToString(pps, Base64.NO_WRAP)
        val profileLevelId = if (sps.size >= 4) {
            String.format("%02x%02x%02x", sps[1], sps[2], sps[3])
        } else {
            "420029"
        }
        return buildString {
            append("v=0\r\n")
            append("o=- 0 0 IN IP4 0.0.0.0\r\n")
            append("s=$sessionName\r\n")
            append("c=IN IP4 0.0.0.0\r\n")
            append("t=0 0\r\n")
            append("a=control:*\r\n")
            append("m=video 0 RTP/AVP 96\r\n")
            append("a=rtpmap:96 H264/90000\r\n")
            append("a=fmtp:96 packetization-mode=1;profile-level-id=$profileLevelId;sprop-parameter-sets=$spropParams\r\n")
            append("a=control:trackID=1\r\n")
        }
    }
}
