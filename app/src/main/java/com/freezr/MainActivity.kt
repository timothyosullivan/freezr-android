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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import kotlin.math.abs
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
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val vm: ContainerViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        maybeRequestNotificationPermission()
        setContent { FreezrApp(vm) }
    }

    private fun maybeRequestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val perm = android.Manifest.permission.POST_NOTIFICATIONS
            if (checkSelfPermission(perm) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(perm), 1001)
            }
        }
    }
}

@Composable
fun FreezrApp(vm: ContainerViewModel) {
    val state by vm.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showScan by remember { mutableStateOf(false) }
    val scanDialog by vm.scanDialog.collectAsState()

    var labelTarget by remember { mutableStateOf<com.freezr.data.model.Container?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    Scaffold(
    topBar = { TopBar(state, onSort = vm::setSort, onToggleUsed = { vm.setShowUsed(!state.showUsed) }, onScan = { showScan = true }, onOpenSettings = { settingsOpen = true }) },
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
                onMarkUsed = vm::markUsed,
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
        if (settingsOpen) SettingsDialog(vm = vm, onClose = { settingsOpen = false })
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
                            val item = existing
                            val now = System.currentTimeMillis()
                            val reminderAt = item?.reminderAt
                            val (label, color) = if (reminderAt != null) {
                                val diff = reminderAt - now
                                when {
                                    diff < 0 -> rel(diff) to Color(0xFFC62828)
                                    diff <= 7L*24*60*60*1000 -> rel(diff) to Color(0xFFF9A825)
                                    else -> rel(diff) to Color(0xFF2E7D32)
                                }
                            } else "No reminder" to MaterialTheme.colorScheme.outline
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(14.dp).background(color, CircleShape))
                                Spacer(Modifier.width(8.dp))
                                Text("Reminder: $label", style = MaterialTheme.typography.labelMedium)
                            }
                            if (reminderAt != null) {
                                Spacer(Modifier.height(4.dp))
                                TextButton(onClick = { vm.updateReminderAt(item!!.id, reminderAt + 7L*24*60*60*1000) }) { Text("Snooze +7d") }
                            }
                        }
                    },
                    confirmButton = {
                        Row { 
                            TextButton(onClick = { existing?.let { vm.markUsed(it.id) }; vm.dismissScanDialog() }) { Text("Mark Used") }
                            TextButton(onClick = { vm.dismissScanDialog() }) { Text("Close") }
                        }
                    }
                )
                ScanMode.HISTORICAL -> AlertDialog(
                    onDismissRequest = { vm.dismissScanDialog() },
                    title = { Text("Reuse Label") },
                    text = {
                        Column {
                            Text("Used label: ${existing?.name}", style = MaterialTheme.typography.bodySmall)
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
private fun TopBar(state: ContainerUiState, onSort: (SortOrder) -> Unit, onToggleUsed: () -> Unit, onScan: () -> Unit, onOpenSettings: () -> Unit) {
    val contextLocal = androidx.compose.ui.platform.LocalContext.current
    var overflow by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text("Freezr") },
        actions = {
            SortMenu(current = state.sortOrder, onSelect = onSort)
            FilterUsedChip(show = state.showUsed, onToggle = onToggleUsed)
            ReminderFilterMenu(current = state.filter)
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
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Filled.Settings, contentDescription = null)
                                Spacer(Modifier.width(8.dp)); Text("Settings")
                            }
                        },
                        onClick = { overflow = false; onOpenSettings() }
                    )
                }
            }
        },
        modifier = Modifier.testTag(UiTestTags.TopBar)
    )
}

