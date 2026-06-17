package com.qrstudio.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import com.qrstudio.R

/** Rasterises the app mark (vector) into a bitmap for embedding at the centre of a QR. */
object LogoRenderer {

    fun appMark(context: Context, sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        ContextCompat.getDrawable(context, R.drawable.ic_app_mark)?.apply {
            setBounds(0, 0, sizePx, sizePx)
            draw(canvas)
        }
        return bitmap
    }
}
