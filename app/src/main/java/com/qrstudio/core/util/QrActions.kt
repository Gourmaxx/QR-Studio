package com.qrstudio.core.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.qrstudio.core.qr.QrParser
import com.qrstudio.core.qr.QrPayload
import com.qrstudio.core.qr.QrType
import java.io.File

/**
 * Contextual actions for a decoded payload: open a link, dial, send an e-mail,
 * add a contact, join a Wi-Fi network, etc. Every launch is guarded so a missing
 * handler app surfaces a toast instead of crashing.
 */
object QrActions {

    /** Short label for the primary action of a type, or null when there is none. */
    fun primaryActionLabel(type: QrType): String? = when (type) {
        QrType.URL -> "Ouvrir le lien"
        QrType.PHONE -> "Appeler"
        QrType.SMS -> "Envoyer le SMS"
        QrType.EMAIL -> "Envoyer l'e-mail"
        QrType.GEO -> "Voir sur la carte"
        QrType.CONTACT -> "Ajouter le contact"
        QrType.WIFI -> "Se connecter au Wi-Fi"
        QrType.TEXT, QrType.FILE -> null
    }

    fun runPrimaryAction(context: Context, type: QrType, content: String) {
        when (type) {
            QrType.URL -> openUri(context, QrPayload.url(content))
            QrType.PHONE -> safeStart(context, Intent(Intent.ACTION_DIAL, Uri.parse(content)))
            QrType.SMS -> sendSms(context, content)
            QrType.EMAIL -> safeStart(context, Intent(Intent.ACTION_SENDTO, Uri.parse(content)))
            QrType.GEO -> openUri(context, content)
            QrType.CONTACT -> addContact(context, content)
            QrType.WIFI -> joinWifi(context, content)
            QrType.TEXT, QrType.FILE -> Unit
        }
    }

    private fun openUri(context: Context, uri: String) {
        safeStart(context, Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
    }

    private fun sendSms(context: Context, content: String) {
        // Handles SMSTO:number:message and sms:number?body=… in any case mix.
        val form = QrParser.toForm(content, QrType.SMS)
        val number = form?.smsNumber ?: content.substringAfter(':').substringBefore(':')
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).apply {
            form?.smsMessage?.takeIf { it.isNotBlank() }?.let { putExtra("sms_body", it) }
        }
        safeStart(context, intent)
    }

    private fun addContact(context: Context, vcard: String) {
        try {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val file = File(dir, "contact_${System.currentTimeMillis()}.vcf")
            file.writeText(vcard)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "text/x-vcard")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            safeStart(context, intent)
        } catch (e: Exception) {
            toast(context, "Impossible d'ouvrir le contact.")
        }
    }

    /** On Android 11+ proposes the network via the system dialog; otherwise opens Wi-Fi settings. */
    private fun joinWifi(context: Context, content: String) {
        val info = QrParser.parseWifi(content)
        if (info == null) {
            safeStart(context, Intent(Settings.ACTION_WIFI_SETTINGS))
            return
        }
        // WifiNetworkSuggestion cannot express WEP: suggesting it would offer an
        // *open* network of the same SSID, doomed to fail — use the fallback.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            info.encryption != QrPayload.WifiEncryption.WEP
        ) {
            try {
                val builder = WifiNetworkSuggestion.Builder().setSsid(info.ssid)
                if (info.encryption == QrPayload.WifiEncryption.WPA && info.password.isNotEmpty()) {
                    builder.setWpa2Passphrase(info.password)
                }
                val suggestions = arrayListOf(builder.build())
                val intent = Intent(Settings.ACTION_WIFI_ADD_NETWORKS).apply {
                    putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, suggestions)
                }
                safeStart(context, intent)
                return
            } catch (e: Exception) {
                // Fall through to opening Wi-Fi settings.
            }
        }
        QrSharing.copyToClipboard(context, info.password, sensitive = true)
        toast(context, "Mot de passe copié. Ouverture des réglages Wi-Fi…")
        safeStart(context, Intent(Settings.ACTION_WIFI_SETTINGS))
    }

    private fun safeStart(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            toast(context, "Aucune application disponible pour cette action.")
        }
    }

    private fun toast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
