package com.qrstudio.core.qr

import java.net.URLDecoder

/**
 * Parses standard QR payload strings back into structured values — the inverse
 * of [QrPayload]. Pure JVM (no Android dependency) so it stays unit-testable.
 */
object QrParser {

    data class WifiInfo(
        val ssid: String,
        val password: String,
        val encryption: QrPayload.WifiEncryption,
        val hidden: Boolean
    )

    /**
     * Editable form values recovered from a payload. Only the fields relevant
     * to [type] are populated; everything else keeps its empty default.
     */
    data class FormPrefill(
        val type: QrType,
        val urlValue: String = "",
        val textValue: String = "",
        val wifiSsid: String = "",
        val wifiPassword: String = "",
        val wifiEncIndex: Int = 0,
        val wifiHidden: Boolean = false,
        val contactName: String = "",
        val contactOrg: String = "",
        val contactPhone: String = "",
        val contactEmail: String = "",
        val emailTo: String = "",
        val emailSubject: String = "",
        val emailBody: String = "",
        val smsNumber: String = "",
        val smsMessage: String = "",
        val phoneNumber: String = "",
        val geoLat: String = "",
        val geoLng: String = ""
    )

    /**
     * Best-effort recovery of form fields from a payload, or null when the
     * content cannot be edited as a form (embedded files, MECARD contacts…).
     */
    fun toForm(content: String, type: QrType): FormPrefill? {
        val value = content.trim()
        return when (type) {
            QrType.URL -> FormPrefill(type, urlValue = value)
            QrType.TEXT -> FormPrefill(type, textValue = content)
            QrType.WIFI -> parseWifi(value)?.let {
                FormPrefill(
                    type,
                    wifiSsid = it.ssid,
                    wifiPassword = it.password,
                    wifiEncIndex = it.encryption.ordinal,
                    wifiHidden = it.hidden
                )
            }
            QrType.EMAIL -> parseMailto(value)
            QrType.SMS -> parseSms(value)
            QrType.PHONE -> FormPrefill(type, phoneNumber = stripPrefix(value, "tel:").trim())
            QrType.GEO -> parseGeo(value)
            QrType.CONTACT -> parseVCard(value)
            QrType.FILE -> null
        }
    }

    /** Parses a WIFI:…;; payload, unescaping the backslash-escaped values. */
    fun parseWifi(content: String): WifiInfo? {
        if (!content.startsWith("WIFI:", ignoreCase = true)) return null
        val fields = splitWifiFields(content.substring(5))
        val type = fields["T"] ?: "nopass"
        val ssid = fields["S"] ?: return null
        val password = fields["P"] ?: ""
        val hidden = fields["H"]?.equals("true", ignoreCase = true) == true
        val encryption = when (type.uppercase()) {
            "WPA", "WPA2", "WPA3" -> QrPayload.WifiEncryption.WPA
            "WEP" -> QrPayload.WifiEncryption.WEP
            else -> QrPayload.WifiEncryption.NONE
        }
        return WifiInfo(ssid, password, encryption, hidden)
    }

    private fun splitWifiFields(body: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val current = StringBuilder()
        val tokens = mutableListOf<String>()
        var i = 0
        while (i < body.length) {
            val c = body[i]
            when {
                c == '\\' && i + 1 < body.length -> {
                    current.append(body[i + 1]); i += 2
                }
                c == ';' -> {
                    tokens.add(current.toString()); current.clear(); i++
                }
                else -> {
                    current.append(c); i++
                }
            }
        }
        if (current.isNotEmpty()) tokens.add(current.toString())
        tokens.forEach { token ->
            val idx = token.indexOf(':')
            if (idx > 0) result[token.substring(0, idx).uppercase()] = token.substring(idx + 1)
        }
        return result
    }

    private fun parseMailto(value: String): FormPrefill? {
        if (!value.startsWith("mailto:", ignoreCase = true)) return null
        val body = value.substring("mailto:".length)
        val queryStart = body.indexOf('?')
        val rawTo = if (queryStart >= 0) body.substring(0, queryStart) else body
        // Some generators percent-encode the address part too.
        val to = runCatching { URLDecoder.decode(rawTo, "UTF-8") }.getOrDefault(rawTo)
        var subject = ""
        var message = ""
        if (queryStart >= 0) {
            queryParams(body.substring(queryStart + 1)).forEach { (key, decoded) ->
                when (key) {
                    "subject" -> subject = decoded
                    "body" -> message = decoded
                }
            }
        }
        return FormPrefill(QrType.EMAIL, emailTo = to.trim(), emailSubject = subject, emailBody = message)
    }

