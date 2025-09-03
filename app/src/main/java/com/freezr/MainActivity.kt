package com.freezr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.viewinterop.AndroidView
import dagger.hilt.android.AndroidEntryPoint
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import kotlin.math.abs
import com.freezr.data.model.*
import com.freezr.label.QR_PREFIX
import com.freezr.label.QrCodeGenerator
import com.freezr.label.LabelPdfGenerator

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val vm: ContainerViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        maybeRequestNotificationPermission()
    maybeRequestCameraPermission()
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

    private fun maybeRequestCameraPermission() {
        val perm = android.Manifest.permission.CAMERA
        if (checkSelfPermission(perm) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(perm), 1002)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FreezrApp(vm: ContainerViewModel) {
    val ui by vm.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showScan by remember { mutableStateOf(false) }
    val scanDialog by vm.scanDialog.collectAsState()
    var settingsOpen by remember { mutableStateOf(false) }
    var labelPreview: Container? by remember { mutableStateOf(null) }
    var printing by remember { mutableStateOf(false) }
    var pendingPrintCount by remember { mutableStateOf<Int?>(null) }
    Scaffold(
        topBar = { TopBar(ui, onSort = vm::setSort, onToggleUsed = { vm.setShowUsed(!ui.showUsed) }, onOpenSettings = { settingsOpen = true }, onPrint = { printing = true }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = { FloatingActionButton(onClick = { showScan = true }, modifier = Modifier.testTag(UiTestTags.FabAdd)) { Text("Scan") } }
    ) { padding ->
        Column(Modifier.padding(padding).testTag(UiTestTags.RootColumn)) {
            if (showScan) {
                ScanScreen(onClose = { showScan = false }, onResult = { uuid ->
                    showScan = false
                    vm.handleScan(uuid)
                })
            } else {
                ContainerList(
                    items = ui.items,
                    expiringSoonDays = ui.expiringSoonDays,
                    criticalDays = ui.criticalDays,
                    defaultDays = ui.defaultReminderDays,
                    onMarkUsed = vm::markUsed,
                    onDelete = { id ->
                        vm.softDelete(id)
                        scope.launch {
                            // Dismiss any existing snackbar to avoid stacking and lingering
                            snackbarHostState.currentSnackbarData?.dismiss()
                            val res = snackbarHostState.showSnackbar("Deleted", actionLabel = "Undo")
                            if (res == SnackbarResult.ActionPerformed) vm.undoLastDelete() else snackbarHostState.currentSnackbarData?.dismiss()
                        }
                    },
                    onLabel = { c -> labelPreview = c }
                )
            }
            BuildFooter()
        }
        if (settingsOpen) SettingsDialog(vm = vm, onClose = { settingsOpen = false })
        val context = androidx.compose.ui.platform.LocalContext.current
        if (printing) PrintDialog(onDismiss = { printing = false }) { count ->
            printing = false
            pendingPrintCount = count
        }
        pendingPrintCount?.let { cnt ->
            LaunchedEffect(cnt) {
                val labels = (1..cnt).map { LabelPdfGenerator.Label(title = "", uuid = java.util.UUID.randomUUID().toString()) }
                val file = LabelPdfGenerator.generate(context, labels)
                val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(android.content.Intent.createChooser(intent, "Share Labels PDF"))
                pendingPrintCount = null
            }
        }
        labelPreview?.let { c ->
            LabelPreviewDialog(container = c, defaultShelfLifeDays = ui.defaultReminderDays, onDismiss = { labelPreview = null })
        }
        scanDialog?.let { sd ->
            val existing = sd.existing
            when (sd.mode) {
                ScanMode.UNKNOWN -> ClaimDialog(uuid = sd.uuid, initialName = "", onDismiss = vm::dismissScanDialog) { name, shelf, alert ->
                    vm.createFromScan(name, reminderDays = alert, shelfLifeDays = shelf)
                    scope.launch { snackbarHostState.showSnackbar("Created from scan") }
                }
                ScanMode.UNUSED -> ClaimDialog(uuid = sd.uuid, initialName = existing?.name ?: "", onDismiss = vm::dismissScanDialog) { name, shelf, alert ->
                    vm.claimFromScan(name, shelfLifeDays = shelf, reminderDays = alert)
                    scope.launch { snackbarHostState.showSnackbar("Label claimed") }
                }
                ScanMode.ACTIVE -> ActiveDialog(container = existing!!, ui = ui, onDismiss = vm::dismissScanDialog,
                    onShelfLife = { d -> vm.updateShelfLifeDays(existing.id, d) },
                    onAlertDays = { d -> vm.updateReminderDays(existing.id, d) },
                    onSnooze = { at -> vm.updateReminderAt(existing.id, at) },
                    onMarkUsed = { vm.markUsed(existing.id) })
                ScanMode.HISTORICAL -> ReuseDialog(initialName = existing?.name ?: "", onDismiss = vm::dismissScanDialog) { name, shelf, alert ->
                    vm.reuseFromScan(name.ifBlank { null }, reminderDays = alert, shelfLifeDays = shelf)
                    scope.launch { snackbarHostState.showSnackbar("Reused label") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(ui: ContainerUiState, onSort: (SortOrder) -> Unit, onToggleUsed: () -> Unit, onOpenSettings: () -> Unit, onPrint: () -> Unit) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Freezr", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(16.dp))
                FilterUsedChip(ui.showUsed, onToggleUsed)
            }
        },
        actions = {
            SortMenu(ui.sortOrder, onSort)
            IconButton(onClick = { onPrint() }) { Icon(painterResource(id = R.drawable.ic_print), contentDescription = "Print QR Labels") }
            var menu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        onClick = { menu = false; onOpenSettings() }
                    )
                }
            }
        },
        modifier = Modifier.testTag(UiTestTags.TopBar)
    )
}

@Composable
private fun PrintDialog(onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var countText by remember { mutableStateOf("21") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Print Blank Labels") }, text = {
        Column {
            Text("How many blank labels do you want?", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = countText, onValueChange = { if (it.length<=3) countText = it.filter(Char::isDigit) }, label = { Text("Count") })
            Spacer(Modifier.height(4.dp))
            Text("They will contain only QR codes. After printing & cutting, scan to claim.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = {
        val count = countText.toIntOrNull()?.coerceIn(1, 500) ?: 0
        TextButton(enabled = count>0, onClick = { onConfirm(count) }) { Text("Generate") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun ClaimDialog(uuid: String, initialName: String, onDismiss: () -> Unit, onSave: (String, Int?, Int?) -> Unit) {
    var name by remember(uuid) { mutableStateOf(initialName) }
    var shelfLife by remember { mutableStateOf("") }
    var alert by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Claim Label") }, text = {
        Column {
            Text("UUID: $uuid", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Description") })
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = shelfLife, onValueChange = { if (it.length<=3) shelfLife = it.filter(Char::isDigit) }, label = { Text("Shelf life days (optional)") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = alert, onValueChange = { if (it.length<=3) alert = it.filter(Char::isDigit) }, label = { Text("Reminder alert in (days, optional)") }, singleLine = true)
            Spacer(Modifier.height(4.dp))
            Text("Shelf life = total freshness. Reminder = earlier alert.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = {
        val shelf = shelfLife.toIntOrNull()
        val alertDays = alert.toIntOrNull()
        TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), shelf, alertDays); onDismiss() }) { Text("Save") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun ActiveDialog(container: Container, ui: ContainerUiState, onDismiss: () -> Unit, onShelfLife: (Int) -> Unit, onAlertDays: (Int) -> Unit, onSnooze: (Long) -> Unit, onMarkUsed: () -> Unit) {
    val now = System.currentTimeMillis()
    val dayMs = 24L*60*60*1000L
    val soonMs = ui.expiringSoonDays * dayMs
    val criticalMs = ui.criticalDays * dayMs
    val shelfLifeDays = container.shelfLifeDays ?: ui.defaultReminderDays
    val shelfExpiry = container.createdAt + shelfLifeDays * dayMs
    val reminderAt = container.reminderAt
    val remainingShelf = kotlin.math.ceil((shelfExpiry - now).toDouble()/dayMs.toDouble()).toLong()
    val shelfLabel = when {
        remainingShelf < 0 -> "Expired ${-remainingShelf}d"
        remainingShelf == 0L -> "Expires today"
        else -> "$remainingShelf d left"
    }
    val shelfRemainingMs = shelfExpiry - now
    val (alertLabel, color) = when {
        shelfRemainingMs < 0 -> rel(shelfRemainingMs) to Color(0xFFC62828)
        shelfRemainingMs <= criticalMs -> rel(shelfRemainingMs) to Color(0xFFC62828)
        shelfRemainingMs <= soonMs -> rel(shelfRemainingMs) to Color(0xFFF9A825)
        else -> rel(shelfRemainingMs) to Color(0xFF2E7D32)
    }
    var shelfInput by remember(container.shelfLifeDays) { mutableStateOf(container.shelfLifeDays?.toString() ?: "") }
    var alertInput by remember(reminderAt, container.reminderDays) { mutableStateOf(container.reminderDays?.toString() ?: "") }
    var alertHour by remember(reminderAt) { mutableStateOf(8) }
    var alertMinute by remember(reminderAt) { mutableStateOf(0) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(container.name.ifBlank { "Active Item" }) }, text = {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).background(color, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text("Alert: $alertLabel", style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(4.dp)); Text("Shelf life: $shelfLabel", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp)); Text("Scanned: ${formatFullDate(container.createdAt)}", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = shelfInput, onValueChange = { if (it.length<=3) shelfInput = it.filter(Char::isDigit) }, label = { Text("Shelf life days") }, singleLine = true)
            TextButton(enabled = shelfInput.toIntOrNull()!=null, onClick = { shelfInput.toIntOrNull()?.let(onShelfLife) }) { Text("Save Shelf Life") }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = alertInput, onValueChange = { if (it.length<=3) alertInput = it.filter(Char::isDigit) }, label = { Text("Alert in days") }, singleLine = true)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = "%02d".format(alertHour),
                    onValueChange = { v -> v.filter(Char::isDigit).take(2).toIntOrNull()?.let { if (it in 0..23) alertHour = it } },
                    label = { Text("HH") }, modifier = Modifier.width(80.dp), singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = "%02d".format(alertMinute),
                    onValueChange = { v -> v.filter(Char::isDigit).take(2).toIntOrNull()?.let { if (it in 0..59) alertMinute = it } },
                    label = { Text("MM") }, modifier = Modifier.width(80.dp), singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                Text("24h time", style = MaterialTheme.typography.labelSmall)
            }
            TextButton(enabled = alertInput.toIntOrNull()!=null, onClick = {
                alertInput.toIntOrNull()?.let { days ->
                    // Use new time-aware update via viewModel (indirect: onAlertDays keeps signature; just sets days)
                    onAlertDays(days)
                }
            }) { Text("Save Alert") }
            if (reminderAt != null) {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { onSnooze(reminderAt + 7L*dayMs) }) { Text("Snooze +7d") }
            }
        }
    }, confirmButton = {
        Row { TextButton(onClick = { onMarkUsed(); onDismiss() }) { Text("Mark Used") }; TextButton(onClick = onDismiss) { Text("Close") } }
    })
}

@Composable
private fun ReuseDialog(initialName: String, onDismiss: () -> Unit, onReuse: (String, Int?, Int?) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    var shelfLife by remember { mutableStateOf("") }
    var alert by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Reuse Label") }, text = {
        Column {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name (optional)") })
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = shelfLife, onValueChange = { if (it.length<=3) shelfLife = it.filter(Char::isDigit) }, label = { Text("Shelf life days (optional)") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = alert, onValueChange = { if (it.length<=3) alert = it.filter(Char::isDigit) }, label = { Text("Reminder alert in (days, optional)") }, singleLine = true)
            Spacer(Modifier.height(4.dp))
            Text("Set either or both now, or edit later.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = {
        TextButton(onClick = { onReuse(name, shelfLife.toIntOrNull(), alert.toIntOrNull()); onDismiss() }) { Text("Reuse") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val hasPermission = remember { mutableStateOf(
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    ) }
    var permissionRequestAttempts by remember { mutableStateOf(0) }
    // After each request attempt, poll briefly until system callback updates state.
    LaunchedEffect(permissionRequestAttempts) {
        if (permissionRequestAttempts > 0) {
            repeat(10) { // ~3s max
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        context, android.Manifest.permission.CAMERA
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    hasPermission.value = true
                    return@LaunchedEffect
                }
                delay(300)
            }
        }
    }
    var detected by remember { mutableStateOf<String?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var cameraRef by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    LaunchedEffect(detected) { detected?.let { onResult(it) } }
    if (!hasPermission.value) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Camera permission required to scan labels", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = {
                    if (context is android.app.Activity) {
                        permissionRequestAttempts++
                        context.requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 1002)
                    }
                }) { Text("Grant Permission") }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onClose) { Text("Close") }
            }
        }
        return
    }
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
                                        detected = uuid.take(120)
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
        val guideSize = 220.dp
        val frameColor = MaterialTheme.colorScheme.primary
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterUsedChip(show: Boolean, onToggle: () -> Unit) {
    AssistChip(onClick = onToggle, label = { Text(if (show) "Show Used" else "Hide Used") }, modifier = Modifier.testTag(UiTestTags.FilterArchivedChip))
}

