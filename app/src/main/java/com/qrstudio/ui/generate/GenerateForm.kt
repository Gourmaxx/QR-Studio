package com.qrstudio.ui.generate

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.qrstudio.core.qr.QrPayload
import com.qrstudio.core.qr.QrType
import com.qrstudio.ui.components.AppTextField
import com.qrstudio.ui.components.iconFor
import com.qrstudio.ui.theme.CyanDeep
import com.qrstudio.ui.theme.QrInkBerry
import com.qrstudio.ui.theme.QrInkBlack
import com.qrstudio.ui.theme.QrInkBlue
import com.qrstudio.ui.theme.QrInkGreen
import com.qrstudio.ui.theme.Violet

/** Categories shown in the picker, in display order. */
private val CATEGORY_ORDER = listOf(
    QrType.URL, QrType.TEXT, QrType.WIFI, QrType.EMAIL,
    QrType.SMS, QrType.PHONE, QrType.CONTACT, QrType.GEO, QrType.FILE
)

/** Ink colour presets for the generated QR; black first as the default. */
private val INK_PRESETS = listOf(QrInkBlack, Violet, CyanDeep, QrInkBlue, QrInkGreen, QrInkBerry)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CategoryGrid(selected: QrType, onSelect: (QrType) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CATEGORY_ORDER.forEach { type ->
            FilterChip(
                selected = selected == type,
                onClick = { onSelect(type) },
                label = { Text(type.frenchLabel) },
                leadingIcon = {
                    Icon(iconFor(type), contentDescription = null, modifier = Modifier.size(18.dp))
                }
            )
        }
    }
}

@Composable
internal fun TypeForm(
    type: QrType,
    form: GenerateFormState,
    fileInfo: String?,
    fileProcessing: Boolean,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (type) {
            QrType.URL -> AppTextField(
                form.url, { form.url = it }, "Adresse du lien", keyboardType = KeyboardType.Uri
            )

            QrType.TEXT -> AppTextField(
                form.text, { form.text = it }, "Texte à encoder", singleLine = false, minLines = 3
            )

            QrType.WIFI -> {
                AppTextField(form.wifiSsid, { form.wifiSsid = it }, "Nom du réseau (SSID)")
                WifiEncryptionSelector(form.wifiEncIndex) { form.wifiEncIndex = it }
                if (form.wifiEncIndex != QrPayload.WifiEncryption.NONE.ordinal) {
                    AppTextField(
                        form.wifiPassword, { form.wifiPassword = it }, "Mot de passe",
                        keyboardType = KeyboardType.Password
                    )
                }
                ToggleRow("Réseau masqué", form.wifiHidden) { form.wifiHidden = it }
            }

            QrType.CONTACT -> {
                AppTextField(form.contactName, { form.contactName = it }, "Nom complet")
                AppTextField(form.contactOrg, { form.contactOrg = it }, "Organisation")
                AppTextField(
                    form.contactPhone, { form.contactPhone = it }, "Téléphone",
                    keyboardType = KeyboardType.Phone
                )
                AppTextField(
                    form.contactEmail, { form.contactEmail = it }, "E-mail",
                    keyboardType = KeyboardType.Email
                )
            }

            QrType.EMAIL -> {
                AppTextField(
                    form.emailTo, { form.emailTo = it }, "Destinataire",
                    keyboardType = KeyboardType.Email
                )
                AppTextField(form.emailSubject, { form.emailSubject = it }, "Objet")
                AppTextField(
                    form.emailBody, { form.emailBody = it }, "Message",
                    singleLine = false, minLines = 2
                )
            }

            QrType.SMS -> {
                AppTextField(
                    form.smsNumber, { form.smsNumber = it }, "Numéro",
                    keyboardType = KeyboardType.Phone
                )
                AppTextField(
                    form.smsMessage, { form.smsMessage = it }, "Message",
                    singleLine = false, minLines = 2
                )
            }

            QrType.PHONE -> AppTextField(
                form.phoneNumber, { form.phoneNumber = it }, "Numéro de téléphone",
                keyboardType = KeyboardType.Phone
            )

            QrType.GEO -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppTextField(
                    form.geoLat, { form.geoLat = it }, "Latitude",
                    keyboardType = KeyboardType.Decimal, modifier = Modifier.weight(1f)
                )
                AppTextField(
                    form.geoLng, { form.geoLng = it }, "Longitude",
                    keyboardType = KeyboardType.Decimal, modifier = Modifier.weight(1f)
                )
            }

            QrType.FILE -> FileForm(fileInfo, fileProcessing, onPickImage, onPickFile)
        }
    }
}

@Composable
private fun WifiEncryptionSelector(selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QrPayload.WifiEncryption.entries.forEachIndexed { index, encryption ->
            FilterChip(
                selected = selectedIndex == index,
                onClick = { onSelect(index) },
                label = { Text(encryption.frenchLabel) }
            )
        }
    }
}

@Composable
internal fun InkSelector(selectedArgb: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Couleur du QR", style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            INK_PRESETS.forEach { color ->
                InkSwatch(
                    color = color,
                    selected = color.toArgb() == selectedArgb,
                    onClick = { onSelect(color.toArgb()) }
                )
            }
        }
    }
}

@Composable
private fun InkSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                shape = CircleShape
            )
            .clickable(onClick = onClick)
    )
}

@Composable
internal fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun FileForm(
    info: String?,
    processing: Boolean,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Le fichier est compressé et intégré au QR (~2,9 Ko max). Les images " +
                "deviennent une miniature. En scannant ce QR, QR Studio peut ré-enregistrer " +
                "le fichier d'origine.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onPickImage, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Image, contentDescription = null)
                Text("Image", modifier = Modifier.padding(start = 6.dp))
            }
            OutlinedButton(onClick = onPickFile, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.AttachFile, contentDescription = null)
                Text("Fichier", modifier = Modifier.padding(start = 6.dp))
            }
        }
        if (processing) Text("Traitement…", style = MaterialTheme.typography.labelMedium)
        info?.let {
            Text(
                text = "Sélectionné : $it",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
