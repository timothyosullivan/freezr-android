@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlin.OptIn
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
        val startIntent = intent
        setContent {
            LaunchedEffect(startIntent) { startIntent?.let { handleAppIntent(it) } }
            FreezrApp(vm)
        }
    }
    private fun handleAppIntent(intent: android.content.Intent) {
        android.util.Log.d("Freezr","handleAppIntent action=" + intent.action + " extras=" + intent.extras)
        if (intent.action == "com.freezr.ACTION_OPEN_CONTAINER") {
            val id = intent.getLongExtra("containerId", -1L)
            android.util.Log.d("Freezr","Opening container from intent id="+id)
            if (id > 0) vm.openScanDialogForId(id)
        }
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
    var guideOpen by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var labelPreview: Container? by remember { mutableStateOf(null) }
    var printing by remember { mutableStateOf(false) }
    var pendingPrintCount by remember { mutableStateOf<Int?>(null) }
    Scaffold(
        topBar = { TopBar(
            ui,
            onSort = vm::setSort,
            onToggleUsed = { vm.setShowUsed(!ui.showUsed) },
            onOpenSettings = { settingsOpen = true },
            onOpenGuide = { guideOpen = true },
            onOpenCoffee = {
                try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.buymeacoffee.com/zippee"))
                    context.startActivity(intent)
                } catch (_: Exception) {
                    scope.launch { snackbarHostState.showSnackbar("Couldn't open browser") }
                }
            },
            onPrint = { printing = true }
        ) },
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
                            snackbarHostState.currentSnackbarData?.dismiss()
                            val res = snackbarHostState.showSnackbar("Deleted", actionLabel = "Undo", withDismissAction = true, duration = SnackbarDuration.Short)
                            if (res == SnackbarResult.ActionPerformed) vm.undoLastDelete()
                        }
                    },
                    onLabel = { c -> labelPreview = c }
                )
            }
            BuildFooter()
        }
    if (settingsOpen) SettingsDialog(vm = vm, onClose = { settingsOpen = false })
    if (guideOpen) UsageGuideDialog(onClose = { guideOpen = false })
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
                ScanMode.UNKNOWN -> ClaimDialog(uuid = sd.uuid, initialName = "", onDismiss = vm::dismissScanDialog) { name, shelf, alert, timeMinutes ->
                    vm.createFromScan(name, reminderDays = alert, shelfLifeDays = shelf, reminderTimeMinutes = timeMinutes)
                    scope.launch { snackbarHostState.showSnackbar("Created from scan") }
                }
                ScanMode.UNUSED -> ClaimDialog(uuid = sd.uuid, initialName = existing?.name ?: "", onDismiss = vm::dismissScanDialog) { name, shelf, alert, timeMinutes ->
                    vm.claimFromScan(name, shelfLifeDays = shelf, reminderDays = alert, reminderTimeMinutes = timeMinutes)
                    scope.launch { snackbarHostState.showSnackbar("Label claimed") }
                }
                ScanMode.ACTIVE -> ActiveDialog(
                    container = existing!!,
                    ui = ui,
                    onDismiss = vm::dismissScanDialog,
                    onShelfLife = { d -> vm.updateShelfLifeDays(existing.id, d) },
                    onReminderDateTime = { dateMidnight, hh, mm -> vm.updateReminderExplicit(existing.id, dateMidnight, hh, mm) },
                    onSnooze = { at -> vm.updateReminderAt(existing.id, at) },
                    onMarkUsed = { vm.markUsed(existing.id) }
                )
                ScanMode.HISTORICAL -> ClaimDialog(uuid = sd.uuid, initialName = existing?.name ?: "", onDismiss = vm::dismissScanDialog) { name, shelf, alert, timeMinutes ->
                    vm.reuseFromScan(name.ifBlank { null }, reminderDays = alert, shelfLifeDays = shelf, reminderTimeMinutes = timeMinutes)
                    scope.launch { snackbarHostState.showSnackbar("Reused label") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    ui: ContainerUiState,
    onSort: (SortOrder) -> Unit,
    onToggleUsed: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenCoffee: () -> Unit,
    onPrint: () -> Unit
) {
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
                    DropdownMenuItem(
                        text = { Text("Usage Guide") },
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                        onClick = { menu = false; onOpenGuide() }
                    )
                    DropdownMenuItem(
                        text = { Text("Buy Me A Coffee") },
                        leadingIcon = { Icon(Icons.Default.LocalCafe, contentDescription = null) },
                        onClick = { menu = false; onOpenCoffee() }
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
private fun ClaimDialog(uuid: String, initialName: String, onDismiss: () -> Unit, onSave: (String, Int?, Int?, Int) -> Unit) {
    var name by remember(uuid) { mutableStateOf(initialName) }
    var shelfLife by remember { mutableStateOf("") }
    var selectedDateMidnight by remember { mutableStateOf<Long?>(null) }
    var hour by remember { mutableStateOf(8) }
    var minute by remember { mutableStateOf(0) }
    var showDateTime by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Claim Label") }, text = {
        Column {
            Text("UUID: $uuid", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Description") })
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = shelfLife, onValueChange = { if (it.length<=3) shelfLife = it.filter(Char::isDigit) }, label = { Text("Shelf life days (optional)") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showDateTime = true }) { Text(selectedDateMidnight?.let { formatShortDate(it) + " %02d:%02d".format(hour, minute) } ?: "Pick reminder date & time (optional)") }
                if (selectedDateMidnight != null) TextButton(onClick = { selectedDateMidnight = null }) { Text("Clear") }
            }
            Spacer(Modifier.height(4.dp))
            Text("Choose a specific calendar date + time for reminder.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = {
        val shelf = shelfLife.toIntOrNull()
        val alertDays = selectedDateMidnight?.let { millisToDayDiff(it) }
        TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), shelf, alertDays, hour*60 + minute); onDismiss() }) { Text("Save") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
    if (showDateTime) {
        DateTimePickerDialog(initialDateMidnight = selectedDateMidnight, initialHour = hour, initialMinute = minute, onDismiss = { showDateTime = false }) { d, h, m ->
            selectedDateMidnight = d; hour = h; minute = m; showDateTime = false
        }
    }
}

@Composable
private fun ActiveDialog(
    container: Container,
    ui: ContainerUiState,
    onDismiss: () -> Unit,
    onShelfLife: (Int) -> Unit,
    onReminderDateTime: (Long, Int, Int) -> Unit,
    onSnooze: (Long) -> Unit,
    onMarkUsed: () -> Unit
) {
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
    val existingCal = remember(reminderAt) { reminderAt?.let { java.util.Calendar.getInstance().apply { timeInMillis = it } } }
    var selectedDateMidnight by remember(reminderAt) {
        mutableStateOf(existingCal?.clone()?.let { (it as java.util.Calendar).apply {
            set(java.util.Calendar.HOUR_OF_DAY,0); set(java.util.Calendar.MINUTE,0); set(java.util.Calendar.SECOND,0); set(java.util.Calendar.MILLISECOND,0)
        }.timeInMillis })
    }
    var hour by remember(reminderAt) { mutableStateOf(existingCal?.get(java.util.Calendar.HOUR_OF_DAY) ?: 8) }
    var minute by remember(reminderAt) { mutableStateOf(existingCal?.get(java.util.Calendar.MINUTE) ?: 0) }
    var showDateTime by remember { mutableStateOf(false) }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showDateTime = true }) { Text(selectedDateMidnight?.let { formatShortDate(it) + " %02d:%02d".format(hour, minute) } ?: "Pick reminder date & time") }
                if (selectedDateMidnight != null) TextButton(onClick = { selectedDateMidnight = null }) { Text("Clear") }
            }
            // Snooze removed per request
            if (showDateTime) {
                DateTimePickerDialog(initialDateMidnight = selectedDateMidnight, initialHour = hour, initialMinute = minute, onDismiss = { showDateTime = false }) { d, h, m ->
                    selectedDateMidnight = d; hour = h; minute = m; showDateTime = false
                }
            }
        }
    }, confirmButton = {
    TextButton(onClick = { selectedDateMidnight?.let { onReminderDateTime(it, hour, minute) }; onDismiss() }) { Text("Save") }
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
                            val cal = java.util.Calendar.getInstance().apply { timeInMillis = ra }
                            val dateStr = formatShortDate(ra)
                            val hh = cal.get(java.util.Calendar.HOUR_OF_DAY)
                            val mm = cal.get(java.util.Calendar.MINUTE)
                            val timeStr = "%02d:%02d".format(hh, mm)
                            val label = if (ra < now) "Reminder passed $dateStr $timeStr" else "Reminder: $dateStr $timeStr"
                            Text(label, style = MaterialTheme.typography.bodySmall)
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
                        TextButton(onClick = { onLabel(c) }) { Text("Details") }
                        when (c.status) {
                            Status.UNUSED -> {}
                            Status.ACTIVE -> TextButton(onClick = { onMarkUsed(c.id) }) { Text("Used") }
                            Status.USED -> {}
                            Status.DELETED -> {}
                        }
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

private fun formatShortDate(epochMillis: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = epochMillis }
    val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
    val month = cal.getDisplayName(java.util.Calendar.MONTH, java.util.Calendar.SHORT, java.util.Locale.getDefault()) ?: ""
    return "$day $month"
}

