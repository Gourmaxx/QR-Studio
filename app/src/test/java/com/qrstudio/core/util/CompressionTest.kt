package com.qrstudio.core.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompressionTest {

    @Test
    fun `deflate then inflate round trips`() {
        val original = "Hello, QR Studio — accents éàü and symbols ;:,\\".toByteArray()
        val restored = Compression.inflate(Compression.deflate(original))
        assertArrayEquals(original, restored)
    }

    @Test
    fun `inflate gives up past the output ceiling (decompression bomb guard)`() {
        // Highly compressible input: a small deflated blob that expands a lot.
        val original = ByteArray(200_000) // all zeros -> tiny once deflated
        val deflated = Compression.deflate(original)
        // A ceiling below the real output must abort rather than allocate it.
        assertNull(Compression.inflate(deflated, maxOutputBytes = 1_000))
    }

    @Test
    fun `inflate within the ceiling still decodes fully`() {
        val original = ByteArray(200_000)
        val deflated = Compression.deflate(original)
        val restored = Compression.inflate(deflated, maxOutputBytes = 1_000_000)
        assertArrayEquals(original, restored)
    }
}
