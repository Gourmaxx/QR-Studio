package com.qrstudio.core.qr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Renders QR payloads to bitmaps using ZXing. Pure rendering, no Android UI deps
 * beyond [Bitmap] so it can be unit-tested and reused off the main thread.
 */
object QrEncoder {

    /** Max bytes a version-40 QR can hold at the L correction level (byte mode). */
    const val MAX_CAPACITY_BYTES = 2953

    sealed interface Result {
        data class Success(val bitmap: Bitmap) : Result
        data class Failure(val reason: String) : Result
    }

    data class Style(
        val sizePx: Int = 1024,
        val foreground: Int = Color.BLACK,
        val background: Int = Color.WHITE,
        val margin: Int = 2,
        val correction: ErrorCorrectionLevel = ErrorCorrectionLevel.M,
        val logo: Bitmap? = null
    )

    /**
     * The encoding style for a given [type]: FILE payloads are dense, so they use
     * the lowest correction level (L) for maximum capacity and carry neither a
     * logo nor a custom ink (contrast margin matters); everything else can.
     * Single source of truth for the generator, the result sheet and the widget.
     */
    fun styleFor(
        type: QrType,
        foregroundArgb: Int = Color.BLACK,
        logo: Bitmap? = null,
        sizePx: Int = 1024
    ): Style = if (type == QrType.FILE) {
        Style(sizePx = sizePx, correction = ErrorCorrectionLevel.L)
    } else {
        Style(sizePx = sizePx, foreground = foregroundArgb, logo = logo)
    }

    fun encode(content: String, style: Style = Style()): Result {
        if (content.isEmpty()) return Result.Failure("Le contenu est vide.")
        // A logo punches a hole in the matrix, so force high error correction.
        val correction = if (style.logo != null) ErrorCorrectionLevel.H else style.correction
        return try {
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to correction,
                EncodeHintType.MARGIN to style.margin,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            val matrix = QRCodeWriter()
                .encode(content, BarcodeFormat.QR_CODE, style.sizePx, style.sizePx, hints)
            val bitmap = matrix.toBitmap(style.foreground, style.background)
            style.logo?.let { drawLogo(bitmap, it, style.background) }
            Result.Success(bitmap)
        } catch (e: WriterException) {
            Result.Failure(
                "Contenu trop volumineux pour un QR code (limite ≈ $MAX_CAPACITY_BYTES octets)."
            )
        } catch (e: IllegalArgumentException) {
            Result.Failure(
                "Contenu trop volumineux pour un QR code (limite ≈ $MAX_CAPACITY_BYTES octets)."
            )
        }
    }

    private fun BitMatrix.toBitmap(foreground: Int, background: Int): Bitmap {
        val w = width
        val h = height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val offset = y * w
            for (x in 0 until w) {
                pixels[offset + x] = if (get(x, y)) foreground else background
            }
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            it.setPixels(pixels, 0, w, 0, 0, w, h)
        }
    }

    private fun drawLogo(target: Bitmap, logo: Bitmap, background: Int) {
        val canvas = Canvas(target)
        val logoSize = target.width * 0.22f
        val cx = target.width / 2f
        val cy = target.height / 2f
        val pad = logoSize * 0.14f

        val plate = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background }
        val plateRect = RectF(
            cx - logoSize / 2f - pad,
            cy - logoSize / 2f - pad,
            cx + logoSize / 2f + pad,
            cy + logoSize / 2f + pad
        )
        val radius = plateRect.width() * 0.18f
        canvas.drawRoundRect(plateRect, radius, radius, plate)

        val dst = RectF(
            cx - logoSize / 2f,
            cy - logoSize / 2f,
            cx + logoSize / 2f,
            cy + logoSize / 2f
        )
        canvas.drawBitmap(logo, null, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }
}
