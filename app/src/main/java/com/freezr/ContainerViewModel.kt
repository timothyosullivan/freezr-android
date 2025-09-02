package com.freezr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freezr.data.model.Container
import com.freezr.data.model.Settings
import com.freezr.data.model.SortOrder
import com.freezr.data.model.Status
import com.freezr.data.repository.ContainerRepository
import com.freezr.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ContainerViewModel @Inject constructor(
    private val containers: ContainerRepository,
    private val settingsRepo: SettingsRepository,
    private val reminderScheduler: com.freezr.reminder.ReminderScheduler
) : ViewModel() {
    private val lastDeleted = MutableStateFlow<Container?>(null)
    private val _scanDialog = MutableStateFlow<ScanDialogState?>(null)
    val scanDialog: StateFlow<ScanDialogState?> = _scanDialog.asStateFlow()

    // Source of truth: persisted settings
    private val settings: StateFlow<Settings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    val state: StateFlow<ContainerUiState> = settings
        .flatMapLatest { s ->
            containers.observe(s.showArchived, s.sortOrder).map { list ->
                ContainerUiState(
                    items = list,
                    sortOrder = s.sortOrder,
                    showArchived = s.showArchived
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ContainerUiState())

    @Deprecated("Direct add disabled; create entries by scanning/claiming a label")
    fun add(name: String, quantity: Int = 1, reminderDays: Int? = null) = viewModelScope.launch { /* no-op now */ }
    fun archive(id: Long) = viewModelScope.launch {
        // Cancel any outstanding reminder when archiving
        reminderScheduler.cancel(id)
        containers.archive(id)
    }
    fun markUsed(id: Long) = viewModelScope.launch {
        reminderScheduler.cancel(id)
        containers.markUsed(id)
    }
    fun activate(id: Long) = viewModelScope.launch { containers.activate(id) }
    fun softDelete(id: Long) = viewModelScope.launch {
        // Cancel any reminder when deleting
        reminderScheduler.cancel(id)
        state.value.items.firstOrNull { it.id == id }?.let { lastDeleted.value = it }
        containers.softDelete(id)
    }
    fun reuse(id: Long, newName: String?) = viewModelScope.launch {
        // Reuse archives old container; cancel any reminder attached to old id
        reminderScheduler.cancel(id)
        containers.reuse(id, newName)
    }
    // Invoked when a scan detects a uuid; populates dialog state (no DB write yet)
    fun handleScan(uuid: String) = viewModelScope.launch {
        val existing = containers.findByUuid(uuid)
        _scanDialog.value = ScanDialogState(uuid = uuid, existing = existing)
    }

    fun dismissScanDialog() { _scanDialog.value = null }

    // (Removed earlier simpler createFromScan; unified version is below)

    fun reuseFromScan(newName: String?) = viewModelScope.launch {
        val current = _scanDialog.value ?: return@launch
        val existing = current.existing ?: return@launch
        // Cancel any reminder on the old container before reusing
        reminderScheduler.cancel(existing.id)
        val id = containers.reusePreserveUuid(existing.id, newName)
        if (id > 0) scheduleReminder(id, null)
        _scanDialog.value = null
    }

    // Placeholder generation removed from printing flow: labels are now purely physical until scanned.

    /** Claim an UNUSED placeholder scanned label */
    fun claimFromScan(name: String, reminderDays: Int? = null) = viewModelScope.launch {
        val current = _scanDialog.value ?: return@launch
        val existing = current.existing ?: return@launch
        if (existing.status != Status.UNUSED) return@launch
        val id = containers.claimPlaceholder(existing.uuid, name.trim(), quantity = 1, reminderDays = reminderDays)
        if (id > 0) scheduleReminder(id, reminderDays)
        _scanDialog.value = null
    }

    private fun scheduleReminder(id: Long, reminderDays: Int?) {
        val settings = settings.value
        val days = reminderDays ?: settings.defaultReminderDays
        val triggerAt = System.currentTimeMillis() + days * 24L * 60L * 60L * 1000L
        reminderScheduler.schedule(id, triggerAt)
    }
    // Removed generatePlaceholders() – labels for printing are now ephemeral and not inserted until scan claim.
    
    // Legacy path (may be removed when UI no longer calls it); only valid for UNKNOWN mode where we create new row from scanned uuid
    fun createFromScan(name: String, reminderDays: Int? = null) = viewModelScope.launch {
        val current = _scanDialog.value ?: return@launch
        val existing = current.existing
        when {
            existing == null -> {
                val id = containers.addFromScan(current.uuid, name = name)
                scheduleReminder(id, reminderDays)
            }
            existing.status == Status.UNUSED -> {
                val id = containers.claimPlaceholder(existing.uuid, name.trim(), quantity = 1, reminderDays = reminderDays)
                if (id > 0) scheduleReminder(id, reminderDays)
            }
            else -> { /* ignore; UI should use reuse */ }
        }
        _scanDialog.value = null
    }
    fun undoLastDelete() = viewModelScope.launch {
        lastDeleted.getAndUpdate { null }?.let { containers.add(it.name) }
    }
    fun setSort(sort: SortOrder) = viewModelScope.launch {
        val current = settings.value
        if (current.sortOrder != sort) settingsRepo.updateSort(sort, current)
    }
    fun setShowArchived(show: Boolean) = viewModelScope.launch {
        val current = settings.value
        if (current.showArchived != show) settingsRepo.updateShowArchived(show, current)
    }
}

data class ContainerUiState(
    val items: List<Container> = emptyList(),
    val sortOrder: SortOrder = SortOrder.CREATED_DESC,
    val showArchived: Boolean = false
)

data class ScanDialogState(val uuid: String, val existing: Container?)
{
    val mode: ScanMode = when {
        existing == null -> ScanMode.UNKNOWN
        existing.status == Status.UNUSED -> ScanMode.UNUSED
        existing.status == Status.ACTIVE -> ScanMode.ACTIVE
        else -> ScanMode.HISTORICAL
    }
}

enum class ScanMode { UNKNOWN, UNUSED, ACTIVE, HISTORICAL }

