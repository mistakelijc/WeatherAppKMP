package com.leon.weather.ui.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leon.weather.data.repository.WeatherRepository
import com.leon.weather.model.Location
import com.leon.weather.presentation.WeatherStore
import com.leon.weather.time.WeatherClock

class WeatherViewModel(val location: Location, repository: WeatherRepository, clock: WeatherClock = WeatherClock.System) : ViewModel() {
    private val store = WeatherStore(location, repository, clock, viewModelScope)
    val uiState = store.uiState
    fun refresh() = store.refresh()
    fun onForeground() = store.onForeground()
}
