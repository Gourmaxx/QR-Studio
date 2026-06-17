package com.qrstudio.core.util

/**
 * Computes the BitmapFactory `inSampleSize` (a power of two) that keeps the
 * largest source dimension at or below [maxDimension]. Shared by the gallery
 * scan path and the file-embedding path, which both downsample before decoding
 * to bound memory. Returns 1 when the bounds are unknown (decode failed).
 */
fun sampleSizeFor(outWidth: Int, outHeight: Int, maxDimension: Int): Int {
    var sampleSize = 1
    val largest = maxOf(outWidth, outHeight)
    while (largest / sampleSize > maxDimension) sampleSize *= 2
    return sampleSize
}
