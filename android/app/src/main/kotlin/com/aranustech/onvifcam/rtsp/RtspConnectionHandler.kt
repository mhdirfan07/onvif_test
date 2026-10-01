package com.aranustech.onvifcam.rtsp

import com.aranustech.onvifcam.auth.DigestAuthenticator
import com.aranustech.onvifcam.channel.EventBridge
import com.aranustech.onvifcam.rtp.InterleavedRtpTransport
import com.aranustech.onvifcam.rtp.RtpSession
import com.aranustech.onvifcam.rtp.StreamFanOut
import com.aranustech.onvifcam.rtp.UdpRtpTransport
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Socket
import java.util.UUID

/**
 * Per-client RTSP state machine: OPTIONS/DESCRIBE/SETUP/PLAY/TEARDOWN,
 * RTP/AVP unicast only. HTTP Digest auth (shared with the SOAP server) is
 * enforced via [authenticator] when non-null.
 */
class RtspConnectionHandler(
    private val socket: Socket,
    private val fanOut: StreamFanOut,
    private val paramSetsProvider: () -> Pair<ByteArray, ByteArray>?,
    private val onClientAboutToPlay: () -> Unit,
    private val authenticator: DigestAuthenticator?,
    private val credentials: () -> Pair<String, String>,
) {
    private val rtspSessionId: String = UUID.randomUUID().toString().take(8)
    private var rtpSession: RtpSession? = null
    private val writeLock = Any()

    fun handle() {
        try {
            val input = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val output = socket.getOutputStream()
            while (true) {
                val requestLine = input.readLine() ?: break
                if (requestLine.isBlank()) continue

                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = input.readLine() ?: break
                    if (line.isBlank()) break
                    val idx = line.indexOf(':')
                    if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
                }

                val parts = requestLine.split(" ")
                if (parts.size < 2) break
                val method = parts[0]
                val uri = parts[1]
                val cseq = headers["cseq"] ?: "0"

                if (authenticator != null && method != "OPTIONS") {
                    val (user, pass) = credentials()
                    val authHeader = headers["authorization"]
                    if (authHeader == null || !authenticator.validate(method, uri, authHeader, user, pass)) {
                        respond(
                            output, cseq, 401, "Unauthorized",
                            mapOf("WWW-Authenticate" to authenticator.challenge()),
                        )
                        continue
                    }
                }

                when (method) {
                    "OPTIONS" -> respond(
                        output, cseq, 200, "OK",
                        mapOf("Public" to "OPTIONS, DESCRIBE, SETUP, PLAY, TEARDOWN, GET_PARAMETER"),
                    )
                    "DESCRIBE" -> handleDescribe(output, cseq)
                    "SETUP" -> handleSetup(output, cseq, headers)
                    "PLAY" -> handlePlay(output, cseq)
                    "GET_PARAMETER" -> respond(output, cseq, 200, "OK", mapOf("Session" to rtspSessionId))
                    "TEARDOWN" -> {
                        respond(output, cseq, 200, "OK", mapOf("Session" to rtspSessionId))
                        return
                    }
                    else -> respond(output, cseq, 501, "Not Implemented", emptyMap())
                }
            }
        } catch (e: IOException) {
            // client disconnected
        } finally {
            cleanup()
        }
    }

    private fun cleanup() {
        rtpSession?.let { fanOut.removeClient(it.id) }
        try {
            socket.close()
        } catch (e: IOException) {
            // already closed
        }
    }

    private fun handleDescribe(output: OutputStream, cseq: String) {
        val params = paramSetsProvider()
        if (params == null) {
            respond(output, cseq, 503, "Service Unavailable", emptyMap())
            return
        }
        val sdp = SdpBuilder.build(params.first, params.second)
        val headers = linkedMapOf(
            "Content-Base" to "rtsp://${(socket.localAddress.hostAddress)}:${socket.localPort}/onvif1/",
            "Content-Type" to "application/sdp",
        )
        respond(output, cseq, 200, "OK", headers, sdp.toByteArray(Charsets.UTF_8))
    }

    private fun handleSetup(output: OutputStream, cseq: String, headers: Map<String, String>) {
        val transportHeader = headers["transport"] ?: ""
        EventBridge.log("INFO", "SETUP Transport header: $transportHeader")

        val udpMatch = Regex("client_port=(\\d+)-(\\d+)").find(transportHeader)
        val tcpMatch = Regex("interleaved=(\\d+)-(\\d+)").find(transportHeader)

        val session: RtpSession
        val responseTransport: String

        when {
            udpMatch != null -> {
                val clientRtpPort = udpMatch.groupValues[1].toInt()
                val clientRtcpPort = udpMatch.groupValues[2].toInt()
                val udpTransport = UdpRtpTransport(socket.inetAddress, clientRtpPort)
                session = RtpSession(udpTransport)
                responseTransport = "RTP/AVP;unicast;client_port=$clientRtpPort-$clientRtcpPort;" +
                    "server_port=${udpTransport.localRtpPort}-${udpTransport.localRtcpPort};ssrc=${"%08x".format(session.ssrc)}"
            }
            tcpMatch != null -> {
                val rtpChannel = tcpMatch.groupValues[1].toInt()
                val interleavedTransport = InterleavedRtpTransport(output, rtpChannel, writeLock)
                session = RtpSession(interleavedTransport)
                responseTransport = "RTP/AVP/TCP;unicast;interleaved=${tcpMatch.groupValues[1]}-${tcpMatch.groupValues[2]};" +
                    "ssrc=${"%08x".format(session.ssrc)}"
            }
            else -> {
                respond(output, cseq, 461, "Unsupported Transport", emptyMap())
                return
            }
        }

        rtpSession = session
        fanOut.addClient(session)
        respond(
            output, cseq, 200, "OK",
            linkedMapOf("Transport" to responseTransport, "Session" to rtspSessionId),
        )
    }

    private fun handlePlay(output: OutputStream, cseq: String) {
        onClientAboutToPlay()
        respond(
            output, cseq, 200, "OK",
            linkedMapOf("Session" to rtspSessionId, "Range" to "npt=0.000-"),
        )
        EventBridge.log("INFO", "RTSP PLAY from ${socket.inetAddress.hostAddress}")
    }

    private fun respond(
        output: OutputStream,
        cseq: String,
        code: Int,
        reason: String,
        headers: Map<String, String>,
        body: ByteArray? = null,
    ) {
        val sb = StringBuilder()
        sb.append("RTSP/1.0 $code $reason\r\n")
        sb.append("CSeq: $cseq\r\n")
        for ((k, v) in headers) sb.append("$k: $v\r\n")
        if (body != null) sb.append("Content-Length: ${body.size}\r\n")
        sb.append("\r\n")
        synchronized(writeLock) {
            output.write(sb.toString().toByteArray(Charsets.UTF_8))
            if (body != null) output.write(body)
            output.flush()
        }
    }
}
