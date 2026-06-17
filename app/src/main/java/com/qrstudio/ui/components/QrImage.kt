package com.qrstudio.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.qrstudio.ui.theme.QrCanvas

/** Renders a QR bitmap on a white rounded card. FilterQuality.None keeps modules crisp. */
@Composable
fun QrImage(bitmap: Bitmap, modifier: Modifier = Modifier) {
    Surface(
        color = QrCanvas,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "QR code",
            filterQuality = FilterQuality.None,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .padding(16.dp)
                .aspectRatio(1f)
        )
    }
}