    private fun parseSms(value: String): FormPrefill? {
        val body = when {
            value.startsWith("smsto:", ignoreCase = true) -> value.substring("smsto:".length)
            value.startsWith("sms:", ignoreCase = true) -> value.substring("sms:".length)
            else -> return null
        }
        val queryIndex = body.indexOf('?')
        val colonIndex = body.indexOf(':')
        return if (queryIndex >= 0 && (colonIndex < 0 || queryIndex < colonIndex)) {
            // sms:number?body=… variant: the message may itself contain ':',
            // so the query must be handled before any colon split.
            val message = queryParams(body.substring(queryIndex + 1))
                .firstOrNull { it.first == "body" }?.second ?: ""
            FormPrefill(
                QrType.SMS,
                smsNumber = body.substring(0, queryIndex).trim(),
                smsMessage = message.trim()
            )
        } else {
            val parts = body.split(":", limit = 2)
            FormPrefill(
                QrType.SMS,
                smsNumber = parts[0].trim(),
                smsMessage = parts.getOrElse(1) { "" }.trim()
            )
        }
    }

    private fun parseGeo(value: String): FormPrefill? {
        if (!value.startsWith("geo:", ignoreCase = true)) return null
        val coordinates = value.substring("geo:".length).substringBefore('?').split(',')
        if (coordinates.size < 2) return null
        val latitude = coordinates[0].trim()
        val longitude = coordinates[1].trim()
        if (latitude.toDoubleOrNull() == null || longitude.toDoubleOrNull() == null) return null
        return FormPrefill(QrType.GEO, geoLat = latitude, geoLng = longitude)
    }

    /** Properties the contact form can faithfully round-trip. */
    private val EDITABLE_VCARD_KEYS = setOf("BEGIN", "END", "VERSION", "FN", "N", "ORG", "TEL", "EMAIL")

    private fun parseVCard(value: String): FormPrefill? {
        if (!value.startsWith("BEGIN:VCARD", ignoreCase = true)) return null
        var name = ""
        var organization = ""
        var phone = ""
        var email = ""
        var phoneCount = 0
        var emailCount = 0
        // Unfold continuation lines (RFC 6350: CRLF followed by space or tab).
        val unfolded = value.replace("\r\n", "\n").replace("\n ", "").replace("\n\t", "")
        unfolded.lines().forEach { line ->
            val idx = line.indexOf(':')
            if (idx <= 0) return@forEach
            // Property name may carry parameters (TEL;TYPE=CELL) — keep the base name only.
            val key = line.substring(0, idx).substringBefore(';').trim().uppercase()
            val raw = line.substring(idx + 1).trim()
            when (key) {
                "FN" -> if (name.isEmpty()) name = unescapeVCard(raw)
                "ORG" -> if (organization.isEmpty()) organization = unescapeVCard(raw)
                "TEL" -> {
                    phoneCount++
                    if (phone.isEmpty()) phone = raw
                }
                "EMAIL" -> {
                    emailCount++
                    if (email.isEmpty()) email = raw
                }
                else -> {
                    // Editing would silently drop this property when the form
                    // regenerates the card — refuse instead of losing data.
                    if (key !in EDITABLE_VCARD_KEYS && raw.isNotEmpty()) return null
                }
            }
        }
        if (phoneCount > 1 || emailCount > 1) return null
        if (name.isEmpty() && phone.isEmpty() && email.isEmpty()) return null
        return FormPrefill(
            QrType.CONTACT,
            contactName = name,
            contactOrg = organization,
            contactPhone = phone,
            contactEmail = email
        )
    }

    /** Decodes percent-encoded query parameters; malformed values fall back to "". */
    private fun queryParams(query: String): List<Pair<String, String>> =
        query.split('&').mapNotNull { param ->
            val idx = param.indexOf('=')
            if (idx <= 0) return@mapNotNull null
            val key = param.substring(0, idx).lowercase()
            val decoded = runCatching {
                URLDecoder.decode(param.substring(idx + 1), "UTF-8")
            }.getOrDefault("")
            key to decoded
        }

    private fun stripPrefix(value: String, prefix: String): String =
        if (value.startsWith(prefix, ignoreCase = true)) value.substring(prefix.length) else value

    private fun unescapeVCard(value: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (val next = value[i + 1]) {
                    'n', 'N' -> out.append('\n')
                    else -> out.append(next)
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }
}
