package com.aranustech.onvifcam.auth

import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

class NonceStore(private val validityMs: Long = 5 * 60 * 1000L) {
    private val nonces = ConcurrentHashMap<String, Long>()
    private val random = SecureRandom()

    fun generate(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        val nonce = bytes.joinToString("") { "%02x".format(it) }
        nonces[nonce] = System.currentTimeMillis()
        cleanup()
        return nonce
    }

    fun isValid(nonce: String): Boolean {
        val createdAt = nonces[nonce] ?: return false
        return System.currentTimeMillis() - createdAt < validityMs
    }

    private fun cleanup() {
        val now = System.currentTimeMillis()
        nonces.entries.removeIf { now - it.value > validityMs }
    }
}
