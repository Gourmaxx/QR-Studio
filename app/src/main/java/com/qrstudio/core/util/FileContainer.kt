package com.qrstudio.core.util

import android.util.Base64
import java.net.URLDecoder

/**
 * Compact container that packs a small file/image into a single QR payload.
 *
 * Format: `qrsf:1:<flag>:<mime>:<base64>` where `flag` = 1 when the bytes were
 * Deflate-compressed (helps text/binary; skipped for already-compressed images).
 * Standard `data:` URIs are also decoded for interoperability.
 */
object FileContainer {

    /** Usable payload chars in a single QR at error-correction level L (with margin). */
    const val MAX_PAYLOAD_CHARS = 2900

    private const val PREFIX = "qrsf:1:"

    data class Decoded(val mimeType: String, val bytes: ByteArray, val suggestedName: String)

    fun encode(bytes: ByteArray, mimeType: String): String {
        val deflated = Compression.deflate(bytes)
        val useDeflate = deflated.size < bytes.size
        val payloadBytes = if (useDeflate) deflated else bytes
        val base64 = Base64.encodeToString(payloadBytes, Base64.NO_WRAP)
        return PREFIX + (if (useDeflate) "1" else "0") + ":" + mimeType + ":" + base64
    }

    fun isContainer(payload: String): Boolean =
        payload.startsWith(PREFIX) || payload.startsWith("data:")

    fun decode(payload: String): Decoded? = try {
        when {
            payload.startsWith(PREFIX) -> {
                // Format: <flag>:<mime>:<base64>. base64 never contains ':', so peel it off
                // the end; the mime keeps everything in between (it may itself contain ':').
                val body = payload.removePrefix(PREFIX)
                val flag = body.substringBefore(':')
                val rest = body.substringAfter(':', "")
                if (!rest.contains(':')) {
                    null
                } else {
                    val base64 = rest.substringAfterLast(':')
                    val mime = rest.substringBeforeLast(':')
                    val raw = Base64.decode(base64, Base64.NO_WRAP)
                    // inflate() returns null past its output ceiling (decompression bomb guard).
                    val bytes = if (flag == "1") Compression.inflate(raw) else raw
                    if (bytes == null) null else Decoded(mime, bytes, nameFor(mime))
                }
            }
            payload.startsWith("data:") -> {
                val comma = payload.indexOf(',')
                if (comma < 0) {
                    null
                } else {
                    val meta = payload.substring(5, comma)
                    val data = payload.substring(comma + 1)
                    val mime = meta.substringBefore(';').ifBlank { "application/octet-stream" }
                    val bytes = if (meta.contains("base64")) {
                        Base64.decode(data, Base64.DEFAULT)
                    } else {
                        URLDecoder.decode(data, "UTF-8").toByteArray(Charsets.UTF_8)
                    }
                    Decoded(mime, bytes, nameFor(mime))
                }
            }
            else -> null
        }
    } catch (e: Exception) {
        null
    }

    private fun nameFor(mimeType: String): String {
        val extension = when {
            mimeType == "image/jpeg" || mimeType == "image/jpg" -> "jpg"
            mimeType == "image/png" -> "png"
            mimeType == "image/webp" -> "webp"
            mimeType == "image/gif" -> "gif"
            mimeType == "application/pdf" -> "pdf"
            mimeType.startsWith("text/") -> "txt"
            else -> "bin"
        }
        return "qrstudio_${System.currentTimeMillis()}.$extension"
    }
}
