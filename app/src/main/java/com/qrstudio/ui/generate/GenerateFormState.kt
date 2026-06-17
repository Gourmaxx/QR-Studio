package com.qrstudio.ui.generate

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import com.qrstudio.core.qr.QrParser

/**
 * Holds every editable field of the "create" form in one place, so adding a
 * field or a type touches this class only (instead of mirroring it across the
 * screen state, [buildPayload] and the prefill copy). Survives process death via
 * [Saver]. The selected type, ink colour and logo toggle stay separate screen
 * state — they are not payload fields.
 */
@Stable
class GenerateFormState(
    url: String = "",
    text: String = "",
    wifiSsid: String = "",
    wifiPassword: String = "",
    wifiEncIndex: Int = 0,
    wifiHidden: Boolean = false,
    contactName: String = "",
    contactOrg: String = "",
    contactPhone: String = "",
    contactEmail: String = "",
    emailTo: String = "",
    emailSubject: String = "",
    emailBody: String = "",
    smsNumber: String = "",
    smsMessage: String = "",
    phoneNumber: String = "",
    geoLat: String = "",
    geoLng: String = ""
) {
    var url by mutableStateOf(url)
    var text by mutableStateOf(text)
    var wifiSsid by mutableStateOf(wifiSsid)
    var wifiPassword by mutableStateOf(wifiPassword)
    var wifiEncIndex by mutableStateOf(wifiEncIndex)
    var wifiHidden by mutableStateOf(wifiHidden)
    var contactName by mutableStateOf(contactName)
    var contactOrg by mutableStateOf(contactOrg)
    var contactPhone by mutableStateOf(contactPhone)
    var contactEmail by mutableStateOf(contactEmail)
    var emailTo by mutableStateOf(emailTo)
    var emailSubject by mutableStateOf(emailSubject)
    var emailBody by mutableStateOf(emailBody)
    var smsNumber by mutableStateOf(smsNumber)
    var smsMessage by mutableStateOf(smsMessage)
    var phoneNumber by mutableStateOf(phoneNumber)
    var geoLat by mutableStateOf(geoLat)
    var geoLng by mutableStateOf(geoLng)

    /** Overwrites every field from a payload recovered for re-editing. */
    fun applyPrefill(prefill: QrParser.FormPrefill) {
        url = prefill.urlValue
        text = prefill.textValue
        wifiSsid = prefill.wifiSsid
        wifiPassword = prefill.wifiPassword
        wifiEncIndex = prefill.wifiEncIndex
        wifiHidden = prefill.wifiHidden
        contactName = prefill.contactName
        contactOrg = prefill.contactOrg
        contactPhone = prefill.contactPhone
        contactEmail = prefill.contactEmail
        emailTo = prefill.emailTo
        emailSubject = prefill.emailSubject
        emailBody = prefill.emailBody
        smsNumber = prefill.smsNumber
        smsMessage = prefill.smsMessage
        phoneNumber = prefill.phoneNumber
        geoLat = prefill.geoLat
        geoLng = prefill.geoLng
    }

    companion object {
        // Order MUST match between save and restore — keep the two lists aligned.
        val Saver: Saver<GenerateFormState, Any> = listSaver(
            save = {
                listOf(
                    it.url, it.text,
                    it.wifiSsid, it.wifiPassword, it.wifiEncIndex, it.wifiHidden,
                    it.contactName, it.contactOrg, it.contactPhone, it.contactEmail,
                    it.emailTo, it.emailSubject, it.emailBody,
                    it.smsNumber, it.smsMessage,
                    it.phoneNumber,
                    it.geoLat, it.geoLng
                )
            },
            restore = {
                GenerateFormState(
                    url = it[0] as String,
                    text = it[1] as String,
                    wifiSsid = it[2] as String,
                    wifiPassword = it[3] as String,
                    wifiEncIndex = it[4] as Int,
                    wifiHidden = it[5] as Boolean,
                    contactName = it[6] as String,
                    contactOrg = it[7] as String,
                    contactPhone = it[8] as String,
                    contactEmail = it[9] as String,
                    emailTo = it[10] as String,
                    emailSubject = it[11] as String,
                    emailBody = it[12] as String,
                    smsNumber = it[13] as String,
                    smsMessage = it[14] as String,
                    phoneNumber = it[15] as String,
                    geoLat = it[16] as String,
                    geoLng = it[17] as String
                )
            }
        )
    }
}
