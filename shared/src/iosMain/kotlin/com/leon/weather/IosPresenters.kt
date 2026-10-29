package com.leon.weather

import com.leon.weather.model.Location
import com.leon.weather.model.PopularLocations
import com.leon.weather.presentation.*
import kotlinx.coroutines.*

/** Swift retains this handle and closes it before its ObservableObject is released. */
class StateSubscription internal constructor(private val job: Job) {
    fun close() = job.cancel()
}

/** Main-thread-only facade. The Swift callback must capture its owner weakly. */
class IosSearchPresenter {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val environment = createIosEnvironment()
    private val store = SearchStore(environment.locationRepository, environment.historyRepository, scope)
    val state: SearchViewData get() = store.uiState.value.toViewData()
    val popularLocations: List<Location> get() = PopularLocations.all
    fun watch(observer: (SearchViewData) -> Unit): StateSubscription = StateSubscription(
        scope.launch { store.uiState.collect { observer(it.toViewData()) } },
    )
    fun setQuery(query: String) = store.onQueryChanged(query)
    fun retry() = store.retry()
    fun select(location: Location) = store.onLocationSelected(location)
    fun removeHistory(id: Long) = store.removeHistory(id)
    fun clearHistory() = store.clearHistory()
    fun close() = scope.cancel()
}

class IosWeatherPresenter(val location: Location) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val environment = createIosEnvironment()
    private val store = WeatherStore(location, environment.weatherRepository, environment.clock, scope)
    val state: WeatherViewData get() = store.uiState.value.toViewData()
    fun watch(observer: (WeatherViewData) -> Unit): StateSubscription = StateSubscription(
        scope.launch { store.uiState.collect { observer(it.toViewData()) } },
    )
    fun refresh() = store.refresh()
    fun onForeground() = store.onForeground()
    fun close() = scope.cancel()
}
