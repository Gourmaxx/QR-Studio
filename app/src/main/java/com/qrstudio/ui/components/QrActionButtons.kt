package com.qrstudio.ui.components

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.qrstudio.core.qr.QrType
import com.qrstudio.core.util.QrActions
import com.qrstudio.core.util.QrSharing

/**
 * Standard action set for a payload: its contextual primary action (open link,
 * call, join Wi-Fi…) plus copy / share / save-image / share-image. Reused by the
 * generator preview, the scan result sheet and the history detail.
 */
@Composable
fun QrActionButtons(
    content: String,
    type: QrType,
    bitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    fun saveToGallery() {
        val image = bitmap ?: return
        val name = "qrstudio_${System.currentTimeMillis()}"
        val saved = QrSharing.saveToGallery(context, image, name)
        Toast.makeText(
            context,
            if (saved) "Image enregistrée dans la galerie." else "Échec de l'enregistrement.",
            Toast.LENGTH_SHORT
        ).show()
    }

    val requestSave = rememberLegacyStorageSave { saveToGallery() }

    val primaryLabel = remember(type) { QrActions.primaryActionLabel(type) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (primaryLabel != null) {
            Button(
                onClick = { QrActions.runPrimaryAction(context, type, content) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.OpenInNew, contentDescription = null)
                Text(text = primaryLabel, modifier = Modifier.padding(start = 8.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryAction(
                label = "Copier",
                icon = Icons.Outlined.ContentCopy,
                modifier = Modifier.weight(1f)
            ) {
                QrSharing.copyToClipboard(context, content, sensitive = type == QrType.WIFI)
                Toast.makeText(context, "Copié.", Toast.LENGTH_SHORT).show()
            }
            SecondaryAction(
                label = "Partager",
                icon = Icons.Outlined.Share,
                modifier = Modifier.weight(1f)
            ) { QrSharing.shareText(context, content) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryAction(
                label = "Enregistrer",
                icon = Icons.Outlined.Download,
                enabled = bitmap != null,
                modifier = Modifier.weight(1f)
            ) { requestSave() }
            SecondaryAction(
                label = "Image",
                icon = Icons.Outlined.Image,
                enabled = bitmap != null,
                modifier = Modifier.weight(1f)
            ) { bitmap?.let { QrSharing.shareImage(context, it) } }
        }
    }
}

@Composable
private fun SecondaryAction(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Icon(icon, contentDescription = null)
        Text(text = label, modifier = Modifier.padding(start = 6.dp))
    }
}
