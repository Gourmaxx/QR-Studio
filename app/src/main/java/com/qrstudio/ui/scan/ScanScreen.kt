package com.qrstudio.ui.scan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qrstudio.core.qr.QrDecoder
import com.qrstudio.core.qr.QrParser
import com.qrstudio.core.util.sampleSizeFor
import com.qrstudio.ui.EditRequest
import com.qrstudio.ui.components.QrResultSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ScanScreen(
    onEditRequest: (EditRequest) -> Unit = {},
    viewModel: ScanViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var torchOn by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val galleryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val decoded = withContext(Dispatchers.IO) { decodeQrFromUri(context, uri) }
                viewModel.onGalleryResult(decoded)
            }
        }
    }

    state.message?.let { message ->
        LaunchedEffect(message) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    // Short haptic confirmation, once per accepted scan. Tracking the consumed
    // scanCount (saveable) avoids re-vibrating when the composition is recreated
    // while the result sheet is still open (rotation, tab switch).
    var vibratedScanCount by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(state.scanCount) {
        if (vibratedScanCount > state.scanCount) {
            // Process death: the view model restarted its counter — resync so
            // the next scans are not silently swallowed.
            vibratedScanCount = state.scanCount
        }
        if (state.scanCount > vibratedScanCount) {
            vibratedScanCount = state.scanCount
            // Some devices have no vibrator; fail silently in that case.
            runCatching {
                vibratorOf(context)?.vibrate(
                    VibrationEffect.createOneShot(80L, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasPermission) {
            CameraPreview(
                torchOn = torchOn,
                onQrDecoded = viewModel::onDecoded,
                modifier = Modifier.fillMaxSize()
            )
            ScannerViewfinder()
            ScanControls(
                torchOn = torchOn,
                onToggleTorch = { torchOn = !torchOn },
                onImportImage = {
                    galleryPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp)
            )
        } else {
            PermissionPrompt(
                onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onImportImage = {
                    galleryPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }

    state.scanned?.let { scanned ->
        val prefill = remember(scanned) { QrParser.toForm(scanned.content, scanned.type) }
        QrResultSheet(
            content = scanned.content,
            type = scanned.type,
            subtitle = "Scanné",
            onDismiss = viewModel::dismissResult,
            onEdit = if (prefill != null) {
                {
                    viewModel.dismissResult()
                    onEditRequest(EditRequest(scanned.content, scanned.type))
                }
            } else {
                null
            }
        )
    }
}

@Composable
private fun ScannerViewfinder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(260.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp))
        )
        Text(
            text = "Visez un QR code ou un code-barres",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun ScanControls(
    torchOn: Boolean,
    onToggleTorch: () -> Unit,
    onImportImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalIconButton(
            onClick = onToggleTorch,
            modifier = Modifier.size(56.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors()
        ) {
            Icon(
                imageVector = if (torchOn) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff,
                contentDescription = "Lampe"
            )
        }
        FilledTonalIconButton(
            onClick = onImportImage,
            modifier = Modifier.size(56.dp)
        ) {
            Icon(Icons.Outlined.PhotoLibrary, contentDescription = "Importer une image")
        }
    }
}

@Composable
private fun PermissionPrompt(
    onRequest: () -> Unit,
    onImportImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            Icons.Outlined.QrCodeScanner,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Text(
            text = "Accès à la caméra",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "Autorisez la caméra pour scanner des QR codes en direct, " +
                "ou importez une image existante.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) {
            Text("Autoriser la caméra")
        }
        androidx.compose.material3.OutlinedButton(
            onClick = onImportImage,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
            Text("Importer une image", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** Resolves the device vibrator across API levels (VibratorManager from API 31). */
private fun vibratorOf(context: Context): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
            ?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

private fun decodeQrFromUri(context: Context, uri: Uri): String? {
    val bitmap = decodeSampledBitmap(context, uri, maxDimension = 2048) ?: return null
    return QrDecoder.decodeBitmap(bitmap)
}

/** Decodes a downsampled bitmap to keep memory bounded while staying readable. */
private fun decodeSampledBitmap(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxDimension)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    } catch (e: Exception) {
        null
    }
}
