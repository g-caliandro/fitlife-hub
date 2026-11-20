package com.lifehub.app.feature.food

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.lifehub.app.container
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.components.LhButton
import com.lifehub.app.core.designsystem.components.LhButtonVariant
import com.lifehub.app.core.designsystem.components.LhEmptyState
import com.lifehub.app.core.designsystem.components.LhTopBar
import com.lifehub.app.data.repo.FoodLookup
import com.lifehub.app.data.repo.ProductLookup
import com.lifehub.app.navigation.Routes
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.concurrent.Executors

/**
 * One-shot result of the scanner opened from the copilot chat ("pick" mode): the id of the cached food the user scanned.
 * Same pattern as [com.lifehub.app.navigation.DiaryJump]; the chat consumes it when it is shown again.
 */
object ScanPick {
    private val _foodId = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val foodId: kotlinx.coroutines.flow.StateFlow<String?> = _foodId
    fun set(id: String) { _foodId.value = id }
    fun consume() { _foodId.value = null }
}

/** What the scanner screen shows. */
sealed interface ScanUi {
    /** Camera permission not granted yet (first ask or soft denial). */
    data class NeedsPermission(val deniedBefore: Boolean) : ScanUi
    /** Denied with "don't ask again": only system settings can fix it. */
    data object PermanentlyDenied : ScanUi
    data object Scanning : ScanUi
    data class Looking(val code: String) : ScanUi
    data class NotFound(val code: String) : ScanUi
    data class Failed(val code: String, val message: String) : ScanUi
}

@Composable
fun ScanRoute(date: LocalDate, onNavigate: (String) -> Unit, onBack: () -> Unit, pick: Boolean = false) {
    val ctx = LocalContext.current
    val c = ctx.container
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var asked by remember { mutableStateOf(false) }
    var permanent by remember { mutableStateOf(false) }
    var lookup by remember { mutableStateOf<ScanUi?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok; asked = true
        if (!ok) permanent = !ActivityCompat.shouldShowRequestPermissionRationale(ctx as android.app.Activity, Manifest.permission.CAMERA)
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    fun onCode(code: String) {
        if (lookup != null) return
        lookup = ScanUi.Looking(code)
        scope.launch {
            if (pick) {
                // Copilot chat: the product goes back to the composer as an attachment instead of opening the food detail.
                when (val r = c.barcodeLookup.findProduct(code)) {
                    is ProductLookup.Found -> { ScanPick.set(c.barcodeLookup.cache(r.product).id); onBack() }
                    ProductLookup.NotFound -> lookup = ScanUi.NotFound(code)
                    is ProductLookup.Failed -> lookup = ScanUi.Failed(code, r.message)
                }
                return@launch
            }
            when (val r = c.barcodeLookup.findFood(code)) {
                is FoodLookup.Found -> onNavigate(Routes.replace(Routes.foodDetail(r.food.id, date)))
                FoodLookup.NotFound -> lookup = ScanUi.NotFound(code)
                is FoodLookup.Failed -> lookup = ScanUi.Failed(code, r.message)
            }
        }
    }

    val ui = lookup ?: when {
        granted -> ScanUi.Scanning
        permanent -> ScanUi.PermanentlyDenied
        else -> ScanUi.NeedsPermission(asked)
    }
    ScanScreen(
        ui, onBack,
        onRequestPermission = { launcher.launch(Manifest.permission.CAMERA) },
        onOpenSettings = { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
        onRetry = { lookup = null },
        onCreateCustom = { code -> onNavigate(Routes.replace(Routes.foodCustom(barcode = code))) },
        allowCustom = !pick,
        camera = { CameraPreview(enabled = lookup == null, onBarcode = ::onCode) },
    )
}

@Composable
fun ScanScreen(
    ui: ScanUi, onBack: () -> Unit, onRequestPermission: () -> Unit, onOpenSettings: () -> Unit,
    onRetry: () -> Unit, onCreateCustom: (String) -> Unit, camera: @Composable () -> Unit = {},
    /** False when the scanner picks a product for the copilot chat: no "create custom food" there. */
    allowCustom: Boolean = true,
) {
    val c = LhTheme.colors
    Column(Modifier.fillMaxSize().background(c.background)) {
        LhTopBar("Scan barcode", onBack = onBack)
        Box(Modifier.weight(1f).fillMaxWidth().testTag("scan-body"), contentAlignment = Alignment.Center) {
            when (ui) {
                is ScanUi.NeedsPermission -> LhEmptyState(
                    "Camera access needed",
                    description = if (ui.deniedBefore) "Without the camera we can't read barcodes. You can still search by name or create a custom food."
                    else "FitLife Hub uses the camera only to read product barcodes. Nothing is recorded or stored.",
                    icon = Icons.Outlined.CameraAlt, actionText = "Allow camera", onAction = onRequestPermission,
                )
                ScanUi.PermanentlyDenied -> LhEmptyState(
                    "Camera is blocked",
                    description = "Camera permission was denied. Enable it in the system settings to scan, or go back and search by name.",
                    icon = Icons.Outlined.CameraAlt, actionText = "Open settings", onAction = onOpenSettings, actionIcon = Icons.Outlined.Settings,
                )
                ScanUi.Scanning -> {
                    camera()
                    Text("Point the camera at a barcode", style = LhType.label, color = c.foreground,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp).background(c.background.copy(alpha = 0.8f), LhShapes.pill).padding(horizontal = 16.dp, vertical = 8.dp))
                }
                is ScanUi.Looking -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = c.primary)
                    Text("Looking up ${ui.code}…", style = LhType.body, color = c.mutedForeground)
                }
                is ScanUi.NotFound -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LhEmptyState("Product not found", description = "${ui.code} isn't in Open Food Facts yet. Add it as a custom food.")
                    if (allowCustom) LhButton("Create custom food", { onCreateCustom(ui.code) }, leadingIcon = Icons.Outlined.Add)
                    LhButton("Scan again", onRetry, variant = LhButtonVariant.Tonal)
                }
                is ScanUi.Failed -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LhEmptyState("Lookup failed", description = ui.message)
                    LhButton("Try again", onRetry, variant = LhButtonVariant.Tonal)
                    if (allowCustom) LhButton("Create custom food", { onCreateCustom(ui.code) }, variant = LhButtonVariant.Outline, leadingIcon = Icons.Outlined.Add)
                }
            }
        }
    }
}

