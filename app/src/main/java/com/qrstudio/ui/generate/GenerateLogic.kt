package com.qrstudio.ui.generate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.qrstudio.core.qr.QrPayload
import com.qrstudio.core.qr.QrType
import com.qrstudio.core.util.FileContainer
import com.qrstudio.core.util.FileEmbedding
import com.qrstudio.core.util.Formatting
import com.qrstudio.core.util.readBytesBounded
import com.qrstudio.core.util.sampleSizeFor

/** Result of turning a picked file/image into an embeddable QR payload. */
sealed interface FileOutcome {
    data class Ready(val payload: String, val info: String) : FileOutcome
    data class Error(val message: String) : FileOutcome
}

/** Builds the encodable payload from the current form fields, or null if required input is missing. */
fun buildPayload(type: QrType, form: GenerateFormState, filePayload: String?): String? = when (type) {
    QrType.URL -> form.url.ifBlank { null }?.let { QrPayload.url(it) }
    QrType.TEXT -> form.text.ifBlank { null }
    QrType.WIFI -> form.wifiSsid.ifBlank { null }?.let {
        QrPayload.wifi(
            ssid = it,
            password = form.wifiPassword,
            encryption = QrPayload.WifiEncryption.entries[form.wifiEncIndex],
            hidden = form.wifiHidden
        )
    }
    QrType.CONTACT -> form.contactName.ifBlank { null }?.let {
        QrPayload.vCard(
            fullName = it,
            organization = form.contactOrg,
            title = "",
            phone = form.contactPhone,
            email = form.contactEmail,
            url = "",
            address = "",
            note = ""
        )
    }
    QrType.EMAIL -> form.emailTo.ifBlank { null }?.let {
        QrPayload.email(to = it, subject = form.emailSubject, body = form.emailBody)
    }
    QrType.SMS -> form.smsNumber.ifBlank { null }?.let {
        QrPayload.sms(number = it, message = form.smsMessage)
    }
    QrType.PHONE -> form.phoneNumber.ifBlank { null }?.let { QrPayload.phone(it) }
    QrType.GEO -> if (form.geoLat.isBlank() || form.geoLng.isBlank()) null
    else QrPayload.geo(form.geoLat, form.geoLng)
    QrType.FILE -> filePayload
}

fun buildLabel(type: QrType, form: GenerateFormState, fileInfo: String?): String? = when (type) {
    QrType.WIFI -> form.wifiSsid.ifBlank { null }
    QrType.CONTACT -> form.contactName.ifBlank { null }
    QrType.FILE -> fileInfo
    else -> null
}

/** Reads the picked content into a compact `qrsf` container, compressing/scaling to fit one QR. */
fun buildFilePayload(context: Context, uri: Uri, isImage: Boolean): FileOutcome {
    val resolver = context.contentResolver
    val displayName = queryDisplayName(context, uri) ?: if (isImage) "image" else "fichier"
    return try {
        val stream = resolver.openInputStream(uri)
            ?: return FileOutcome.Error("Lecture du fichier impossible.")
        // Bounded read: a QR holds ~2.9 KB anyway, no point loading a huge pick
        // into memory (and risking OOM) before rejecting it.
        val bytes = stream.use { it.readBytesBounded(MAX_SOURCE_BYTES) }
            ?: return FileOutcome.Error(
                "Fichier beaucoup trop volumineux. Un QR code ne peut stocker " +
                    "qu'environ 2,9 Ko."
            )
        if (isImage) {
            val source = decodeBoundedBitmap(bytes, MAX_SOURCE_DIMENSION)
                ?: return FileOutcome.Error("Image illisible.")
            val fitted = FileEmbedding.fitImage(source)
                ?: return FileOutcome.Error(
                    "Image trop volumineuse même compressée. Un QR code ne stocke " +
                        "qu'environ 2,9 Ko : choisissez une image plus simple."
                )
            FileOutcome.Ready(
                payload = FileContainer.encode(fitted, "image/jpeg"),
                info = "$displayName (miniature ${Formatting.fileSize(fitted.size)})"
            )
        } else {
            val mime = resolver.getType(uri) ?: "application/octet-stream"
            val payload = FileContainer.encode(bytes, mime)
            if (payload.length > FileContainer.MAX_PAYLOAD_CHARS) {
                return FileOutcome.Error(
                    "Fichier trop volumineux (${Formatting.fileSize(bytes.size)}), même compressé. " +
                        "Un QR code ne peut stocker qu'environ 2,9 Ko."
                )
            }
            FileOutcome.Ready(
                payload = payload,
                info = "$displayName (${Formatting.fileSize(bytes.size)})"
            )
        }
    } catch (e: Exception) {
        FileOutcome.Error("Impossible de lire ce fichier.")
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    } catch (e: Exception) {
        null
    }
}

private const val MAX_SOURCE_DIMENSION = 1280

/** Upper bound for reading a picked file/image into memory (images are thumbnailed after). */
private const val MAX_SOURCE_BYTES = 20L * 1024 * 1024

/** Decodes a downsampled bitmap to bound memory before thumbnailing (avoids OOM on huge images). */
private fun decodeBoundedBitmap(bytes: ByteArray, maxDimension: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxDimension)
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}
