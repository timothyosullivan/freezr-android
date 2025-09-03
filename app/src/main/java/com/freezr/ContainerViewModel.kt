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

    private val _filter = MutableStateFlow(ReminderFilter.NONE)
    val state: StateFlow<ContainerUiState> = settings
        .flatMapLatest { s ->
        containers.observe(s.showUsed, s.sortOrder).map { list ->
                val filtered = applyReminderFilter(list, _filter.value)
                ContainerUiState(
                    items = filtered,
                    sortOrder = s.sortOrder,
                    showUsed = s.showUsed,
                    filter = _filter.value,
                    defaultReminderDays = s.defaultReminderDays,
                    expiringSoonDays = s.expiringSoonDays,
                    criticalDays = s.criticalDays
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ContainerUiState())

    @Deprecated("Direct add disabled; create entries by scanning/claiming a label")
    fun add(name: String, quantity: Int = 1, reminderDays: Int? = null) = viewModelScope.launch { /* no-op now */ }
    // archive removed
    fun markUsed(id: Long) = viewModelScope.launch {
        reminderScheduler.cancel(id)
        containers.markUsed(id)
    }
    // activate removed
    fun softDelete(id: Long) = viewModelScope.launch {
        // Cancel any reminder when deleting
        reminderScheduler.cancel(id)
        state.value.items.firstOrNull { it.id == id }?.let { lastDeleted.value = it }
        containers.softDelete(id)
    }
    fun reuse(id: Long, newName: String?) = viewModelScope.launch {
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

    fun reuseFromScan(newName: String?, reminderDays: Int? = null, shelfLifeDays: Int? = null) = viewModelScope.launch {
        val current = _scanDialog.value ?: return@launch
        val existing = current.existing ?: return@launch
        // Cancel any reminder on the old container before reusing
        reminderScheduler.cancel(existing.id)
        val id = containers.reusePreserveUuid(existing.id, newName)
        if (id > 0) {
            if (shelfLifeDays != null) containers.updateShelfLifeDays(id, shelfLifeDays)
            // If custom days provided, persist them and schedule; else use default.
            if (reminderDays != null) {
                containers.updateReminderDays(id, reminderDays)
                val triggerAt = System.currentTimeMillis() + reminderDays * 24L * 60L * 60L * 1000L
                reminderScheduler.schedule(id, triggerAt)
            } else {
                scheduleReminder(id, null)
            }
        }
        _scanDialog.value = null
    }

    // Placeholder generation removed from printing flow: labels are now purely physical until scanned.

    /** Claim an UNUSED placeholder scanned label */
    fun claimFromScan(name: String, shelfLifeDays: Int? = null, reminderDays: Int? = null) = viewModelScope.launch {
        val current = _scanDialog.value ?: return@launch
        val existing = current.existing ?: return@launch
        if (existing.status != Status.UNUSED) return@launch
        val id = containers.claimPlaceholder(existing.uuid, name.trim(), quantity = 1, shelfLifeDays = shelfLifeDays, reminderDays = reminderDays)
        if (id > 0 && reminderDays != null) scheduleReminder(id, reminderDays) // only schedule if user specified
        _scanDialog.value = null
    }

    private fun scheduleReminder(id: Long, reminderDays: Int?, timeOfDayMinutes: Int = DEFAULT_ALERT_TIME_MINUTES) {
        val settings = settings.value
        val days = reminderDays ?: settings.defaultReminderDays
        val triggerAt = computeTriggerAt(days, timeOfDayMinutes)
        reminderScheduler.schedule(id, triggerAt)
    }

    private fun computeTriggerAt(days: Int, timeOfDayMinutes: Int): Long {
        val cal = java.util.Calendar.getInstance()
        // Normalize to today 00:00 then add target days + set desired time
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        cal.add(java.util.Calendar.DAY_OF_YEAR, days)
        cal.set(java.util.Calendar.HOUR_OF_DAY, timeOfDayMinutes / 60)
        cal.set(java.util.Calendar.MINUTE, timeOfDayMinutes % 60)
        return cal.timeInMillis
    }

    fun updateReminderDaysWithTime(id: Long, days: Int, hour: Int, minute: Int) = viewModelScope.launch {
        containers.updateReminderDays(id, days) // persists days + recalculates reminderAt (we'll overwrite with anchored time)
        val triggerAt = computeTriggerAt(days, hour*60 + minute)
        reminderScheduler.schedule(id, triggerAt)
        containers.updateReminderAt(id, triggerAt)
    }
    // Removed generatePlaceholders() – labels for printing are now ephemeral and not inserted until scan claim.
    
    // Legacy path (may be removed when UI no longer calls it); only valid for UNKNOWN mode where we create new row from scanned uuid
    fun createFromScan(name: String, reminderDays: Int? = null, shelfLifeDays: Int? = null) = viewModelScope.launch {
        val current = _scanDialog.value ?: return@launch
        val existing = current.existing
        when {
            existing == null -> {
                val id = containers.addFromScan(current.uuid, name = name)
                // If user supplied a shelf life for a brand new scan, persist it (was previously ignored – bug fix)
                if (shelfLifeDays != null) containers.updateShelfLifeDays(id, shelfLifeDays)
                if (reminderDays != null) scheduleReminder(id, reminderDays)
            }
            existing.status == Status.UNUSED -> {
                val id = containers.claimPlaceholder(existing.uuid, name.trim(), quantity = 1, shelfLifeDays = shelfLifeDays, reminderDays = reminderDays)
                if (id > 0 && reminderDays != null) scheduleReminder(id, reminderDays)
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
    fun setShowUsed(show: Boolean) = viewModelScope.launch {
        val current = settings.value
        if (current.showUsed != show) settingsRepo.updateShowUsed(show, current)
    }
    fun setReminderFilter(filter: ReminderFilter) { _filter.value = filter }
    fun setDefaultReminderDays(days: Int) = viewModelScope.launch {
        val current = settings.value
        if (current.defaultReminderDays != days) settingsRepo.updateDefaultReminderDays(days, current)
    }
    fun setExpiringSoonDays(days: Int) = viewModelScope.launch {
        val current = settings.value
        if (current.expiringSoonDays != days) settingsRepo.updateExpiringSoonDays(days, current)
    }
    fun setCriticalDays(days: Int) = viewModelScope.launch {
        val current = settings.value
        if (current.criticalDays != days) settingsRepo.updateCriticalDays(days, current)
    }

    private fun applyReminderFilter(list: List<Container>, filter: ReminderFilter): List<Container> {
        if (filter == ReminderFilter.NONE) return list
        val now = System.currentTimeMillis()
        val settings = settings.value
        val soonWindowMs = settings.expiringSoonDays * 24L * 60L * 60L * 1000L
        return when (filter) {
            ReminderFilter.EXPIRING_SOON -> list.filter { it.status == Status.ACTIVE && it.reminderAt != null && it.reminderAt > now && it.reminderAt - now <= soonWindowMs }
            ReminderFilter.EXPIRED -> list.filter { it.status == Status.ACTIVE && it.reminderAt != null && it.reminderAt <= now }
            ReminderFilter.NONE -> list
        }
    }

    fun snooze(id: Long, days: Int = 7) = viewModelScope.launch { containers.snooze(id, days) }
    fun updateReminderDays(id: Long, days: Int) = viewModelScope.launch {
        containers.updateReminderDays(id, days)
        val triggerAt = computeTriggerAt(days, DEFAULT_ALERT_TIME_MINUTES)
        reminderScheduler.schedule(id, triggerAt)
        containers.updateReminderAt(id, triggerAt)
    }
    fun updateReminderAt(id: Long, at: Long) = viewModelScope.launch { containers.updateReminderAt(id, at); reminderScheduler.schedule(id, at) }
    fun updateShelfLifeDays(id: Long, days: Int) = viewModelScope.launch { containers.updateShelfLifeDays(id, days) }
}

private const val DEFAULT_ALERT_TIME_MINUTES = 8 * 60

data class ContainerUiState(
    val items: List<Container> = emptyList(),
    val sortOrder: SortOrder = SortOrder.CREATED_DESC,
    val showUsed: Boolean = false,
    val filter: ReminderFilter = ReminderFilter.NONE,
    val defaultReminderDays: Int = 60,
    val expiringSoonDays: Int = 7,
    val criticalDays: Int = 2
)

data class ScanDialogState(val uuid: String, val existing: Container?)
{
    val mode: ScanMode = when {
        existing == null -> ScanMode.UNKNOWN
        existing.status == Status.UNUSED -> ScanMode.UNUSED
    existing.status == Status.ACTIVE -> ScanMode.ACTIVE
    existing.status == Status.USED -> ScanMode.HISTORICAL
    else -> ScanMode.HISTORICAL
    }
}

enum class ScanMode { UNKNOWN, UNUSED, ACTIVE, HISTORICAL }

enum class ReminderFilter { NONE, EXPIRING_SOON, EXPIRED }

