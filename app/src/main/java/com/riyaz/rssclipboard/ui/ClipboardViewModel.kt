package com.riyaz.rssclipboard.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.riyaz.rssclipboard.data.ClipCategory
import com.riyaz.rssclipboard.data.ClipDatabase
import com.riyaz.rssclipboard.data.ClipEntity
import com.riyaz.rssclipboard.data.ContentDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class FilterTab(val label: String) {
    ALL("All"), PINNED("Pinned"), MOBILE("Mobile"), EMAIL("Email"), WEBSITE("Website"), PASSWORD("Password"), TEXT("Text")
}

class ClipboardViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = ClipDatabase.getInstance(application).clipDao()

    init {
        viewModelScope.launch {
            val cutoff = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
            dao.deleteExpiredUnpinned(cutoff)
        }
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(FilterTab.ALL)
    val selectedFilter: StateFlow<FilterTab> = _selectedFilter.asStateFlow()

    val clips: StateFlow<List<ClipEntity>> = combine(
        dao.observeAllClips(),
        _searchQuery,
        _selectedFilter
    ) { allClips, query, filter ->
        allClips.filter { clip ->
            val matchesFilter = when (filter) {
                FilterTab.ALL -> true
                FilterTab.PINNED -> clip.isPinned
                FilterTab.MOBILE -> clip.category == ClipCategory.PHONE
                FilterTab.EMAIL -> clip.category == ClipCategory.EMAIL
                FilterTab.WEBSITE -> clip.category == ClipCategory.URL
                FilterTab.PASSWORD -> clip.category == ClipCategory.SENSITIVE || clip.isSensitive
                FilterTab.TEXT -> clip.category == ClipCategory.TEXT
            }
            val matchesQuery = query.isBlank() || 
                clip.content.contains(query.trim(), ignoreCase = true) ||
                (clip.customName?.contains(query.trim(), ignoreCase = true) == true) ||
                clip.sourceApp.contains(query.trim(), ignoreCase = true)
            matchesFilter && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onSearchQueryChange(q: String) { _searchQuery.value = q }
    fun onFilterChange(f: FilterTab) { _selectedFilter.value = f }

    fun togglePin(clip: ClipEntity) {
        viewModelScope.launch { 
            val newPinned = !clip.isPinned
            dao.update(clip.copy(isPinned = newPinned, isManualSave = newPinned, customName = if (!newPinned) null else clip.customName)) 
        }
    }

    fun updateClipContent(clip: ClipEntity, newText: String, sensitive: Boolean, customName: String?) {
        val trimmed = newText.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val category = if (sensitive) ClipCategory.SENSITIVE else ContentDetector.detectCategory(trimmed)
            val trimmedName = customName?.trim()?.takeIf { it.isNotEmpty() }
            dao.update(
                clip.copy(
                    content = trimmed,
                    category = category,
                    isSensitive = sensitive,
                    customName = trimmedName,
                    isPinned = if (trimmedName != null) true else clip.isPinned,
                    isManualSave = if (trimmedName != null) true else clip.isManualSave
                )
            )
        }
    }

    fun deleteClip(clip: ClipEntity) { viewModelScope.launch { dao.delete(clip) } }
    fun clearUnpinned() { viewModelScope.launch { dao.deleteAllUnpinned() } }
}