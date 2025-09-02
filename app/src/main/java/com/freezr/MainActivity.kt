package com.freezr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import com.freezr.label.QrCodeGenerator
import android.graphics.Bitmap
import com.freezr.data.model.*
import androidx.core.content.FileProvider
import android.content.Intent
import com.freezr.label.LabelPdfGenerator
import com.freezr.label.QR_PREFIX
import androidx.camera.view.PreviewView
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import java.util.concurrent.Executors
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.toArgb

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val vm: ContainerViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { FreezrApp(vm) } }
}

@Composable
fun FreezrApp(vm: ContainerViewModel) {
    val state by vm.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showScan by remember { mutableStateOf(false) }
    val scanDialog by vm.scanDialog.collectAsState()

    var labelTarget by remember { mutableStateOf<com.freezr.data.model.Container?>(null) }
    Scaffold(
        topBar = { TopBar(state, onSort = vm::setSort, onToggleArchived = { vm.setShowArchived(!state.showArchived) }, onScan = { showScan = true }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // Only creation path is scanning; FAB opens scanner
            FloatingActionButton(onClick = { showScan = true }, modifier = Modifier.testTag(UiTestTags.FabAdd)) { Text("Scan") }
        }
    ) { padding ->
    Column(Modifier.padding(padding).testTag(UiTestTags.RootColumn)) {
            PlaceholderPrintDialog(vm) {}
            if (showScan) {
                ScanScreen(onClose = { showScan = false }, onResult = { uuid ->
                    showScan = false
                    vm.handleScan(uuid)
                })
            } else {
            ContainerList(
                items = state.items,
                onArchive = vm::archive,
                onActivate = vm::activate,
                onDelete = { id ->
                    vm.softDelete(id)
                    scope.launch {
                        val res = snackbarHostState.showSnackbar("Deleted", actionLabel = "Undo")
                        if (res == SnackbarResult.ActionPerformed) vm.undoLastDelete()
                    }
                },
                onLabel = { c -> labelTarget = c }
            )
            }
            BuildFooter()
        }
        scanDialog?.let { sd ->
            val existing = sd.existing
            var name by remember(sd.uuid) { mutableStateOf(existing?.name ?: "") }
            when (sd.mode) {
                ScanMode.UNKNOWN -> {
                    var newName by remember(sd.uuid) { mutableStateOf("") }
                    AlertDialog(
                        onDismissRequest = { vm.dismissScanDialog() },
                        title = { Text("Create From Scan") },
                        text = {
                            Column {
                                Text("Scanned code not yet tracked. Save as a container.")
                                Spacer(Modifier.height(8.dp))
                                Text("Code: ${sd.uuid.take(48)}", style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Name") })
                            }
                        },
                        confirmButton = {
                            TextButton(enabled = newName.isNotBlank(), onClick = {
                                vm.createFromScan(newName.trim())
                                scope.launch { snackbarHostState.showSnackbar("Created from scan") }
                            }) { Text("Create") }
                        },
                        dismissButton = { TextButton(onClick = { vm.dismissScanDialog() }) { Text("Cancel") } }
                    )
                }
                ScanMode.UNUSED -> AlertDialog(
                    onDismissRequest = { vm.dismissScanDialog() },
                    title = { Text("Claim Label") },
                    text = {
                        Column {
                            Text("UUID: ${sd.uuid}", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Description") })
                        }
                    },
                    confirmButton = {
                        TextButton(enabled = name.isNotBlank(), onClick = {
                            vm.claimFromScan(name.trim())
                            scope.launch { snackbarHostState.showSnackbar("Label claimed") }
                        }) { Text("Save") }
                    },
                    dismissButton = { TextButton(onClick = { vm.dismissScanDialog() }) { Text("Cancel") } }
                )
                ScanMode.ACTIVE -> AlertDialog(
                    onDismissRequest = { vm.dismissScanDialog() },
                    title = { Text(existing?.name ?: "Item") },
                    text = {
                        Column {
                            Text("UUID: ${sd.uuid}", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(4.dp))
                            Text("Status: ACTIVE", style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    confirmButton = { TextButton(onClick = { vm.dismissScanDialog() }) { Text("Close") } }
                )
                ScanMode.HISTORICAL -> AlertDialog(
                    onDismissRequest = { vm.dismissScanDialog() },
                    title = { Text("Reuse Label") },
                    text = {
                        Column {
                            Text("Archived/Used label: ${existing?.name}", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("New Description (optional)") })
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            vm.reuseFromScan(name.takeIf { it.isNotBlank() })
                            scope.launch { snackbarHostState.showSnackbar("Reused label") }
                        }) { Text("Reuse") }
                    },
                    dismissButton = { TextButton(onClick = { vm.dismissScanDialog() }) { Text("Cancel") } }
                )
            }
        }
        labelTarget?.let { c ->
            AlertDialog(onDismissRequest = { labelTarget = null }, confirmButton = {
                TextButton(onClick = { labelTarget = null }) { Text("Close") }
            }, title = { Text("Label: ${c.name}") }, text = {
                val matrix = remember(c.uuid) { QrCodeGenerator.matrix(QR_PREFIX + c.uuid, 256) }
                val bmp = remember(matrix) {
                    Bitmap.createBitmap(matrix.size, matrix.size, Bitmap.Config.ARGB_8888).apply {
                        for (y in 0 until matrix.size) for (x in 0 until matrix.size) {
                            setPixel(x,y, if (matrix.get(x,y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
                        }
                    }
                }
                Column { Image(bmp.asImageBitmap(), contentDescription = "QR ${'$'}{c.name}")
                    Text(c.uuid, style = MaterialTheme.typography.bodySmall)
                }
            })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(state: ContainerUiState, onSort: (SortOrder) -> Unit, onToggleArchived: () -> Unit, onScan: () -> Unit) {
    val contextLocal = androidx.compose.ui.platform.LocalContext.current
    var overflow by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text("Freezr") },
        actions = {
            SortMenu(current = state.sortOrder, onSelect = onSort)
            FilterArchivedChip(show = state.showArchived, onToggle = onToggleArchived)
            // Overflow menu for secondary actions to reduce crowding
            Box {
                TextButton(onClick = { overflow = true }) { Text("⋮") }
                DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
            DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Icon(painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_print), contentDescription = null)
                                Spacer(Modifier.width(8.dp)); Text("Print Labels")
                            }
                        },
                        onClick = {
                            overflow = false
                // Dialog will be triggered via shared state below
                PlaceholderPrintController.open()
                        }
                    )
            DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Icon(painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_scan), contentDescription = null)
                                Spacer(Modifier.width(8.dp)); Text("Scan Label")
                            }
                        },
                        onClick = {
                            overflow = false
                onScan()
                        }
                    )
                }
            }
        },
        modifier = Modifier.testTag(UiTestTags.TopBar)
    )
}