private object LhShapes { val pill = androidx.compose.foundation.shape.RoundedCornerShape(50) }

/** CameraX preview + ML Kit (bundled model) barcode analysis. Reports each new code once while [enabled]. */
@Composable
private fun CameraPreview(enabled: Boolean, onBarcode: (String) -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
                .build(),
        )
    }
    val latestEnabled by rememberUpdatedStateCompat(enabled)
    val latestCallback by rememberUpdatedStateCompat(onBarcode)
    DisposableEffect(Unit) { onDispose { executor.shutdown(); scanner.close() } }
    AndroidView(
        modifier = Modifier.fillMaxSize().testTag("camera-preview"),
        factory = { context ->
            val view = PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(executor) { proxy -> analyze(proxy, scanner) { code -> if (latestEnabled) view.post { latestCallback(code) } } }
                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }
            }, ContextCompat.getMainExecutor(context))
            view
        },
        onRelease = { runCatching { ProcessCameraProvider.getInstance(ctx).get().unbindAll() } },
    )
}

@Composable
private fun <T> rememberUpdatedStateCompat(v: T) = androidx.compose.runtime.rememberUpdatedState(v)

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun analyze(proxy: ImageProxy, scanner: com.google.mlkit.vision.barcode.BarcodeScanner, onCode: (String) -> Unit) {
    val media = proxy.image
    if (media == null) { proxy.close(); return }
    val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
    scanner.process(image)
        .addOnSuccessListener { list -> list.firstNotNullOfOrNull { it.rawValue }?.let(onCode) }
        .addOnCompleteListener { proxy.close() }
}
