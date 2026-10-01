package com.aranustech.onvifcam.rtsp

import com.aranustech.onvifcam.auth.DigestAuthenticator
import com.aranustech.onvifcam.channel.EventBridge
import com.aranustech.onvifcam.rtp.StreamFanOut
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean

class RtspServer(
    private val port: Int,
    private val fanOut: StreamFanOut,
    private val paramSetsProvider: () -> Pair<ByteArray, ByteArray>?,
    private val onClientAboutToPlay: () -> Unit,
    private val authenticator: DigestAuthenticator?,
    private val credentials: () -> Pair<String, String>,
) {
    private var serverSocket: ServerSocket? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)

    fun start() {
        running.set(true)
        scope.launch {
            try {
                // reuseAddress lets us rebind immediately after a fast stop/start
                // cycle, instead of failing with EADDRINUSE while the previous
                // socket lingers in TIME_WAIT.
                val ss = ServerSocket()
                ss.reuseAddress = true
                ss.bind(InetSocketAddress(port))
                serverSocket = ss
                EventBridge.log("INFO", "RTSP server listening on port $port")
                while (running.get()) {
                    val socket = try {
                        ss.accept()
                    } catch (e: IOException) {
                        break
                    }
                    scope.launch {
                        RtspConnectionHandler(
                            socket, fanOut, paramSetsProvider, onClientAboutToPlay, authenticator, credentials,
                        ).handle()
                    }
                }
            } catch (e: IOException) {
                EventBridge.log("ERROR", "RTSP server failed to start: ${e.message}")
            }
        }
    }

    fun stop() {
        running.set(false)
        try {
            serverSocket?.close()
        } catch (e: IOException) {
            // ignore
        }
        scope.cancel()
    }

    fun clientCount(): Int = fanOut.sessionCount()
}
