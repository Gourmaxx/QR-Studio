package com.qrstudio.core.util

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Reads the whole stream, but gives up (returns null) past [maxBytes].
 * User-picked SAF uris can point at arbitrarily large files (a 1.5 GB video…):
 * an unbounded readBytes() would OOM before any size check can run.
 */
fun InputStream.readBytesBounded(maxBytes: Long): ByteArray? {
    val buffer = ByteArrayOutputStream()
    val chunk = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val read = read(chunk)
        if (read < 0) break
        total += read
        if (total > maxBytes) return null
        buffer.write(chunk, 0, read)
    }
    return buffer.toByteArray()
}
