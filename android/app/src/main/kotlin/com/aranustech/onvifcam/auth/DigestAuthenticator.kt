package com.aranustech.onvifcam.auth

import java.security.MessageDigest

/**
 * HTTP Digest (RFC 2617), shared verbatim between the RTSP server (RFC 2326
 * §19.6 reuses the same header syntax) and the SOAP HTTP server.
 */
class DigestAuthenticator(private val realm: String = "OnvifCam") {
    private val nonceStore = NonceStore()

    fun challenge(): String {
        val nonce = nonceStore.generate()
        return "Digest realm=\"$realm\", nonce=\"$nonce\", qop=\"auth\", algorithm=MD5"
    }

    fun validate(method: String, requestUri: String, authorizationHeader: String, username: String, password: String): Boolean {
        val params = parseDigestHeader(authorizationHeader) ?: return false
        if (params["username"] != username) return false
        val nonce = params["nonce"] ?: return false
        if (!nonceStore.isValid(nonce)) return false

        val digestUri = params["uri"] ?: requestUri
        val ha1 = md5("$username:$realm:$password")
        val ha2 = md5("$method:$digestUri")
        val qop = params["qop"]
        val expected = if (qop != null) {
            val nc = params["nc"] ?: return false
            val cnonce = params["cnonce"] ?: return false
            md5("$ha1:$nonce:$nc:$cnonce:$qop:$ha2")
        } else {
            md5("$ha1:$nonce:$ha2")
        }
        return expected.equals(params["response"], ignoreCase = true)
    }

    private fun parseDigestHeader(header: String): Map<String, String>? {
        if (!header.startsWith("Digest", ignoreCase = true)) return null
        val content = header.substringAfter(" ")
        val result = mutableMapOf<String, String>()
        val regex = Regex("(\\w+)=(\"[^\"]*\"|[^,]*)")
        for (match in regex.findAll(content)) {
            val key = match.groupValues[1]
            var value = match.groupValues[2].trim()
            if (value.startsWith("\"") && value.endsWith("\"")) value = value.substring(1, value.length - 1)
            result[key] = value
        }
        return result
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
