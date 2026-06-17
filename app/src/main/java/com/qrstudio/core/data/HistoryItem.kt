package com.qrstudio.core.data

import com.qrstudio.core.qr.QrType

enum class HistoryOrigin(val frenchLabel: String) {
    GENERATED("Généré"),
    SCANNED("Scanné")
}

/**
 * One persisted entry: a payload that was either generated or scanned.
 * The bitmap is never stored — it is re-rendered from [content] on demand.
 */
data class HistoryItem(
    val id: String,
    val content: String,
    val type: QrType,
    val origin: HistoryOrigin,
    val timestamp: Long,
    val label: String?,
    /** Pinned entries sort first and feed the home-screen widget. */
    val pinned: Boolean = false,
    /** When the entry was pinned — the widget shows the most recently pinned one. */
    val pinnedAt: Long? = null,
    /** Custom QR ink colour (ARGB); null means the default black. */
    val foregroundArgb: Int? = null
)
