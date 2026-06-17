package com.qrstudio.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ContactPage
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import com.qrstudio.core.qr.QrType

fun iconFor(type: QrType): ImageVector = when (type) {
    QrType.URL -> Icons.Outlined.Link
    QrType.TEXT -> Icons.Outlined.Notes
    QrType.WIFI -> Icons.Outlined.Wifi
    QrType.CONTACT -> Icons.Outlined.ContactPage
    QrType.EMAIL -> Icons.Outlined.Email
    QrType.SMS -> Icons.Outlined.Sms
    QrType.PHONE -> Icons.Outlined.Call
    QrType.GEO -> Icons.Outlined.LocationOn
    QrType.FILE -> Icons.Outlined.InsertDriveFile
}
