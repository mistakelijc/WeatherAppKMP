package com.leon.weather

import com.leon.weather.model.CurrentWeather
import com.leon.weather.model.PopularLocations
import com.leon.weather.model.WeatherCondition
import com.leon.weather.model.WeatherSnapshot
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import com.leon.weather.data.repository.SearchHistoryRepository
import com.leon.weather.model.Location
import kotlinx.coroutines.flow.MutableStateFlow
import com.leon.weather.time.WeatherClock

class MutableClock(var now: Instant) : WeatherClock {
    override fun instant(): Instant = now
}

class MemorySearchHistory : SearchHistoryRepository {
    override val locations = MutableStateFlow<List<Location>>(emptyList())
    override suspend fun record(location: Location) {
        locations.value = (listOf(location) + locations.value).distinctBy(Location::id).take(5)
    }
    override suspend fun remove(locationId: Long) {
        locations.value = locations.value.filterNot { it.id == locationId }
    }
    override suspend fun clear() { locations.value = emptyList() }
}

fun sampleWeather() = WeatherSnapshot(
    PopularLocations.all.first(), TimeZone.of("Asia/Shanghai"),
    CurrentWeather(Instant.parse("2026-10-28T08:00:00Z"), 18.0, 17.0, 60, 2.0, WeatherCondition.Clear, true),
    emptyList(), Instant.parse("2026-10-28T08:05:00Z"),
)
