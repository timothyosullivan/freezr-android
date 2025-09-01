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
import androidx.camera.view.PreviewView
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import java.util.concurrent.Executors
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.Alignment

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

    var labelTarget by remember { mutableStateOf<com.freezr.data.model.Container?>(null) }
    Scaffold(
        topBar = { TopBar(state, onSort = vm::setSort, onToggleArchived = { vm.setShowArchived(!state.showArchived) }, onScan = { showScan = true }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            AddFab { name -> vm.add(name) }
        }
    ) { padding ->
    Column(Modifier.padding(padding).testTag(UiTestTags.RootColumn)) {
            if (showScan) {
                ScanScreen(onClose = { showScan = false }, onResult = { uuid ->
                    showScan = false
                    scope.launch { snackbarHostState.showSnackbar("Scanned $uuid") }
                    // TODO integrate reuse/create flow
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
        labelTarget?.let { c ->
            AlertDialog(onDismissRequest = { labelTarget = null }, confirmButton = {
                TextButton(onClick = { labelTarget = null }) { Text("Close") }
            }, title = { Text("Label: ${c.name}") }, text = {
                val matrix = remember(c.uuid) { QrCodeGenerator.matrix(c.uuid, 256) }
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
    val context = androidx.compose.ui.platform.LocalContext.current
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
                            val labels = state.items.filter { it.status == Status.ACTIVE }.take(40).map { c ->
                                LabelPdfGenerator.Label(title = c.name.ifBlank { c.uuid.take(6) }, uuid = c.uuid)
                            }
                            if (labels.isNotEmpty()) {
                                val file = LabelPdfGenerator.generate(context, labels, "labels.pdf")
                                val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share labels PDF"))
                            }
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    var detected by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(detected) { detected?.let { onResult(it) } }
    Box(Modifier.fillMaxSize()) {
    AndroidView(factory = { ctx: android.content.Context ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            val executor = Executors.newSingleThreadExecutor()
            cameraProviderFuture.addListener({
                val provider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analyzer = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                val scanner = BarcodeScanning.getClient()
                analyzer.setAnalyzer(executor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val rotation = imageProxy.imageInfo.rotationDegrees
                        val image = InputImage.fromMediaImage(mediaImage, rotation)
                        scanner.process(image)
                            .addOnSuccessListener { list ->
                                list.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }?.rawValue?.let { value ->
                                    if (detected == null) detected = value
                                }
                            }
                            .addOnCompleteListener { imageProxy.close() }
                    } else imageProxy.close()
                }
                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analyzer)
                } catch (_: Exception) { }
            }, executor)
            previewView
        }, modifier = Modifier.fillMaxSize())
    Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.small, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose) { Text("Close") }
            }
        }
    }
}

@Composable
private fun FilterArchivedChip(show: Boolean, onToggle: () -> Unit) {
    AssistChip(onClick = onToggle, label = { Text(if (show) "Archived On" else "Archived Off") }, modifier = Modifier.testTag(UiTestTags.FilterArchivedChip))
}

@Composable
private fun AddFab(onAdd: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    if (open) {
        AlertDialog(onDismissRequest = { open = false }, confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onAdd(text.trim()); text = ""; open = false }, modifier = Modifier.testTag(UiTestTags.AddDialogConfirm)) { Text("Add") }
        }, dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } }, title = { Text("New Container") }, text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Name") }, modifier = Modifier.testTag(UiTestTags.AddDialogTextField))
        })
    }
    FloatingActionButton(onClick = { open = true }, modifier = Modifier.testTag(UiTestTags.FabAdd)) { Text("Add") }
}

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
