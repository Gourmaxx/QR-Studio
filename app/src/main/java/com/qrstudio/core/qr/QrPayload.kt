package com.qrstudio.core.qr

import java.net.URLEncoder

/**
 * Builders that turn structured form input into the standard QR payload strings
 * recognised by most readers (ZXing, native camera apps, iOS, etc.).
 */
object QrPayload {

    enum class WifiEncryption(val zxingValue: String, val frenchLabel: String) {
        WPA("WPA", "WPA/WPA2"),
        WEP("WEP", "WEP"),
        NONE("nopass", "Aucune (réseau ouvert)")
    }

    /** Normalises a raw URL by prepending https:// when no scheme is present. */
    fun url(raw: String): String {
        val value = raw.trim()
        if (value.isEmpty()) return value
        val hasScheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(value)
        return if (hasScheme) value else "https://$value"
    }

    fun wifi(
        ssid: String,
        password: String,
        encryption: WifiEncryption,
        hidden: Boolean
    ): String {
        val s = escapeWifi(ssid)
        val builder = StringBuilder("WIFI:T:${encryption.zxingValue};S:$s;")
        if (encryption != WifiEncryption.NONE) {
            builder.append("P:${escapeWifi(password)};")
        }
        if (hidden) builder.append("H:true;")
        builder.append(";")
        return builder.toString()
    }

    fun phone(number: String): String = "tel:${number.trim()}"

    fun sms(number: String, message: String): String {
        val n = number.trim()
        return if (message.isBlank()) "SMSTO:$n:" else "SMSTO:$n:${message.trim()}"
    }

    fun email(to: String, subject: String, body: String): String {
        val params = buildList {
            if (subject.isNotBlank()) add("subject=" + encode(subject))
            if (body.isNotBlank()) add("body=" + encode(body))
        }
        val query = if (params.isEmpty()) "" else "?" + params.joinToString("&")
        return "mailto:${to.trim()}$query"
    }

    fun geo(latitude: String, longitude: String): String =
        "geo:${latitude.trim()},${longitude.trim()}"

    fun vCard(
        fullName: String,
        organization: String,
        title: String,
        phone: String,
        email: String,
        url: String,
        address: String,
        note: String
    ): String = buildString {
        append("BEGIN:VCARD\n")
        append("VERSION:3.0\n")
        if (fullName.isNotBlank()) {
            append("FN:${escapeVCard(fullName)}\n")
            append("N:${escapeVCard(fullName)};;;;\n")
        }
        if (organization.isNotBlank()) append("ORG:${escapeVCard(organization)}\n")
        if (title.isNotBlank()) append("TITLE:${escapeVCard(title)}\n")
        if (phone.isNotBlank()) append("TEL;TYPE=CELL:${phone.trim()}\n")
        if (email.isNotBlank()) append("EMAIL:${email.trim()}\n")
        if (url.isNotBlank()) append("URL:${url(url)}\n")
        if (address.isNotBlank()) append("ADR;TYPE=HOME:;;${escapeVCard(address)};;;;\n")
        if (note.isNotBlank()) append("NOTE:${escapeVCard(note)}\n")
        append("END:VCARD")
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun escapeWifi(value: String): String =
        value.replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace(":", "\\:")
            .replace("\"", "\\\"")

    private fun escapeVCard(value: String): String =
        value.replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace(",", "\\,")
            .replace(";", "\\;")
}
