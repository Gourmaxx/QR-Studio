package com.qrstudio.core.qr

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer

/**
 * Decodes QR codes and the common 1D/2D barcode formats (EAN, UPC, Code 128,
 * Data Matrix, Aztec, PDF417, ...) from either a static [Bitmap] (gallery
 * import) or a raw camera luminance plane (CameraX analysis). Stateless and
 * therefore safe to call from any thread.
 */
object QrDecoder {

    private val liveHints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(
            BarcodeFormat.QR_CODE,
            BarcodeFormat.DATA_MATRIX,
            BarcodeFormat.AZTEC,
            BarcodeFormat.PDF_417,
            BarcodeFormat.EAN_13,
            BarcodeFormat.EAN_8,
            BarcodeFormat.UPC_A,
            BarcodeFormat.UPC_E,
            BarcodeFormat.CODE_128,
            BarcodeFormat.CODE_39,
            BarcodeFormat.CODE_93,
            BarcodeFormat.ITF,
            BarcodeFormat.CODABAR
        ),
        DecodeHintType.CHARACTER_SET to "UTF-8"
    )

    // TRY_HARDER is far too slow to run on every camera frame across 13 formats;
    // it is reserved for one-shot gallery decodes.
    private val thoroughHints = liveHints + (DecodeHintType.TRY_HARDER to true)

    /** Decode a still image (e.g. picked from the gallery). */
    fun decodeBitmap(bitmap: Bitmap): String? {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val source = RGBLuminanceSource(width, height, pixels)
        return decode(source, thoroughHints) ?: decode(source.invert(), thoroughHints)
    }

    /**
     * Decode the Y (luminance) plane of a YUV_420_888 camera frame.
     * [rowStride] handles row padding so cropping stays correct on every device.
     */
    fun decodeYuv(
        yPlane: ByteArray,
        rowStride: Int,
        dataHeight: Int,
        cropWidth: Int,
        cropHeight: Int
    ): String? {
        val width = minOf(cropWidth, rowStride)
        val height = minOf(cropHeight, dataHeight)
        val source = PlanarYUVLuminanceSource(
            yPlane, rowStride, dataHeight, 0, 0, width, height, false
        )
        return decode(source, liveHints)
    }

    private fun decode(source: LuminanceSource, hints: Map<DecodeHintType, Any>): String? {
        val reader = MultiFormatReader().apply { setHints(hints) }
        return try {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
        } catch (e: NotFoundException) {
            null
        } catch (e: Exception) {
            null
        } finally {
            reader.reset()
        }
    }
}
