package com.qrstudio.core.qr

/**
 * Categories of QR payloads. Used both to drive the generator forms and to
 * classify a freshly scanned string so the reader can offer contextual actions.
 */
enum class QrType(val frenchLabel: String) {
    URL("Lien"),
    TEXT("Texte"),
    WIFI("Wi-Fi"),
    CONTACT("Contact"),
    EMAIL("E-mail"),
    SMS("SMS"),
    PHONE("Téléphone"),
    GEO("Localisation"),
    FILE("Fichier");

    companion object {
        /** Best-effort classification of a decoded payload. */
        fun detect(raw: String): QrType {
            val value = raw.trim()
            val lower = value.lowercase()
            return when {
                lower.startsWith("http://") || lower.startsWith("https://") -> URL
                lower.startsWith("www.") -> URL
                lower.startsWith("wifi:") -> WIFI
                lower.startsWith("begin:vcard") || lower.startsWith("mecard:") -> CONTACT
                lower.startsWith("mailto:") || lower.startsWith("matmsg:") -> EMAIL
                lower.startsWith("smsto:") || lower.startsWith("sms:") -> SMS
                lower.startsWith("tel:") -> PHONE
                lower.startsWith("geo:") -> GEO
                lower.startsWith("data:") -> FILE
                lower.startsWith("qrsf:") -> FILE
                else -> TEXT
            }
        }
    }
}
