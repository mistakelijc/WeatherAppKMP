package com.leon.weather.presentation

import com.leon.weather.data.repository.AppError
import com.leon.weather.model.*
import com.leon.weather.sampleWeather
import kotlinx.datetime.*
import kotlin.test.*

class NativePresentationTest {
    @Test fun midnightFailureKeepsTimestampButFiltersAndRelabelsForecasts() {
        val originalTime = Instant.parse("2026-10-28T15:59:00Z")
        val newTime = Instant.parse("2026-10-28T16:01:00Z")
        val data = sampleWeather().copy(
            fetchedAt = originalTime,
            current = sampleWeather().current.copy(time = originalTime),
            hourly = listOf(
                HourlyForecast(Instant.parse("2026-10-28T16:00:00Z"), 18.0, 0, WeatherCondition.Clear, false),
                HourlyForecast(Instant.parse("2026-10-28T17:00:00Z"), null, null, WeatherCondition.Clear, false),
            ),
            daily = listOf(
                DailyForecast(LocalDate(2026, 10, 28), 20.0, 10.0, WeatherCondition.Clear),
                DailyForecast(LocalDate(2026, 10, 29), 22.0, 12.0, WeatherCondition.Clear),
            ),
        )
        val state = WeatherUiState.Content(data, refreshError = AppError.Network, referenceTime = newTime).toViewData()
        val details = assertNotNull(state.details)
        assertEquals("10-28 23:59", details.updatedAt)
        assertEquals("22°C", details.high)
        assertEquals("今天", details.days.single().day)
        assertEquals("01:00", details.hours.single().hour)
        assertEquals("—", details.hours.single().temperature)
        assertEquals("—", details.hours.single().precipitation)
        assertNotNull(state.refreshErrorMessage)
        assertEquals(2, data.hourly.size)
    }

    @Test fun repeatedDstHoursHaveDistinctSwiftUiIds() {
        val data = sampleWeather().copy(
            timeZone = TimeZone.of("America/New_York"),
            fetchedAt = Instant.parse("2026-11-01T04:30:00Z"),
            hourly = listOf("2026-11-01T05:00:00Z", "2026-11-01T06:00:00Z").map {
                HourlyForecast(Instant.parse(it), 10.0, 0, WeatherCondition.Clear, false)
            },
        )
        val hours = WeatherUiState.Content(data).toViewData().details!!.hours
        assertEquals(listOf("01:00", "01:00"), hours.map { it.hour })
        assertNotEquals(hours[0].id, hours[1].id)
        assertTrue(hours.all { it.precipitation == "0%" })
    }

    @Test fun searchBridgePreservesHistoryAndErrors() {
        val result = SearchUiState(query = "北京", status = SearchStatus.Error(AppError.Timeout),
            history = PopularLocations.all, historyUpdateFailed = true).toViewData()
        assertEquals(SearchPhase.Error, result.phase)
        assertEquals(PopularLocations.all, result.history)
        assertTrue(result.historyUpdateFailed)
        assertNotNull(result.errorMessage)
    }
}
