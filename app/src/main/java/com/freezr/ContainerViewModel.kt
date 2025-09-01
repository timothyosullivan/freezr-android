package com.freezr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freezr.data.repository.ContainerRepository
import com.freezr.data.repository.SettingsRepository
import com.freezr.data.model.*
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

    fun add(name: String, quantity: Int = 1, reminderDays: Int? = null) = viewModelScope.launch {
        val id = containers.add(name = name, quantity = quantity, reminderDays = reminderDays)
        // Schedule reminder (placeholder logic): compute target days
        val settings = settings.value
        val days = reminderDays ?: settings.defaultReminderDays
        val triggerAt = System.currentTimeMillis() + days * 24L * 60L * 60L * 1000L
        reminderScheduler.schedule(id, triggerAt)
    }
    fun archive(id: Long) = viewModelScope.launch { containers.archive(id) }
    fun activate(id: Long) = viewModelScope.launch { containers.activate(id) }
    fun softDelete(id: Long) = viewModelScope.launch {
        state.value.items.firstOrNull { it.id == id }?.let { lastDeleted.value = it }
        containers.softDelete(id)
    }
    fun reuse(id: Long, newName: String?) = viewModelScope.launch {
        containers.reuse(id, newName)
    }
    fun scanAndAdd(uuid: String, name: String) = add(name) // placeholder mapping
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
