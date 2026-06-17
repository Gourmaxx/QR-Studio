package com.qrstudio.ui.scan

import android.annotation.SuppressLint
import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.qrstudio.core.qr.QrDecoder
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine

/** Analyses the camera's luminance plane frame by frame and reports any decoded QR. */
private class QrAnalyzer(private val onDecoded: (String) -> Unit) : ImageAnalysis.Analyzer {
    override fun analyze(image: ImageProxy) {
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val data = ByteArray(buffer.remaining())
            buffer.get(data)
            val rowStride = plane.rowStride
            if (rowStride <= 0) return
            val dataHeight = data.size / rowStride
            val result = QrDecoder.decodeYuv(
                yPlane = data,
                rowStride = rowStride,
                dataHeight = dataHeight,
                cropWidth = image.width,
                cropHeight = image.height
            )
            if (result != null) onDecoded(result)
        } catch (e: Exception) {
            // Drop the frame; the next one will be analysed.
        } finally {
            image.close()
        }
    }
}

@Composable
fun CameraPreview(
    torchOn: Boolean,
    onQrDecoded: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val providerHolder = remember { arrayOfNulls<ProcessCameraProvider>(1) }

    DisposableEffect(Unit) {
        onDispose {
            // Release the camera binding before shutting the analyzer executor down,
            // otherwise an in-flight frame can hit a stopped executor.
            runCatching { providerHolder[0]?.unbindAll() }
            analysisExecutor.shutdown()
        }
    }

    LaunchedEffect(Unit) {
        // Cancellation must propagate (leaving the tab while the provider is
        // still resolving): binding afterwards would leak the camera past the
        // onDispose unbind. Other init failures simply leave the preview empty.
        val provider = try {
            context.awaitCameraProvider()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@LaunchedEffect
        }
        ensureActive()
        providerHolder[0] = provider
        val preview = Preview.Builder().build().apply {
            setSurfaceProvider(previewView.surfaceProvider)
        }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .apply { setAnalyzer(analysisExecutor, QrAnalyzer(onQrDecoded)) }

        provider.unbindAll()
        // The manifest marks cameras as optional: devices without a back camera
        // must not crash, they just keep the gallery-import path.
        camera = runCatching {
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis
            )
        }.getOrNull()
        // Gestures need a bound camera, so they are attached only after binding succeeds.
        camera?.let { attachTouchGestures(previewView, it) }
    }

    // Keyed on the camera too: a torch toggle made while binding was still in
    // flight is re-applied as soon as the camera becomes available.
    LaunchedEffect(camera, torchOn) {
        runCatching { camera?.cameraControl?.enableTorch(torchOn) }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

/** Wires pinch-to-zoom and tap-to-focus onto the preview once the camera is bound. */
@SuppressLint("ClickableViewAccessibility")
private fun attachTouchGestures(previewView: PreviewView, camera: Camera) {
    val scaleDetector = ScaleGestureDetector(
        previewView.context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val zoomState = camera.cameraInfo.zoomState.value ?: return true
                val newRatio = (zoomState.zoomRatio * detector.scaleFactor)
                    .coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
                runCatching { camera.cameraControl.setZoomRatio(newRatio) }
                return true
            }
        }
    )
    val tapDetector = GestureDetector(
        previewView.context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val point = previewView.meteringPointFactory.createPoint(e.x, e.y)
                val action = FocusMeteringAction.Builder(
                    point,
                    FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                ).build()
                // Some devices reject metering actions (e.g. fixed-focus cameras).
                runCatching { camera.cameraControl.startFocusAndMetering(action) }
                return true
            }
        }
    )
    previewView.setOnTouchListener { view, event ->
        scaleDetector.onTouchEvent(event)
        tapDetector.onTouchEvent(event)
        // Keep accessibility services notified of click-like interactions.
        if (event.action == MotionEvent.ACTION_UP) view.performClick()
        true
    }
}

private suspend fun Context.awaitCameraProvider(): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            {
                // get() itself throws when CameraX fails to initialise; surface
                // that to the caller instead of crashing the main executor.
                runCatching { future.get() }
                    .onSuccess { continuation.resume(it) }
                    .onFailure { continuation.resumeWithException(it) }
            },
            ContextCompat.getMainExecutor(this)
        )
    }
