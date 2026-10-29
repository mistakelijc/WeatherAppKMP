package com.leon.weather.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leon.weather.data.repository.LocationRepository
import com.leon.weather.data.repository.SearchHistoryRepository
import com.leon.weather.model.Location
import com.leon.weather.presentation.SearchStore

class SearchViewModel(
    repository: LocationRepository,
    private val savedStateHandle: SavedStateHandle,
    historyRepository: SearchHistoryRepository,
) : ViewModel() {
    private val store = SearchStore(repository, historyRepository, viewModelScope, savedStateHandle[QUERY] ?: "")
    val uiState = store.uiState
    fun onQueryChanged(query: String) { savedStateHandle[QUERY] = query; store.onQueryChanged(query) }
    fun retry() = store.retry()
    fun onLocationSelected(location: Location) { savedStateHandle[QUERY] = ""; store.onLocationSelected(location) }
    fun removeHistory(id: Long) = store.removeHistory(id)
    fun clearHistory() = store.clearHistory()
    companion object { private const val QUERY = "search_query" }
}
