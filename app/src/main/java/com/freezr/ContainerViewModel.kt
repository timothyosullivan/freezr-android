package com.freezr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freezr.data.repository.ContainerRepository
import com.freezr.data.repository.SettingsRepository
import com.freezr.data.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ContainerViewModel @Inject constructor(
    private val containers: ContainerRepository,
    private val settingsRepo: SettingsRepository
) : ViewModel() {
    private val sortOrder = MutableStateFlow(SortOrder.CREATED_DESC)
    private val showArchived = MutableStateFlow(false)
    private val lastDeleted = MutableStateFlow<Container?>(null)

    val state: StateFlow<ContainerUiState> = combine(
        sortOrder, showArchived,
        settingsRepo.settings,
        ::Triple
    ).flatMapLatest { (sort, show, settings) ->
        containers.observe(show, sort).map { list ->
            ContainerUiState(list, sort, show)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ContainerUiState())

    fun add(name: String) = viewModelScope.launch { containers.add(name) }
    fun archive(id: Long) = viewModelScope.launch { containers.archive(id) }
    fun activate(id: Long) = viewModelScope.launch { containers.activate(id) }
    fun softDelete(id: Long) = viewModelScope.launch {
        state.value.items.firstOrNull { it.id == id }?.let { lastDeleted.value = it }
        containers.softDelete(id)
    }
    fun undoLastDelete() = viewModelScope.launch {
        lastDeleted.getAndUpdate { null }?.let { containers.add(it.name) }
    }
    fun setSort(sort: SortOrder) { sortOrder.value = sort }
    fun setShowArchived(show: Boolean) { showArchived.value = show }
}

data class ContainerUiState(
    val items: List<Container> = emptyList(),
    val sortOrder: SortOrder = SortOrder.CREATED_DESC,
    val showArchived: Boolean = false
)
