package com.qrstudio.ui.scan

import androidx.lifecycle.ViewModel
import com.qrstudio.core.data.HistoryOrigin
import com.qrstudio.core.data.HistoryRepository
import com.qrstudio.core.qr.QrType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class Scanned(val content: String, val type: QrType)

data class ScanUiState(
    val scanned: Scanned? = null,
    val message: String? = null,
    /** Monotonic counter of accepted scans, so one-shot effects (haptics) fire once per scan. */
    val scanCount: Int = 0
)

class ScanViewModel : ViewModel() {

    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    // Avoids instantly re-opening the result for a code that stays in frame.
    // Volatile: written from the camera analyzer thread, read from main.
    @Volatile
    private var lastHandledContent: String? = null

    @Volatile
    private var lastHandledAt: Long = 0L

    fun onDecoded(text: String) {
        val now = System.currentTimeMillis()
        if (text == lastHandledContent && now - lastHandledAt < 1500L) return

        val type = QrType.detect(text)
        lastHandledContent = text
        lastHandledAt = now
        var accepted = false
        // The "already showing a result" guard lives inside the atomic update so
        // two analyzer frames can't both open a sheet / record an entry.
        _state.update {
            if (it.scanned != null) {
                accepted = false
                it
            } else {
                accepted = true
                it.copy(scanned = Scanned(text, type), scanCount = it.scanCount + 1)
            }
        }
        if (accepted) HistoryRepository.record(text, type, HistoryOrigin.SCANNED)
    }

    fun onGalleryResult(text: String?) {
        if (text == null) {
            _state.update { it.copy(message = "Aucun QR code détecté dans l'image.") }
        } else {
            onDecoded(text)
        }
    }

    fun dismissResult() {
        _state.value.scanned?.let {
            lastHandledContent = it.content
            lastHandledAt = System.currentTimeMillis()
        }
        _state.update { it.copy(scanned = null) }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }
}
