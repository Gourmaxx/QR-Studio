package com.qrstudio.core.util

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

/** Raw Deflate/Inflate helpers used to shrink payloads before QR encoding. */
object Compression {

    fun deflate(input: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(input)
        deflater.finish()
        val output = ByteArrayOutputStream(maxOf(64, input.size / 2))
        val buffer = ByteArray(4096)
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            output.write(buffer, 0, count)
        }
        deflater.end()
        return output.toByteArray()
    }

    /** Default decompression ceiling: far above any single-QR payload, yet bounds untrusted input. */
    const val DEFAULT_MAX_OUTPUT_BYTES = 16L * 1024 * 1024

    /**
     * Inflates [input], giving up (returns null) once the output would exceed
     * [maxOutputBytes]. The bound matters because `qrsf:` payloads are attacker
     * controlled (scanned, or merged from an imported history): Deflate reaches
     * ~1000:1, so a tiny payload could otherwise expand into gigabytes and OOM.
     */
    fun inflate(input: ByteArray, maxOutputBytes: Long = DEFAULT_MAX_OUTPUT_BYTES): ByteArray? {
        val inflater = Inflater()
        inflater.setInput(input)
        val output = ByteArrayOutputStream(maxOf(64, input.size * 2))
        val buffer = ByteArray(4096)
        try {
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0) {
                    // Nothing more decodable (truncated input or missing dictionary): stop
                    // rather than spin forever.
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                } else {
                    if (output.size().toLong() + count > maxOutputBytes) return null
                    output.write(buffer, 0, count)
                }
            }
        } finally {
            inflater.end()
        }
        return output.toByteArray()
    }
}
