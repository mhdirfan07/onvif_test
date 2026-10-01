package com.aranustech.onvifcam.encode

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import com.aranustech.onvifcam.channel.EventBridge
import com.aranustech.onvifcam.rtp.NalUnitUtil

/**
 * Wraps MediaCodec in surface-input mode: Camera2 draws directly into
 * [inputSurface] (zero-copy), encoded Annex-B access units come back via
 * [onAccessUnit]; SPS/PPS parameter sets via [onParamSets].
 */
class H264Encoder(private val config: EncoderConfig) {
    lateinit var inputSurface: Surface
        private set

    private lateinit var codec: MediaCodec
    private val callbackThread = HandlerThread("EncoderCallbackThread").apply { start() }
    private val callbackHandler = Handler(callbackThread.looper)

    var onAccessUnit: ((nals: List<ByteArray>, isKeyFrame: Boolean, presentationTimeUs: Long) -> Unit)? = null
    var onParamSets: ((sps: ByteArray, pps: ByteArray) -> Unit)? = null
    private var accessUnitCount = 0L

    fun start() {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, config.width, config.height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, config.bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, config.fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
        }

        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.setCallback(object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                // Not used: frames arrive via the input Surface, not input buffers.
            }

            override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                val buffer = codec.getOutputBuffer(index)
                if (buffer != null && info.size > 0) {
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    val bytes = ByteArray(info.size)
                    buffer.get(bytes)

                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        val nals = NalUnitUtil.splitAnnexB(bytes)
                        val sps = nals.firstOrNull { NalUnitUtil.nalType(it) == 7 }
                        val pps = nals.firstOrNull { NalUnitUtil.nalType(it) == 8 }
                        if (sps != null && pps != null) onParamSets?.invoke(sps, pps)
                    } else {
                        val isKeyFrame = info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
                        val nals = NalUnitUtil.splitAnnexB(bytes)
                        if (nals.isNotEmpty()) {
                            accessUnitCount++
                            if (accessUnitCount == 1L) {
                                EventBridge.log("INFO", "Encoder produced its first frame (${bytes.size}B, keyFrame=$isKeyFrame)")
                            } else if (accessUnitCount % 150 == 0L) {
                                EventBridge.log("INFO", "Encoder has produced $accessUnitCount frames so far")
                            }
                            onAccessUnit?.invoke(nals, isKeyFrame, info.presentationTimeUs)
                        }
                    }
                }
                codec.releaseOutputBuffer(index, false)
            }

            override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                EventBridge.log("ERROR", "Encoder error: ${e.message}")
            }

            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                val spsBuf = format.getByteBuffer("csd-0")
                val ppsBuf = format.getByteBuffer("csd-1")
                if (spsBuf != null && ppsBuf != null) {
                    val spsRaw = ByteArray(spsBuf.remaining()).also { spsBuf.get(it) }
                    val ppsRaw = ByteArray(ppsBuf.remaining()).also { ppsBuf.get(it) }
                    val sps = NalUnitUtil.splitAnnexB(spsRaw).firstOrNull() ?: spsRaw
                    val pps = NalUnitUtil.splitAnnexB(ppsRaw).firstOrNull() ?: ppsRaw
                    onParamSets?.invoke(sps, pps)
                }
            }
        }, callbackHandler)

        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = codec.createInputSurface()
        codec.start()
    }

    fun requestKeyFrame() {
        if (!::codec.isInitialized) return
        val params = Bundle()
        params.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
        codec.setParameters(params)
    }

    fun stop() {
        try {
            codec.stop()
            codec.release()
        } catch (e: Exception) {
            EventBridge.log("WARN", "Error stopping encoder: ${e.message}")
        }
        callbackThread.quitSafely()
    }
}
