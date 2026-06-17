package com.qrstudio.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qrstudio.core.qr.QrEncoder
import com.qrstudio.core.qr.QrType
import com.qrstudio.core.util.FileContainer
import com.qrstudio.core.util.FileExport
import com.qrstudio.core.util.Formatting
import com.qrstudio.core.util.QrSharing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrResultSheet(
    content: String,
    type: QrType,
    subtitle: String?,
    onDismiss: () -> Unit,
    foregroundArgb: Int? = null,
    onEdit: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Decoding a `qrsf:` container means base64 + inflate, which can be heavy for an
    // untrusted payload — keep it off the main thread. The cheap container check is
    // synchronous so the right branch renders immediately (never the raw base64).
    val isFileContainer = remember(content, type) {
        type == QrType.FILE && FileContainer.isContainer(content)
    }
    val decoded by produceState<FileContainer.Decoded?>(null, content, isFileContainer) {
        value = if (isFileContainer) {
            withContext(Dispatchers.Default) { FileContainer.decode(content) }
        } else {
            null
        }
    }
    val previewBitmap = remember(decoded) {
        decoded?.takeIf { it.mimeType.startsWith("image/") }?.let {
            runCatching { BitmapFactory.decodeByteArray(it.bytes, 0, it.bytes.size) }.getOrNull()
        }
    }

    val qrBitmap by produceState<Bitmap?>(initialValue = null, content, type, foregroundArgb) {
        value = withContext(Dispatchers.Default) {
            val style = QrEncoder.styleFor(type, foregroundArgb ?: android.graphics.Color.BLACK)
            (QrEncoder.encode(content, style) as? QrEncoder.Result.Success)?.bitmap
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = iconFor(type),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(8.dp).size(24.dp)
                    )
                }
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(text = type.frenchLabel, style = MaterialTheme.typography.titleMedium)
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            qrBitmap?.let { image ->
                QrImage(
                    bitmap = image,
                    modifier = Modifier
                        .fillMaxWidth(0.62f)
                        .align(Alignment.CenterHorizontally)
                )
            }

            if (isFileContainer) {
                decoded?.let { embedded ->
                    FileResultContent(
                        decoded = embedded,
                        previewBitmap = previewBitmap,
                        qrBitmap = qrBitmap
                    )
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SelectionContainer {
                        Text(
                            text = content,
                            style = MaterialTheme.typography.bodyMedium,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .heightIn(max = 160.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(14.dp)
                        )
                    }
                }
                QrActionButtons(content = content, type = type, bitmap = qrBitmap)
                if (onEdit != null) {
                    OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Edit, contentDescription = null)
                        Text("Réutiliser dans Créer", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FileResultContent(
    decoded: FileContainer.Decoded,
    previewBitmap: Bitmap?,
    qrBitmap: Bitmap?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun save() {
        scope.launch {
            // NonCancellable so closing the sheet mid-write can't leave a half-written
            // (IS_PENDING) ghost entry in MediaStore.
            val success = withContext(NonCancellable + Dispatchers.IO) {
                FileExport.saveToDownloads(
                    context = context,
                    fileName = decoded.suggestedName,
                    mimeType = decoded.mimeType,
                    bytes = decoded.bytes
                )
            }
            Toast.makeText(
                context,
                if (success) "Fichier enregistré dans Téléchargements/QR Studio."
                else "Échec de l'enregistrement.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val requestSave = rememberLegacyStorageSave { save() }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            previewBitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .padding(end = 12.dp)
                )
            }
            Column {
                Text("Fichier intégré", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "${decoded.mimeType} • ${Formatting.fileSize(decoded.bytes.size)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    Button(onClick = { requestSave() }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.Download, contentDescription = null)
        Text("Enregistrer le fichier", modifier = Modifier.padding(start = 8.dp))
    }
    OutlinedButton(
        onClick = { qrBitmap?.let { QrSharing.shareImage(context, it) } },
        enabled = qrBitmap != null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Outlined.Share, contentDescription = null)
        Text("Partager le QR code", modifier = Modifier.padding(start = 8.dp))
    }
}