// Simple singleton holder for opening placeholder generation dialog without prop-drilling
private object PlaceholderPrintController { var open by mutableStateOf(false); fun open() { open = true } fun close() { open = false } }

@Composable
private fun PlaceholderPrintDialog(vm: ContainerViewModel, onDismiss: () -> Unit) {
    var countText by remember { mutableStateOf("20") }
    var isGenerating by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    if (!PlaceholderPrintController.open) return
    AlertDialog(
        onDismissRequest = { if (!isGenerating) { PlaceholderPrintController.close(); onDismiss() } },
        title = { Text("Generate Labels") },
        text = {
            Column {
                Text("Enter how many blank labels to print. They become containers only when scanned & claimed.")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = countText, onValueChange = { if (it.length <= 3) countText = it.filter { ch -> ch.isDigit() } }, label = { Text("Count") })
            }
        },
        confirmButton = {
            val count = countText.toIntOrNull() ?: 0
            val scope = rememberCoroutineScope()
            TextButton(enabled = !isGenerating && count in 1..200, onClick = {
                isGenerating = true
                scope.launch(Dispatchers.Default) {
                    val labels = List(count) {
                        val uuid = java.util.UUID.randomUUID().toString()
                        LabelPdfGenerator.Label(title = uuid.take(6), uuid = uuid)
                    }
                    val file = LabelPdfGenerator.generate(ctx, labels, "blank_labels.pdf")
                    withContext(Dispatchers.Main) {
                        isGenerating = false
                        PlaceholderPrintController.close(); onDismiss()
                        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        ctx.startActivity(Intent.createChooser(intent, "Share labels PDF"))
                    }
                }
            }) { Text(if (isGenerating) "Working..." else "Generate") }
        },
        dismissButton = { TextButton(enabled = !isGenerating, onClick = { PlaceholderPrintController.close(); onDismiss() }) { Text("Cancel") } }
    )
}

@Composable
private fun SortMenu(current: SortOrder, onSelect: (SortOrder) -> Unit) {
    fun label(so: SortOrder) = when(so) {
        SortOrder.NAME_ASC -> "Name A→Z"
        SortOrder.NAME_DESC -> "Name Z→A"
        SortOrder.CREATED_ASC -> "Oldest"
        SortOrder.CREATED_DESC -> "Newest"
    }
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.testTag(UiTestTags.SortMenuBox)) {
        TextButton(onClick = { expanded = true }, modifier = Modifier.testTag(UiTestTags.SortMenuButton)) { Text(label(current)) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortOrder.values().forEach { so ->
                DropdownMenuItem(text = { Text(label(so)) }, onClick = { onSelect(so); expanded = false }, modifier = Modifier.testTag("SortOption_${'$'}{so.name}"))
            }
        }
    }
}

