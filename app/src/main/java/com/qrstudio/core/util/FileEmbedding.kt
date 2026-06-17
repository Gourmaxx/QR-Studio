package com.qrstudio.core.util

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

/**
 * Scales/recompresses an image into a thumbnail small enough to embed in a single
 * QR (a QR stores at most ~2.9 KB, base64 inflates by ~33 %). Progressively
 * downscales until it fits, or returns null when even an aggressive thumbnail can't.
 */
object FileEmbedding {

    /** Usable raw image bytes so the base64 qrsf payload stays within one QR (ECL L). */
    const val MAX_RAW_BYTES = 2150

    /**
     * Returns JPEG bytes of [source] scaled/compressed to fit [maxRaw], or null
     * if even an aggressive thumbnail cannot fit.
     */
    fun fitImage(source: Bitmap, maxRaw: Int = MAX_RAW_BYTES): ByteArray? {
        val targetDimensions = intArrayOf(256, 192, 144, 112, 88, 64, 48, 32)
        val qualities = intArrayOf(80, 60, 45, 30, 20)
        for (dimension in targetDimensions) {
            val scaled = scaleToFit(source, dimension)
            for (quality in qualities) {
                val bytes = ByteArrayOutputStream().use { out ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    out.toByteArray()
                }
                if (bytes.size <= maxRaw) return bytes
            }
        }
        return null
    }

    private fun scaleToFit(source: Bitmap, maxDimension: Int): Bitmap {
        val largest = maxOf(source.width, source.height)
        if (largest <= maxDimension) return source
        val ratio = maxDimension.toFloat() / largest
        val width = (source.width * ratio).toInt().coerceAtLeast(1)
        val height = (source.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }
}
