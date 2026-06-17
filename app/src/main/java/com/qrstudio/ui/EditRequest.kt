package com.qrstudio.ui

import com.qrstudio.core.qr.QrType

/**
 * Request to reopen an existing payload in the generator form, prefilled.
 * Hoisted in [QrApp] so the history and scan screens can hand a payload to
 * the generate tab without any global bus.
 */
data class EditRequest(
    val content: String,
    val type: QrType,
    val foregroundArgb: Int? = null
)
