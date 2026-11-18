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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

@Composable
internal actual fun CheckInCamera(modifier: Modifier, onCode: (String) -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }
    if (!granted) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Allow camera access to scan a check-in QR code.")
                Button({ permission.launch(Manifest.permission.CAMERA) }) { Text("Allow camera access") }
            }
        }
        return
    }

    val owner = LocalLifecycleOwner.current
    val onResult by rememberUpdatedState(onCode)
    val controller = remember(context) { LifecycleCameraController(context) }
    var cameraError by remember { mutableStateOf(false) }
    DisposableEffect(controller, owner) {
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build(),
        )
        var delivered = false
        val executor = ContextCompat.getMainExecutor(context)
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
        }.onFailure { cameraError = true }
        onDispose {
            delivered = true
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            scanner.close()
        }
    }
    if (cameraError) Box(modifier, contentAlignment = Alignment.Center) {
        Text("Camera unavailable. Enter the check-in code manually.")
    } else AndroidView(modifier = modifier, factory = { PreviewView(it).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        this.controller = controller
    } })
}
