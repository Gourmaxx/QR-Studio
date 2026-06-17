package com.qrstudio.core.qr

import org.junit.Assert.assertEquals
import org.junit.Test

class QrPayloadTest {

    @Test
    fun normalisesUrl() {
        assertEquals("https://example.com", QrPayload.url("example.com"))
        assertEquals("http://example.com", QrPayload.url("http://example.com"))
        assertEquals("https://example.com", QrPayload.url("https://example.com"))
    }

    @Test
    fun escapesWifiSpecialCharacters() {
        val payload = QrPayload.wifi(
            ssid = "My;Net",
            password = "p:w",
            encryption = QrPayload.WifiEncryption.WPA,
            hidden = false
        )
        assertEquals("WIFI:T:WPA;S:My\\;Net;P:p\\:w;;", payload)
    }

    @Test
    fun openWifiOmitsPassword() {
        val payload = QrPayload.wifi(
            ssid = "Open",
            password = "ignored",
            encryption = QrPayload.WifiEncryption.NONE,
            hidden = false
        )
        assertEquals("WIFI:T:nopass;S:Open;;", payload)
    }

    @Test
    fun buildsSimpleTypes() {
        assertEquals("tel:123", QrPayload.phone(" 123 "))
        assertEquals("SMSTO:123:Hello", QrPayload.sms("123", "Hello"))
        assertEquals("geo:48.8,2.3", QrPayload.geo("48.8", "2.3"))
    }

    @Test
    fun buildsEmailWithEncodedQuery() {
        assertEquals(
            "mailto:a@b.com?subject=Hi%20there&body=Yo",
            QrPayload.email("a@b.com", "Hi there", "Yo")
        )
    }
}
