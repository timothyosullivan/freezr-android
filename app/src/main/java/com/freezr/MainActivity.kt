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
import com.freezr.data.model.*

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

    Scaffold(
        topBar = { TopBar(state, onSort = vm::setSort, onToggleArchived = { vm.setShowArchived(!state.showArchived) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            AddFab { name -> vm.add(name) }
        }
    ) { padding ->
    Column(Modifier.padding(padding).testTag(UiTestTags.RootColumn)) {
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
                }
            )
            BuildFooter()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(state: ContainerUiState, onSort: (SortOrder) -> Unit, onToggleArchived: () -> Unit) {
    TopAppBar(title = { Text("Freezr") }, actions = {
        SortMenu(current = state.sortOrder, onSelect = onSort)
        FilterArchivedChip(show = state.showArchived, onToggle = onToggleArchived)
    }, modifier = Modifier.testTag(UiTestTags.TopBar))
}

@Composable
private fun SortMenu(current: SortOrder, onSelect: (SortOrder) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.testTag(UiTestTags.SortMenuBox)) { 
        TextButton(onClick = { expanded = true }, modifier = Modifier.testTag(UiTestTags.SortMenuButton)) { Text(current.name) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortOrder.values().forEach { so ->
                DropdownMenuItem(text = { Text(so.name) }, onClick = { onSelect(so); expanded = false }, modifier = Modifier.testTag("SortOption_${'$'}{so.name}"))
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
private fun ContainerList(items: List<Container>, onArchive: (Long) -> Unit, onActivate: (Long) -> Unit, onDelete: (Long) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(8.dp).testTag(UiTestTags.ContainerList)) {
        items(items, key = { it.id }) { c ->
            ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("${'$'}{UiTestTags.ContainerCardPrefix}${'$'}{c.id}")) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(c.status.name, style = MaterialTheme.typography.labelSmall)
                    }
                    Row { 
                        when (c.status) {
                            Status.ACTIVE -> TextButton(onClick = { onArchive(c.id) }, modifier = Modifier.testTag(UiTestTags.ArchiveButton)) { Text("Archive") }
                            Status.ARCHIVED -> TextButton(onClick = { onActivate(c.id) }, modifier = Modifier.testTag(UiTestTags.ActivateButton)) { Text("Activate") }
                            Status.DELETED -> {}
                        }
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
