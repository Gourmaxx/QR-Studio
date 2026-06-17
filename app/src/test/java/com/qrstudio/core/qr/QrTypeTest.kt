package com.qrstudio.core.qr

import org.junit.Assert.assertEquals
import org.junit.Test

class QrTypeTest {

    @Test
    fun detectsUrl() {
        assertEquals(QrType.URL, QrType.detect("https://example.com"))
        assertEquals(QrType.URL, QrType.detect("HTTP://Example.com"))
        assertEquals(QrType.URL, QrType.detect("www.example.com"))
    }

    @Test
    fun detectsStructuredTypes() {
        assertEquals(QrType.WIFI, QrType.detect("WIFI:T:WPA;S:net;P:pw;;"))
        assertEquals(QrType.CONTACT, QrType.detect("BEGIN:VCARD\nVERSION:3.0\nEND:VCARD"))
        assertEquals(QrType.CONTACT, QrType.detect("MECARD:N:Doe;;"))
        assertEquals(QrType.EMAIL, QrType.detect("mailto:a@b.com"))
        assertEquals(QrType.SMS, QrType.detect("SMSTO:0600000000:Salut"))
        assertEquals(QrType.PHONE, QrType.detect("tel:0600000000"))
        assertEquals(QrType.GEO, QrType.detect("geo:48.8566,2.3522"))
        assertEquals(QrType.FILE, QrType.detect("data:image/png;base64,AAAA"))
    }

    @Test
    fun fallsBackToText() {
        assertEquals(QrType.TEXT, QrType.detect("Just some plain text"))
        assertEquals(QrType.TEXT, QrType.detect("file.txt"))
    }
}
