package com.cyberalarm.ui.components

import android.Manifest
import android.nfc.NfcAdapter
import androidx.activity.compose.LocalActivity
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cyberalarm.ui.permissions.Permissions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

fun hasCamera(context: android.content.Context) = Permissions.granted(context, Manifest.permission.CAMERA)

fun hasNfc(context: android.content.Context) = NfcAdapter.getDefaultAdapter(context) != null

fun nfcEnabled(context: android.content.Context) = NfcAdapter.getDefaultAdapter(context)?.isEnabled == true

/**
 * Live back-camera preview that reports every QR code / barcode it sees (any format: a shampoo
 * bottle works as well as a printed QR). Needs the CAMERA permission; the caller checks it.
 */
@OptIn(ExperimentalGetImage::class)
@Composable
fun CodeScanner(modifier: Modifier = Modifier, onCode: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latest by rememberUpdatedState(onCode)
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val scanner = BarcodeScanning.getClient()
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false
        future.addListener({
            if (disposed) return@addListener
            val p = runCatching { future.get() }.getOrNull() ?: return@addListener
            provider = p
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { proxy ->
                val media = proxy.image
                if (media == null) {
                    proxy.close()
                    return@setAnalyzer
                }
                scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                    // Listeners run on the main thread.
                    .addOnSuccessListener { codes -> codes.firstNotNullOfOrNull { it.rawValue }?.let { latest(it) } }
                    .addOnCompleteListener { proxy.close() }
            }
            runCatching {
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            provider?.unbindAll()
            scanner.close()
            executor.shutdown()
        }
    }
    AndroidView({ previewView }, modifier)
}

/**
 * Reads NFC tag ids while this composable is shown and the activity is resumed (reader mode only
 * works in the foreground). Reports the id as hex. No-op on phones without NFC.
 */
@Composable
fun NfcReaderEffect(onTag: (String) -> Unit) {
    val activity = LocalActivity.current ?: return
    val adapter = remember(activity) { NfcAdapter.getDefaultAdapter(activity) } ?: return
    val lifecycleOwner = LocalLifecycleOwner.current
    val latest by rememberUpdatedState(onTag)
    DisposableEffect(activity, lifecycleOwner) {
        val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
        fun enable() = runCatching {
            adapter.enableReaderMode(activity, { tag ->
                val id = tag.id.joinToString("") { "%02x".format(it) }
                activity.runOnUiThread { latest(id) }
            }, flags, null)
        }
        fun disable() = runCatching { adapter.disableReaderMode(activity) }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> enable()
                Lifecycle.Event.ON_PAUSE -> disable()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) enable()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            disable()
        }
    }
}