@Composable
private fun SettingsDialog(vm: ContainerViewModel, onClose: () -> Unit) {
    val state by vm.state.collectAsState()
    var daysText by remember(state.defaultReminderDays) { mutableStateOf(state.defaultReminderDays.toString()) }
    AlertDialog(onDismissRequest = onClose, title = { Text("Settings") }, text = {
        Column { 
            Text("Default reminder days")
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(value = daysText, onValueChange = { if (it.length <=3) daysText = it.filter { ch -> ch.isDigit() } }, singleLine = true, label = { Text("Days") })
            Spacer(Modifier.height(8.dp))
            Text(
                "Used as the default reminder window for new items when you scan a label (or reuse one) and haven't set a custom reminder.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }, confirmButton = {
        val days = daysText.toIntOrNull() ?: -1
        TextButton(enabled = days in 1..365, onClick = { vm.setDefaultReminderDays(days); onClose() }) { Text("Save") }
    }, dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } })
}


@Composable
private fun ReminderFilterMenu(current: ReminderFilter) {
    val vm: ContainerViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    var expanded by remember { mutableStateOf(false) }
    Box { 
        TextButton(onClick = { expanded = true }) { Text(when(current){ ReminderFilter.NONE -> "All"; ReminderFilter.EXPIRING_SOON -> "Soon"; ReminderFilter.EXPIRED -> "Expired" }) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ReminderFilter.values().forEach { f ->
                DropdownMenuItem(text = { Text(when(f){ ReminderFilter.NONE->"All"; ReminderFilter.EXPIRING_SOON->"Expiring ≤7d"; ReminderFilter.EXPIRED->"Expired" }) }, onClick = {
                    vm.setReminderFilter(f); expanded = false
                })
            }
        }
    }
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
private fun FilterUsedChip(show: Boolean, onToggle: () -> Unit) {
    AssistChip(onClick = onToggle, label = { Text(if (show) "Show Used" else "Hide Used") }, modifier = Modifier.testTag(UiTestTags.FilterArchivedChip))
}

// Removed AddFab: creation now exclusively via scanning a QR code.

@Composable
private fun ContainerList(items: List<Container>, onMarkUsed: (Long) -> Unit, onDelete: (Long) -> Unit, onLabel: (Container) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(8.dp).testTag(UiTestTags.ContainerList)) {
        items(items, key = { it.id }) { c ->
            val now = System.currentTimeMillis()
            val baseStatusColor = when (c.status) {
                Status.ACTIVE -> Color(0xFF2E7D32)
                Status.USED -> Color(0xFFC62828)
                Status.UNUSED -> Color(0xFF607D8B)
                Status.DELETED -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            }
            val statusColor = if (c.status == Status.ACTIVE && c.reminderAt != null) {
                val diff = c.reminderAt - now
                when {
                    diff < 0 -> Color(0xFFC62828) // overdue
                    diff <= 7L*24*60*60*1000 -> Color(0xFFF9A825) // soon
                    else -> baseStatusColor
                }
            } else baseStatusColor
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("${'$'}{UiTestTags.ContainerCardPrefix}${'$'}{c.id}")
            ) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(14.dp)
                                    .background(statusColor, CircleShape)
                                    .testTag("StatusDot")
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                c.name.ifBlank { "(unnamed)" },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text("Qty: ${c.quantity}", style = MaterialTheme.typography.bodySmall)
                        val reminder = c.reminderDays?.let { "Rem: ${it}d" } ?: "Rem: default"
                        Text(reminder, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(2.dp))
                        // Compact meta line: show uuid snippet & textual status (for accessibility)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(c.status.name, style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.width(8.dp))
                            Text(c.uuid.take(8), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Row {
                        when (c.status) {
                            Status.UNUSED -> {}
                            Status.ACTIVE -> TextButton(onClick = { onMarkUsed(c.id) }) { Text("Used") }
                            Status.USED -> {}
                            Status.DELETED -> {}
                        }
                        TextButton(onClick = { onLabel(c) }) { Text("Label") }
                        TextButton(onClick = { onDelete(c.id) }, modifier = Modifier.testTag(UiTestTags.DeleteButton)) { Text("Delete") }
                    }
                }
            }
        }
    }
}

private fun rel(diffMillis: Long): String {
    val absMs = abs(diffMillis)
    val days = absMs / (24L*60*60*1000)
    return if (diffMillis < 0) "${days}d overdue" else if (days==0L) "<1d" else "in ${days}d"
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
