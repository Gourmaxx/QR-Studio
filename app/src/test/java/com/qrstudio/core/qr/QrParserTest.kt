package com.qrstudio.core.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrParserTest {

    // ---- Wi-Fi ----

    @Test
    fun `wifi round trip with escaped characters and hidden network`() {
        val payload = QrPayload.wifi(
            ssid = "Mon;Réseau:chez,moi",
            password = "p@ss;word\\1",
            encryption = QrPayload.WifiEncryption.WPA,
            hidden = true
        )
        val info = QrParser.parseWifi(payload)
        assertNotNull(info)
        assertEquals("Mon;Réseau:chez,moi", info!!.ssid)
        assertEquals("p@ss;word\\1", info.password)
        assertEquals(QrPayload.WifiEncryption.WPA, info.encryption)
        assertTrue(info.hidden)
    }

    @Test
    fun `wifi open network has no password and is not hidden`() {
        val payload = QrPayload.wifi("Cafe", "", QrPayload.WifiEncryption.NONE, hidden = false)
        val info = QrParser.parseWifi(payload)
        assertNotNull(info)
        assertEquals("Cafe", info!!.ssid)
        assertEquals("", info.password)
        assertEquals(QrPayload.WifiEncryption.NONE, info.encryption)
        assertFalse(info.hidden)
    }

    @Test
    fun `wifi form prefill maps encryption to its form index`() {
        val payload = QrPayload.wifi("Home", "secret", QrPayload.WifiEncryption.WEP, hidden = false)
        val form = QrParser.toForm(payload, QrType.WIFI)
        assertNotNull(form)
        assertEquals("Home", form!!.wifiSsid)
        assertEquals("secret", form.wifiPassword)
        assertEquals(QrPayload.WifiEncryption.WEP.ordinal, form.wifiEncIndex)
    }

    @Test
    fun `non wifi payload returns null`() {
        assertNull(QrParser.parseWifi("tel:+33612345678"))
    }

    // ---- mailto ----

    @Test
    fun `mailto round trip with accented subject and body`() {
        val payload = QrPayload.email(
            to = "ami@example.com",
            subject = "Salut l'équipe",
            body = "À demain & bonne journée"
        )
        val form = QrParser.toForm(payload, QrType.EMAIL)
        assertNotNull(form)
        assertEquals("ami@example.com", form!!.emailTo)
        assertEquals("Salut l'équipe", form.emailSubject)
        assertEquals("À demain & bonne journée", form.emailBody)
    }

    @Test
    fun `mailto without query keeps subject and body empty`() {
        val form = QrParser.toForm("mailto:ami@example.com", QrType.EMAIL)
        assertNotNull(form)
        assertEquals("ami@example.com", form!!.emailTo)
        assertEquals("", form.emailSubject)
        assertEquals("", form.emailBody)
    }

    // ---- SMS ----

    @Test
    fun `smsto round trip`() {
        val payload = QrPayload.sms("+33612345678", "Salut !")
        val form = QrParser.toForm(payload, QrType.SMS)
        assertNotNull(form)
        assertEquals("+33612345678", form!!.smsNumber)
        assertEquals("Salut !", form.smsMessage)
    }

    @Test
    fun `sms body query variant is recognised`() {
        val form = QrParser.toForm("sms:+33612345678?body=Coucou%20toi", QrType.SMS)
        assertNotNull(form)
        assertEquals("+33612345678", form!!.smsNumber)
        assertEquals("Coucou toi", form.smsMessage)
    }

    @Test
    fun `sms body query containing a colon is kept whole`() {
        val form = QrParser.toForm("sms:+33612345678?body=RDV%3A%2018h", QrType.SMS)
        assertNotNull(form)
        assertEquals("+33612345678", form!!.smsNumber)
        assertEquals("RDV: 18h", form.smsMessage)
    }

    @Test
    fun `smsto without message keeps it empty`() {
        val form = QrParser.toForm("SMSTO:0612345678:", QrType.SMS)
        assertNotNull(form)
        assertEquals("0612345678", form!!.smsNumber)
        assertEquals("", form.smsMessage)
    }

    // ---- Phone ----

    @Test
    fun `tel prefix is stripped`() {
        val form = QrParser.toForm(QrPayload.phone("+33612345678"), QrType.PHONE)
        assertNotNull(form)
        assertEquals("+33612345678", form!!.phoneNumber)
    }

    // ---- Geo ----

    @Test
    fun `geo round trip`() {
        val form = QrParser.toForm(QrPayload.geo("48.8584", "2.2945"), QrType.GEO)
        assertNotNull(form)
        assertEquals("48.8584", form!!.geoLat)
        assertEquals("2.2945", form.geoLng)
    }

    @Test
    fun `geo with query keeps only coordinates`() {
        val form = QrParser.toForm("geo:48.85,2.29?q=Tour+Eiffel", QrType.GEO)
        assertNotNull(form)
        assertEquals("48.85", form!!.geoLat)
        assertEquals("2.29", form.geoLng)
    }

    @Test
    fun `geo with non numeric coordinates is rejected`() {
        assertNull(QrParser.toForm("geo:abc,def", QrType.GEO))
    }

    // ---- vCard ----

    @Test
    fun `vcard round trip with escaped name`() {
        val payload = QrPayload.vCard(
            fullName = "Dupont, Jean",
            organization = "ACME; Labs",
            title = "",
            phone = "+33612345678",
            email = "jean@acme.fr",
            url = "",
            address = "",
            note = ""
        )
        val form = QrParser.toForm(payload, QrType.CONTACT)
        assertNotNull(form)
        assertEquals("Dupont, Jean", form!!.contactName)
        assertEquals("ACME; Labs", form.contactOrg)
        assertEquals("+33612345678", form.contactPhone)
        assertEquals("jean@acme.fr", form.contactEmail)
    }

    @Test
    fun `mecard contact is not editable`() {
        assertNull(QrParser.toForm("MECARD:N:Doe,John;;", QrType.CONTACT))
    }

    @Test
    fun `vcard with properties the form cannot keep is not editable`() {
        val payload = "BEGIN:VCARD\nVERSION:3.0\nFN:Jean Dupont\n" +
            "ADR;TYPE=HOME:;;1 rue de la Paix;;;;\nEND:VCARD"
        assertNull(QrParser.toForm(payload, QrType.CONTACT))
    }

    @Test
    fun `vcard with two phone numbers is not editable`() {
        val payload = "BEGIN:VCARD\nVERSION:3.0\nFN:Jean Dupont\n" +
            "TEL:+33611111111\nTEL:+33622222222\nEND:VCARD"
        assertNull(QrParser.toForm(payload, QrType.CONTACT))
    }

    @Test
    fun `folded vcard lines are unfolded before parsing`() {
        val payload = "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Jean\r\n  Dupont\r\nEND:VCARD"
        val form = QrParser.toForm(payload, QrType.CONTACT)
        assertNotNull(form)
        assertEquals("Jean Dupont", form!!.contactName)
    }

    @Test
    fun `percent encoded mailto address is decoded`() {
        val form = QrParser.toForm("mailto:jean%2Bspam@example.com", QrType.EMAIL)
        assertNotNull(form)
        assertEquals("jean+spam@example.com", form!!.emailTo)
    }

    // ---- Pass-through types ----

    @Test
    fun `url and text pass through unchanged`() {
        assertEquals(
            "https://example.com",
            QrParser.toForm("https://example.com", QrType.URL)!!.urlValue
        )
        assertEquals(
            "Du texte\nsur deux lignes",
            QrParser.toForm("Du texte\nsur deux lignes", QrType.TEXT)!!.textValue
        )
    }

    @Test
    fun `file payloads are not editable`() {
        assertNull(QrParser.toForm("qrsf:1:0:text/plain:aGVsbG8=", QrType.FILE))
    }
}
