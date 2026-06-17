package com.qrstudio.ui.generate

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qrstudio.core.qr.QrParser
import com.qrstudio.core.qr.QrType
import com.qrstudio.core.util.LogoRenderer
import com.qrstudio.ui.EditRequest
import com.qrstudio.ui.components.QrActionButtons
import com.qrstudio.ui.components.QrImage
import com.qrstudio.ui.theme.QrInkBlack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GenerateScreen(
    editRequest: EditRequest? = null,
    onEditConsumed: () -> Unit = {},
    viewModel: GenerateViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedType by rememberSaveable { mutableStateOf(QrType.URL) }
    var formError by remember { mutableStateOf<String?>(null) }
    var embedLogo by rememberSaveable { mutableStateOf(true) }
    var inkArgb by rememberSaveable { mutableStateOf(QrInkBlack.toArgb()) }
    val logoBitmap = remember(context) { LogoRenderer.appMark(context, 240) }

    val form = rememberSaveable(saver = GenerateFormState.Saver) { GenerateFormState() }

    var filePayload by remember { mutableStateOf<String?>(null) }
    var fileInfo by remember { mutableStateOf<String?>(null) }
    var fileProcessing by remember { mutableStateOf(false) }

    fun resetFile() {
        filePayload = null
        fileInfo = null
        formError = null
    }

    fun processPicked(uri: Uri, isImage: Boolean) {
        fileProcessing = true
        formError = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) { buildFilePayload(context, uri, isImage) }
            fileProcessing = false
            when (outcome) {
                is FileOutcome.Ready -> {
                    filePayload = outcome.payload
                    fileInfo = outcome.info
                }
                is FileOutcome.Error -> {
                    filePayload = null
                    fileInfo = null
                    formError = outcome.message
                }
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { processPicked(it, isImage = true) } }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { processPicked(it, isImage = false) } }

    // A history/scan entry asked to be reopened here: prefill the form once.
    LaunchedEffect(editRequest) {
        val request = editRequest ?: return@LaunchedEffect
        val prefill = QrParser.toForm(request.content, request.type)
        if (prefill != null) {
            selectedType = prefill.type
            form.applyPrefill(prefill)
            inkArgb = request.foregroundArgb ?: QrInkBlack.toArgb()
            resetFile()
            viewModel.clearResult()
        }
        onEditConsumed()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Safety net: the screen is designed to fit without scrolling, but
            // small screens with the keyboard open can still overflow.
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (state.bitmap == null) {
            // ---- Editing view ----
            Text(text = "Créer un QR code", style = MaterialTheme.typography.headlineMedium)

            CategoryGrid(selected = selectedType, onSelect = {
                selectedType = it
                formError = null
            })

            TypeForm(
                type = selectedType,
                form = form,
                fileInfo = fileInfo,
                fileProcessing = fileProcessing,
                onPickImage = {
                    resetFile()
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onPickFile = {
                    resetFile()
                    filePicker.launch("*/*")
                }
            )

            ToggleRow("Logo au centre du QR", embedLogo) { embedLogo = it }

            if (selectedType != QrType.FILE) {
                InkSelector(selectedArgb = inkArgb, onSelect = { inkArgb = it })
            }

            formError?.let { MessageCard(it, isError = true) }
            state.error?.let { MessageCard(it, isError = true) }

            Button(
                onClick = {
                    val payload = buildPayload(selectedType, form, filePayload)
                    if (payload == null) {
                        formError = "Renseignez les champs requis pour ce type."
                    } else {
                        formError = null
                        viewModel.generate(
                            type = selectedType,
                            payload = payload,
                            label = buildLabel(selectedType, form, fileInfo),
                            logo = if (embedLogo) logoBitmap else null,
                            foregroundArgb = inkArgb
                        )
                    }
                },
                enabled = !state.isLoading && !fileProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Outlined.QrCode2, contentDescription = null)
                    Text("Générer le QR code", modifier = Modifier.padding(start = 8.dp))
                }
            }
        } else {
            // ---- Result view ----
            ResultView(
                content = state.payload,
                type = state.type,
                note = state.note,
                bitmap = state.bitmap!!,
                onBack = viewModel::clearResult
            )
        }
    }
}

@Composable
private fun ColumnScope.ResultView(
    content: String,
    type: QrType,
    note: String?,
    bitmap: android.graphics.Bitmap,
    onBack: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
            Text("Modifier", modifier = Modifier.padding(start = 6.dp))
        }
        Text(
            text = type.frenchLabel,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
    QrImage(
        bitmap = bitmap,
        modifier = Modifier
            .fillMaxWidth(0.66f)
            .align(Alignment.CenterHorizontally)
    )
    note?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
    QrActionButtons(content = content, type = type, bitmap = bitmap)
}

@Composable
private fun MessageCard(message: String, isError: Boolean) {
    Surface(
        color = if (isError) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(14.dp)
        )
    }
}