private fun millisToDayDiff(targetMidnight: Long): Int {
    val nowCal = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE,0); set(java.util.Calendar.SECOND,0); set(java.util.Calendar.MILLISECOND,0)
    }
    val diff = targetMidnight - nowCal.timeInMillis
    val dayMs = 24L*60*60*1000L
    return if (diff <= 0) 0 else (diff / dayMs).toInt()
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
            OutlinedTextField(value = defaultDays, onValueChange = { if (it.length<=3) defaultDays = it.filter(Char::isDigit) }, label = { Text("Default shelf life (days)") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = soon, onValueChange = { if (it.length<=3) soon = it.filter(Char::isDigit) }, label = { Text("Expiring soon threshold (days)") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = critical, onValueChange = { if (it.length<=3) critical = it.filter(Char::isDigit) }, label = { Text("Critical threshold (days)") }, singleLine = true)
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
private fun UsageGuideDialog(onClose: () -> Unit) {
    val scrollState = rememberScrollState()
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Usage Guide") },
        text = {
            Column(Modifier.verticalScroll(scrollState).fillMaxWidth()) {
                Text(
                    "Welcome to Freezr!",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "The app that loves your freezer & loves your food",
                        fontWeight = FontWeight.Bold,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = Color(0xFF2E7D32), // Green
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("Core Concepts", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Bullet("Print blank QR labels, then scan to 'claim' them when you actually store an item.")
                Bullet("Each label represents a container or frozen item. Scanning pulls up its current state.")
                Bullet("You can set a shelf life (how long it's good) and an optional reminder (specific date + time).")
                Spacer(Modifier.height(8.dp))
                Text("Workflow", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Bullet("1. Tap Scan (FAB) to open the camera.")
                Bullet("2. Point at a printed Freezr QR. If it's unused you'll Claim it; if it was used before you'll either view Active details or Reuse it.")
                Bullet("3. During Claim you can: set a description, optional shelf life days, and pick a calendar date & time for a reminder.")
                Bullet("4. Active items show status color based on time left vs thresholds (Settings lets you adjust 'expiring soon' & 'critical').")
                Bullet("5. 'Details' shows the QR, shelf info, and lets you change the reminder date/time.")
                Bullet("6. Mark an item Used when consumed. It stays in history (if Show Used toggled on).")
                Spacer(Modifier.height(8.dp))
                Text("Reminders", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Bullet("Reminders are scheduled at an exact date & time you pick.")
                Bullet("Notification opens directly to the item's dialog so you can update or mark it 'used'.")
                Spacer(Modifier.height(8.dp))
                Text("Shelf Life vs Reminder", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Bullet("Shelf life: total expected freshness window (drives colored status dot).")
                Bullet("Reminder: a separate early (or exact) nudge you choose. It does not change shelf life.")
                Spacer(Modifier.height(8.dp))
                Text("Status Colors", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Bullet("Green: comfortably within shelf life.")
                Bullet("Amber: within 'expiring soon' threshold.")
                Bullet("Red: within 'critical' threshold or already past shelf life.")
                Spacer(Modifier.height(8.dp))
                Text("Printing Labels", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Bullet("Open menu > Print to generate a PDF of blank QR codes. Cut & affix to containers.")
                Bullet("These start as UNUSED until first claim after scanning.")
                Spacer(Modifier.height(8.dp))
                Text("Sorting & Filtering", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Bullet("Use the sort menu to switch between Newest/Oldest or Name A→Z/Z→A.")
                Bullet("Toggle 'Show Used' to reveal history. Deleted items are hidden after undo window.")
                Spacer(Modifier.height(8.dp))
                Text("Tips", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Bullet("Set shelf life once; tweak reminders as plans change.")
                Bullet("Use 'Details' for quick reminder adjustments without re‑scanning.")
                Spacer(Modifier.height(12.dp))
                Text("You're set! Scan your first label or print some to get started.")
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } }
    )
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text("•", modifier = Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
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
    val reminderLabel = container.reminderAt?.let { at ->
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = at }
        val hh = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val mm = cal.get(java.util.Calendar.MINUTE)
        val timeStr = "%02d:%02d".format(hh, mm)
        container.reminderDays?.let { d -> "Reminder: ${d}d @ ${timeStr}" } ?: "Reminder: @ ${timeStr}"
    } ?: (container.reminderDays?.let { d -> "Reminder: ${d}d" } ?: "Reminder: none")
    val qrMatrix = remember(container.uuid) { QrCodeGenerator.matrix(QR_PREFIX + container.uuid, 256) }
    val bmp = remember(qrMatrix) {
        android.graphics.Bitmap.createBitmap(qrMatrix.size, qrMatrix.size, android.graphics.Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until qrMatrix.size) for (x in 0 until qrMatrix.size) {
                setPixel(x, y, if (qrMatrix.get(x,y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            }
        }
    }
    var showDateTime by remember { mutableStateOf(false) }
    var hour by remember(container.reminderAt) { mutableStateOf(container.reminderAt?.let { java.util.Calendar.getInstance().apply { timeInMillis = it }.get(java.util.Calendar.HOUR_OF_DAY) } ?: 8) }
    var minute by remember(container.reminderAt) { mutableStateOf(container.reminderAt?.let { java.util.Calendar.getInstance().apply { timeInMillis = it }.get(java.util.Calendar.MINUTE) } ?: 0) }
    var selectedDateMidnight by remember(container.reminderAt) {
        mutableStateOf(container.reminderAt?.let {
            java.util.Calendar.getInstance().apply { timeInMillis = it; set(java.util.Calendar.HOUR_OF_DAY,0); set(java.util.Calendar.MINUTE,0); set(java.util.Calendar.SECOND,0); set(java.util.Calendar.MILLISECOND,0) }.timeInMillis
        })
    }
    val owner = androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner.current
    val vm: ContainerViewModel? = owner?.let { androidx.lifecycle.viewmodel.compose.viewModel(it) }
    AlertDialog(onDismissRequest = { android.util.Log.d("Freezr","Details dialog dismissed"); onDismiss() }, title = { Text(container.name.ifBlank { "Details" }) }, text = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(bmp.asImageBitmap(), contentDescription = "QR Code", modifier = Modifier.size(180.dp))
            Spacer(Modifier.height(8.dp))
            Text(shelfLabel, style = MaterialTheme.typography.bodySmall)
            Text(reminderLabel, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { showDateTime = true }) { Text(selectedDateMidnight?.let { "Change reminder (${formatShortDate(it)} %02d:%02d".format(hour, minute) + ")" } ?: "Set reminder date & time") }
            Text("UUID: ${container.uuid}", style = MaterialTheme.typography.labelSmall)
        }
    }, confirmButton = { TextButton(onClick = { android.util.Log.d("Freezr","Details dialog Close clicked"); onDismiss() }) { Text("Close") } })
    if (showDateTime) {
        DateTimePickerDialog(initialDateMidnight = selectedDateMidnight, initialHour = hour, initialMinute = minute, onDismiss = { showDateTime = false }) { d, h, m ->
            selectedDateMidnight = d; hour = h; minute = m; showDateTime = false
            vm?.updateReminderExplicit(container.id, d, h, m)
        }
    }
}

@Composable
private fun DateTimePickerDialog(
    initialDateMidnight: Long? = null,
    initialHour: Int = 8,
    initialMinute: Int = 0,
    onDismiss: () -> Unit,
    onConfirm: (dateMidnight: Long, hour: Int, minute: Int) -> Unit
) {
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialDateMidnight)
    var hour by remember { mutableStateOf(initialHour) }
    var minute by remember { mutableStateOf(initialMinute) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Date & Time") },
        text = {
            Column {
                DatePicker(state = datePickerState)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    var hourExpanded by remember { mutableStateOf(false) }
                    var minExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = hourExpanded, onExpandedChange = { hourExpanded = !hourExpanded }) {
                        OutlinedTextField(value = "%02d".format(hour), onValueChange = {}, readOnly = true, label = { Text("Hour") }, modifier = Modifier.menuAnchor().width(90.dp))
                        ExposedDropdownMenu(expanded = hourExpanded, onDismissRequest = { hourExpanded = false }) {
                            (0..23).forEach { h -> DropdownMenuItem(text = { Text("%02d".format(h)) }, onClick = { hour = h; hourExpanded = false }) }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    ExposedDropdownMenuBox(expanded = minExpanded, onExpandedChange = { minExpanded = !minExpanded }) {
                        OutlinedTextField(value = "%02d".format(minute), onValueChange = {}, readOnly = true, label = { Text("Min") }, modifier = Modifier.menuAnchor().width(90.dp))
                        ExposedDropdownMenu(expanded = minExpanded, onDismissRequest = { minExpanded = false }) {
                            listOf(0,5,10,15,20,25,30,35,40,45,50,55).forEach { m -> DropdownMenuItem(text = { Text("%02d".format(m)) }, onClick = { minute = m; minExpanded = false }) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = datePickerState.selectedDateMillis != null, onClick = {
                val raw = datePickerState.selectedDateMillis ?: return@TextButton
                val cal = java.util.Calendar.getInstance().apply {
                    timeInMillis = raw
                    set(java.util.Calendar.HOUR_OF_DAY,0); set(java.util.Calendar.MINUTE,0); set(java.util.Calendar.SECOND,0); set(java.util.Calendar.MILLISECOND,0)
                }
                onConfirm(cal.timeInMillis, hour, minute)
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
