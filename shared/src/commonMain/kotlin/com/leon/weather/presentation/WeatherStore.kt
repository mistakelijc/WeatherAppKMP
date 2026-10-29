package com.leon.weather.presentation

import kotlinx.coroutines.CoroutineScope
import com.leon.weather.data.repository.AppError
import com.leon.weather.data.repository.AppResult
import com.leon.weather.data.repository.WeatherRepository
import com.leon.weather.model.Location
import com.leon.weather.model.WeatherSnapshot
import com.leon.weather.time.WeatherClock
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.Instant
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface WeatherUiState {
    data object Loading : WeatherUiState
    data class Error(val error: AppError) : WeatherUiState
    data class Content(
        val data: WeatherSnapshot,
        val isRefreshing: Boolean = false,
        val refreshError: AppError? = null,
        val referenceTime: Instant = data.fetchedAt,
    ) : WeatherUiState
}

class WeatherStore(
    val location: Location,
    private val repository: WeatherRepository,
    private val clock: WeatherClock = WeatherClock.System,
    private val scope: CoroutineScope,
) {
    private val _uiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val uiState = _uiState.asStateFlow()
    private var job: Job? = null

    init { refresh() }

    fun onForeground() {
        val content = _uiState.value as? WeatherUiState.Content ?: return
        val now = clock.instant()
        _uiState.value = content.copy(referenceTime = now)
        val data = content.data
        val crossedLocalDate = now.toLocalDateTime(data.timeZone).date != data.fetchedAt.toLocalDateTime(data.timeZone).date
        if (now < data.fetchedAt || (now - data.fetchedAt) >= REFRESH_INTERVAL || crossedLocalDate) {
            refresh()
        }
    }

    fun refresh() {
        if (job?.isActive == true) return
        val previous = (_uiState.value as? WeatherUiState.Content)?.data
        _uiState.value = previous?.let { WeatherUiState.Content(it, isRefreshing = true, referenceTime = clock.instant()) } ?: WeatherUiState.Loading
        job = scope.launch {
            _uiState.value = when (val result = repository.getWeather(location)) {
                is AppResult.Success -> WeatherUiState.Content(result.value, referenceTime = clock.instant())
                is AppResult.Failure -> previous?.let { WeatherUiState.Content(it, refreshError = result.error, referenceTime = clock.instant()) }
                    ?: WeatherUiState.Error(result.error)
            }
        }
    }

    private companion object { val REFRESH_INTERVAL = 15.minutes }
}
