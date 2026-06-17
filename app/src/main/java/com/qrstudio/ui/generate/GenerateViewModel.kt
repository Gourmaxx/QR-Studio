package com.qrstudio.ui.generate

import android.graphics.Bitmap
import android.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qrstudio.core.data.HistoryOrigin
import com.qrstudio.core.data.HistoryRepository
import com.qrstudio.core.qr.QrEncoder
import com.qrstudio.core.qr.QrType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GenerateUiState(
    val isLoading: Boolean = false,
    val bitmap: Bitmap? = null,
    val payload: String = "",
    val type: QrType = QrType.URL,
    val error: String? = null,
    val note: String? = null
)

class GenerateViewModel : ViewModel() {

    private val _state = MutableStateFlow(GenerateUiState())
    val state: StateFlow<GenerateUiState> = _state.asStateFlow()

    /**
     * Encodes [payload] off the main thread, then records it as a generated entry.
     * When [logo] is set it is embedded at the centre; if the payload is too dense
     * to stay scannable with a logo, it falls back to a logo-less QR and notes it.
     */
    fun generate(
        type: QrType,
        payload: String,
        label: String?,
        logo: Bitmap? = null,
        foregroundArgb: Int = Color.BLACK
    ) {
        if (payload.isBlank()) {
            _state.update { it.copy(error = "Renseignez un contenu à encoder.", bitmap = null) }
            return
        }
        _state.update { it.copy(isLoading = true, error = null, note = null, type = type) }
        viewModelScope.launch {
            var note: String? = null
            val style = QrEncoder.styleFor(type, foregroundArgb, logo)
            var result = withContext(Dispatchers.Default) { QrEncoder.encode(payload, style) }
            if (result is QrEncoder.Result.Failure && logo != null && type != QrType.FILE) {
                note = "Logo retiré : contenu trop dense pour rester lisible."
                result = withContext(Dispatchers.Default) {
                    QrEncoder.encode(payload, QrEncoder.styleFor(type, foregroundArgb))
                }
            }
            when (val outcome = result) {
                is QrEncoder.Result.Success -> {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            bitmap = outcome.bitmap,
                            payload = payload,
                            type = type,
                            error = null,
                            note = note
                        )
                    }
                    HistoryRepository.record(
                        content = payload,
                        type = type,
                        origin = HistoryOrigin.GENERATED,
                        label = label,
                        foregroundArgb = foregroundArgb.takeIf { it != Color.BLACK && type != QrType.FILE }
                    )
                }
                is QrEncoder.Result.Failure -> _state.update {
                    it.copy(isLoading = false, bitmap = null, error = outcome.reason, note = null)
                }
            }
        }
    }

    fun clearResult() {
        _state.update { it.copy(bitmap = null, payload = "", error = null, note = null) }
    }
}
