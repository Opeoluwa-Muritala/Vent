package app.vent

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import app.vent.ui.theme.spacing
import androidx.camera.core.TorchState
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

@Composable
internal actual fun CheckInCamera(modifier: Modifier, onCode: (String) -> Unit, onClose: () -> Unit, onManual: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, context) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }
    if (!granted) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Allow camera access to scan a check-in QR code.")
                Button({
                    context.startActivity(android.content.Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:${context.packageName}"),
                    ))
                }) { Text("Open settings") }
            }
        }
        return
    }

    val onResult by rememberUpdatedState(onCode)
    val controller = remember(context) { LifecycleCameraController(context) }
    var cameraError by remember { mutableStateOf(false) }
    var hasTorch by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }
    var torchPending by remember { mutableStateOf(false) }
    var torchError by remember { mutableStateOf(false) }
    var attached by remember { mutableStateOf(false) }
    DisposableEffect(controller, owner) {
        attached = true
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build(),
        )
        var delivered = false
        val executor = ContextCompat.getMainExecutor(context)
        val torchObserver = androidx.lifecycle.Observer<Int> { torchOn = it == TorchState.ON }
        controller.torchState.observe(owner, torchObserver)
        runCatching {
            controller.cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            controller.setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            controller.setImageAnalysisAnalyzer(executor, MlKitAnalyzer(
                listOf(scanner), ImageAnalysis.COORDINATE_SYSTEM_ORIGINAL, executor,
            ) { result ->
                if (!delivered) {
                    val code = result.getValue(scanner)?.firstNotNullOfOrNull { barcode ->
                        barcode.rawValue?.trim()?.takeIf { it.length == 6 && it.all { digit -> digit in '0'..'9' } }
                    }
                    if (code != null) {
                        delivered = true
                        onResult(code)
                    }
                }
            })
            controller.bindToLifecycle(owner)
            controller.initializationFuture.addListener({
                if (attached) {
                    runCatching { controller.initializationFuture.get() }
                        .onSuccess { hasTorch = controller.cameraInfo?.hasFlashUnit() == true }
                        .onFailure { cameraError = true }
                }
            }, executor)
        }.onFailure { cameraError = true }
        onDispose {
            attached = false
            delivered = true
            controller.torchState.removeObserver(torchObserver)
            runCatching { controller.enableTorch(false) }
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            scanner.close()
        }
    }
    if (cameraError) Box(modifier, contentAlignment = Alignment.Center) {
        Text("Camera unavailable. Enter the check-in code manually.")
    } else Box(modifier) {
        AndroidView(modifier = Modifier.matchParentSize(), factory = { PreviewView(it).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            this.controller = controller
        } })
        val lineColor = MaterialTheme.colorScheme.primary
        val spacing = MaterialTheme.spacing
        val frameSize = with(LocalDensity.current) { (spacing.touch * 5).toPx() }
        val frameStroke = with(LocalDensity.current) { spacing.xs.toPx() }
        Canvas(Modifier.matchParentSize()) {
            val frame = minOf(frameSize, size.minDimension * .8f)
            val left = (size.width - frame) / 2
            val right = left + frame
            val top = (size.height - frame) / 2
            val bottom = top + frame
            val corner = frame / 8
            for (x in listOf(left, right)) {
                for (y in listOf(top, bottom)) {
                    val point = Offset(x, y)
                    val dx = if (x == left) corner else -corner
                    val dy = if (y == top) corner else -corner
                    drawLine(lineColor, point, Offset(x + dx, y), frameStroke, StrokeCap.Round)
                    drawLine(lineColor, point, Offset(x, y + dy), frameStroke, StrokeCap.Round)
                }
            }
        }
        Surface(Modifier.align(Alignment.BottomStart).padding(spacing.lg),
            shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(spacing.sm), horizontalAlignment = Alignment.CenterHorizontally) {
                IconToggleButton(
                    checked = torchOn,
                    enabled = hasTorch && !torchPending,
                    modifier = Modifier.size(spacing.touch).semantics {
                        stateDescription = if (torchOn) "Torch on" else "Torch off"
                    },
                    onCheckedChange = { enabled ->
                        torchError = false
                        torchPending = true
                        runCatching { controller.enableTorch(enabled) }
                            .onSuccess { future ->
                                future.addListener({
                                    if (attached) {
                                        torchPending = false
                                        torchError = runCatching { future.get() }.isFailure
                                    }
                                }, ContextCompat.getMainExecutor(context))
                            }
                            .onFailure { torchPending = false; torchError = true }
                    },
                ) { Icon(if (torchOn) Icons.Outlined.FlashlightOn else Icons.Outlined.FlashlightOff, "Torch") }
                Text(if (!hasTorch) "Torch unavailable" else if (torchOn) "Torch on" else "Torch off",
                    style = MaterialTheme.typography.labelLarge)
                if (torchError) Text("Could not change the torch.", color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
