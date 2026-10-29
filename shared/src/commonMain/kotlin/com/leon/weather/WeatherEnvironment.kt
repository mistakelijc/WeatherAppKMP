package com.leon.weather

import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import com.leon.weather.data.remote.OpenMeteoApi
import com.leon.weather.data.remote.weatherHttpClient
import com.leon.weather.data.repository.*
import com.leon.weather.time.WeatherClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okio.Path.Companion.toPath

/** One environment per application: a single client and DataStore owner. */
class WeatherEnvironment internal constructor(historyPath: String) {
    val clock: WeatherClock = WeatherClock.System
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val client = weatherHttpClient()
    private val api = OpenMeteoApi(client)
    private val dataStore = PreferenceDataStoreFactory.createWithPath(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = persistenceScope,
        produceFile = { historyPath.toPath() },
    )
    val locationRepository: LocationRepository = OpenMeteoLocationRepository(api)
    val weatherRepository: WeatherRepository = OpenMeteoWeatherRepository(api, clock)
    val historyRepository: SearchHistoryRepository = DataStoreSearchHistoryRepository(dataStore)

    fun close() {
        client.close()
        persistenceScope.cancel()
    }
}