@Composable
private fun ContainerList(items: List<Container>, expiringSoonDays: Int, criticalDays: Int, defaultDays: Int, onMarkUsed: (Long) -> Unit, onDelete: (Long) -> Unit, onLabel: (Container) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(8.dp).testTag(UiTestTags.ContainerList)) {
        items(items, key = { it.id }) { c ->
            val now = System.currentTimeMillis()
            val baseStatusColor = when (c.status) {
                Status.ACTIVE -> Color(0xFF2E7D32)
                Status.USED, Status.UNUSED -> Color(0xFF607D8B)
                Status.DELETED -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            }
            val soonMs = expiringSoonDays * 24L*60L*60L*1000L
            val criticalMs = criticalDays * 24L*60L*60L*1000L
            val statusColor = if (c.status == Status.ACTIVE) {
                val dayMs = 24L*60L*60L*1000L
                val shelfDays = c.shelfLifeDays ?: defaultDays
                val shelfRemainingMs = (c.createdAt + shelfDays * dayMs) - now
                when {
                    shelfRemainingMs < 0 -> Color(0xFFC62828)
                    shelfRemainingMs <= criticalMs -> Color(0xFFC62828)
                    shelfRemainingMs <= soonMs -> Color(0xFFF9A825)
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
                            Box(Modifier.size(14.dp).background(statusColor, CircleShape).testTag("StatusDot"))
                            Spacer(Modifier.width(8.dp))
                            Text(c.name.ifBlank { "(unnamed)" }, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                        }
                        Spacer(Modifier.height(2.dp))
                        Text("Qty: ${c.quantity}", style = MaterialTheme.typography.bodySmall)
                        val dayMs = 24L*60*60*1000L
                        val durationDays = c.shelfLifeDays ?: defaultDays
                        val targetAt = c.createdAt + durationDays * dayMs
                        val remaining = (targetAt - now) / dayMs
                        val remLabel = when {
                            remaining < 0 -> "Expired ${-remaining}d ago"
                            remaining == 0L -> "Expires today"
                            else -> "${remaining}d remaining"
                        }
                        Text(remLabel, style = MaterialTheme.typography.bodySmall)
                        c.reminderAt?.let { ra ->
                            val rd = (ra - now)/dayMs
                            val alertLabel = if (rd < 0) "Alert overdue" else if (rd==0L) "Alert today" else "Alert in ${rd}d"
                            Text(alertLabel, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("Scanned: ${formatFullDate(c.createdAt)}", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(2.dp))
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

private fun formatFullDate(epochMillis: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = epochMillis }
    val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
    val year = cal.get(java.util.Calendar.YEAR)
    val monthName = java.text.DateFormatSymbols().months[cal.get(java.util.Calendar.MONTH)]
    val suffix = when {
        day in 11..13 -> "th"
        day % 10 == 1 -> "st"
        day % 10 == 2 -> "nd"
        day % 10 == 3 -> "rd"
        else -> "th"
    }
    return "${day}${suffix} ${monthName} ${year}"
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

@Composable
private fun SettingsDialog(vm: ContainerViewModel, onClose: () -> Unit) {
    val ui by vm.state.collectAsState()
    var defaultDays by remember { mutableStateOf(ui.defaultReminderDays.toString()) }
    var soon by remember { mutableStateOf(ui.expiringSoonDays.toString()) }
    var critical by remember { mutableStateOf(ui.criticalDays.toString()) }
    AlertDialog(onDismissRequest = onClose, title = { Text("Settings") }, text = {
        Column {
            OutlinedTextField(value = defaultDays, onValueChange = { if (it.length<=3) defaultDays = it.filter(Char::isDigit) }, label = { Text("Default reminder days") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = soon, onValueChange = { if (it.length<=3) soon = it.filter(Char::isDigit) }, label = { Text("Expiring soon threshold") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = critical, onValueChange = { if (it.length<=3) critical = it.filter(Char::isDigit) }, label = { Text("Critical threshold") }, singleLine = true)
            Spacer(Modifier.height(4.dp))
            Text("Thresholds color the alert (not the shelf life) dot.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = {
        TextButton(onClick = {
            defaultDays.toIntOrNull()?.let { vm.setDefaultReminderDays(it) }
            soon.toIntOrNull()?.let { vm.setExpiringSoonDays(it) }
            critical.toIntOrNull()?.let { vm.setCriticalDays(it) }
            onClose()
        }) { Text("Save") }
    }, dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } })
}

@Composable
private fun LabelPreviewDialog(container: Container, defaultShelfLifeDays: Int, onDismiss: () -> Unit) {
    val shelfDays = container.shelfLifeDays ?: defaultShelfLifeDays
    val dayMs = 24L*60*60*1000L
    val expiry = container.createdAt + shelfDays * dayMs
    val remaining = (expiry - System.currentTimeMillis()) / dayMs
    val remLabel = when {
        remaining < 0 -> "expired ${-remaining}d ago"
        remaining == 0L -> "expires today"
        else -> "${remaining}d remaining"
    }
    val shelfLabel = "Shelf life: ${shelfDays}d (${remLabel})"
    val reminderLabel = container.reminderDays?.let { "Reminder: ${it}d" } ?: "Reminder: none"
    val qrMatrix = remember(container.uuid) { QrCodeGenerator.matrix(QR_PREFIX + container.uuid, 256) }
    val bmp = remember(qrMatrix) {
        android.graphics.Bitmap.createBitmap(qrMatrix.size, qrMatrix.size, android.graphics.Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until qrMatrix.size) for (x in 0 until qrMatrix.size) {
                setPixel(x, y, if (qrMatrix.get(x,y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            }
        }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(container.name.ifBlank { "Label" }) }, text = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(bmp.asImageBitmap(), contentDescription = "QR Code", modifier = Modifier.size(180.dp))
            Spacer(Modifier.height(8.dp))
            Text(shelfLabel, style = MaterialTheme.typography.bodySmall)
            Text(reminderLabel, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            Text("UUID: ${container.uuid}", style = MaterialTheme.typography.labelSmall)
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}
