package az.sontarix.market.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import az.sontarix.market.ui.components.EmptyState
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** CameraX + ML Kit (cihazda, internetsiz) barkod skaneri. */
@Composable
fun ScannerScreen(onResult: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted; denied = !granted
    }
    var manual by remember { mutableStateOf(false) }
    var torch by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val delivered = remember { AtomicBoolean(false) }

    fun deliver(code: String) {
        if (!delivered.compareAndSet(false, true)) return
        runCatching {
            val v = context.getSystemService(Vibrator::class.java)
            v?.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
        }
        onResult(code)
    }

    androidx.compose.runtime.LaunchedEffect(Unit) { if (!hasPermission) launcher.launch(Manifest.permission.CAMERA) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            CameraPreview(onCamera = { camera = it }, onCode = { deliver(it) })
            ScanOverlay()
        } else if (denied) {
            Box(Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                EmptyState(Icons.Filled.NoPhotography, "Kamera icazəsi yoxdur", "Barkodu skan etmək üçün kamera icazəsi verin və ya barkodu əl ilə daxil edin.") {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("İcazə ver") }
                        TextButton(onClick = { manual = true }) { Text("Əl ilə daxil et") }
                    }
                }
            }
        }
        Row(Modifier.statusBarsPadding().padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FilledIconButton(onClick = onBack, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.45f))) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            if (hasPermission && camera?.cameraInfo?.hasFlashUnit() == true) {
                FilledIconButton(onClick = {
                    torch = !torch; camera?.cameraControl?.enableTorch(torch)
                }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.45f))) {
                    Icon(if (torch) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff, "Fənər", tint = Color.White)
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (hasPermission) Text("Barkodu çərçivənin içinə yerləşdirin", color = Color.White)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { manual = true }, modifier = Modifier.height(52.dp)) {
                Icon(Icons.Filled.Keyboard, null); Spacer(Modifier.width(8.dp)); Text("Barkodu əl ilə daxil et")
            }
        }
    }

    if (manual) {
        var code by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { manual = false },
            title = { Text("Barkod") },
            text = {
                OutlinedTextField(value = code, onValueChange = { code = it.filter { c -> !c.isWhitespace() }.take(64) },
                    singleLine = true, label = { Text("Barkod nömrəsi") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii))
            },
            confirmButton = { TextButton(enabled = code.isNotBlank(), onClick = { manual = false; deliver(code) }) { Text("Təsdiqlə") } },
            dismissButton = { TextButton(onClick = { manual = false }) { Text("Ləğv et") } }
        )
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraPreview(onCamera: (Camera) -> Unit, onCode: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(
                Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128, Barcode.FORMAT_CODE_39, Barcode.FORMAT_CODE_93, Barcode.FORMAT_ITF,
                Barcode.FORMAT_CODABAR, Barcode.FORMAT_QR_CODE, Barcode.FORMAT_DATA_MATRIX
            ).build()
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            scanner.close()
            executor.shutdown()
        }
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val view = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { proxy ->
                    val media = proxy.image
                    if (media == null) { proxy.close(); return@setAnalyzer }
                    val input = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                    scanner.process(input)
                        .addOnSuccessListener { codes ->
                            codes.firstNotNullOfOrNull { it.rawValue?.trim()?.takeIf { v -> v.isNotEmpty() } }?.let(onCode)
                        }
                        .addOnCompleteListener { proxy.close() }
                }
                runCatching {
                    provider.unbindAll()
                    val cam = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    onCamera(cam)
                }
            }, ContextCompat.getMainExecutor(ctx))
            view
        }
    )
}

@Composable
private fun ScanOverlay() {
    val transition = rememberInfiniteTransition(label = "scan")
    val line by transition.animateFloat(0.1f, 0.9f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "line")
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(Modifier.size(width = 290.dp, height = 190.dp).border(3.dp, Color.White, RoundedCornerShape(20.dp))) {
            Canvas(Modifier.fillMaxSize()) {
                val y = size.height * line
                drawLine(Color(0xFFF97316), Offset(16f, y), Offset(size.width - 16f, y), strokeWidth = 5f)
            }
        }
    }
}