@Composable
private fun ScanScreen(onClose: () -> Unit, onResult: (String) -> Unit) {
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    var detected by remember { mutableStateOf<String?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var cameraRef by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    LaunchedEffect(detected) { detected?.let { onResult(it) } }
    Box(Modifier.fillMaxSize()) {
    AndroidView(factory = { ctx: android.content.Context ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            val analysisExecutor = Executors.newSingleThreadExecutor()
            val mainExecutor = androidx.core.content.ContextCompat.getMainExecutor(ctx)
            cameraProviderFuture.addListener({
                val provider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analyzer = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                val scanner = BarcodeScanning.getClient()
                analyzer.setAnalyzer(analysisExecutor) { imageProxy ->
                    if (paused || detected != null) { imageProxy.close(); return@setAnalyzer }
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val rotation = imageProxy.imageInfo.rotationDegrees
                        val image = InputImage.fromMediaImage(mediaImage, rotation)
                        scanner.process(image)
                            .addOnSuccessListener { list ->
                                list.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }?.rawValue?.let { value ->
                                    if (detected == null) {
                                        val uuid = if (value.startsWith(QR_PREFIX)) value.removePrefix(QR_PREFIX) else value
                                        detected = uuid.take(120) // limit length defensively
                                    }
                                }
                            }
                            .addOnCompleteListener { imageProxy.close() }
                    } else imageProxy.close()
                }
                try {
                    provider.unbindAll()
                    val cam = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analyzer)
                    cameraRef = cam
                    if (torchOn) cam.cameraControl.enableTorch(true)
                } catch (_: Exception) { }
            }, mainExecutor)
            previewView
        }, modifier = Modifier.fillMaxSize())
        // Framing overlay
        val guideSize = 220.dp
        val frameColor = MaterialTheme.colorScheme.primary // capture outside draw scope
        Box(
            Modifier
                .align(Alignment.Center)
                .size(guideSize)
                .drawBehind {
                    val strokePx = 4.dp.toPx()
                    drawRoundRect(
                        color = frameColor,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f,16f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokePx)
                    )
                }
        )
        Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.small, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose) { Text("Close") }
                TextButton(onClick = {
                    torchOn = !torchOn
                    cameraRef?.cameraControl?.enableTorch(torchOn)
                }) { Text(if (torchOn) "Torch Off" else "Torch On") }
                TextButton(onClick = { paused = !paused }) { Text(if (paused) "Resume" else "Pause") }
                TextButton(onClick = { if (detected == null) detected = "TEST-UUID-INJECT" }) { Text("Inject Test QR") }
            }
        }
    }
}

@Composable
private fun FilterArchivedChip(show: Boolean, onToggle: () -> Unit) {
    AssistChip(onClick = onToggle, label = { Text(if (show) "Archived On" else "Archived Off") }, modifier = Modifier.testTag(UiTestTags.FilterArchivedChip))
}

// Removed AddFab: creation now exclusively via scanning a QR code.

@Composable
private fun ContainerList(items: List<Container>, onArchive: (Long) -> Unit, onActivate: (Long) -> Unit, onDelete: (Long) -> Unit, onLabel: (Container) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(8.dp).testTag(UiTestTags.ContainerList)) {
        items(items, key = { it.id }) { c ->
            ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("${'$'}{UiTestTags.ContainerCardPrefix}${'$'}{c.id}")) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        val qtyLine = "Qty: ${c.quantity}"
                        Text(qtyLine, style = MaterialTheme.typography.bodySmall)
                        val reminder = c.reminderDays?.let { "Rem: ${it}d" } ?: "Rem: default"
                        Text(reminder, style = MaterialTheme.typography.bodySmall)
                        Text(c.status.name, style = MaterialTheme.typography.labelSmall)
                        Text(c.uuid.take(8), style = MaterialTheme.typography.labelSmall)
                    }
                    Row { 
                        when (c.status) {
                            Status.UNUSED -> { /* placeholder: no actions except maybe future claim shortcut */ }
                            Status.ACTIVE -> TextButton(onClick = { onArchive(c.id) }, modifier = Modifier.testTag(UiTestTags.ArchiveButton)) { Text("Archive") }
                            Status.ARCHIVED -> TextButton(onClick = { onActivate(c.id) }, modifier = Modifier.testTag(UiTestTags.ActivateButton)) { Text("Activate") }
                            Status.DELETED -> {}
                            Status.USED -> {}
                        }
                        TextButton(onClick = { onLabel(c) }) { Text("Label") }
                        TextButton(onClick = { onDelete(c.id) }, modifier = Modifier.testTag(UiTestTags.DeleteButton)) { Text("Delete") }
                    }
                }
            }
        }
    }
}

@Composable
private fun BuildFooter() {
    val sha = BuildConfig.GIT_SHA
    val dirty = sha.endsWith("-dirty")
    val label = if (sha == "UNKNOWN") "NO GIT SHA" else sha
    Surface(tonalElevation = 4.dp) {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(if (dirty) "DIRTY" else "", style = MaterialTheme.typography.labelSmall)
        }
    }
}
