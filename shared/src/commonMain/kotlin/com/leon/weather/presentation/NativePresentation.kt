package com.leon.weather.presentation

import com.leon.weather.data.repository.AppError
import com.leon.weather.model.Location
import com.leon.weather.model.WeatherCondition
import com.leon.weather.time.*
import kotlinx.datetime.*

enum class SearchPhase { Idle, TooShort, Loading, Empty, Results, Error }
enum class WeatherPhase { Loading, Error, Content }

data class SearchViewData(
    val query: String,
    val phase: SearchPhase,
    val results: List<Location>,
    val history: List<Location>,
    val errorMessage: String?,
    val historyUpdateFailed: Boolean,
)

data class HourViewData(
    val id: String, val day: String, val hour: String, val temperature: String,
    val precipitation: String, val conditionKey: String, val conditionLabel: String, val isDay: Boolean,
)

data class DayViewData(
    val id: String, val day: String, val date: String, val high: String, val low: String,
    val conditionKey: String, val conditionLabel: String,
)

data class WeatherDetails(
    val temperature: String, val high: String, val low: String,
    val apparentTemperature: String, val humidity: String, val wind: String, val uv: String,
    val conditionKey: String, val conditionLabel: String, val isDay: Boolean,
    val updatedAt: String, val timeZone: String,
    val hours: List<HourViewData>, val days: List<DayViewData>,
)

data class WeatherViewData(
    val phase: WeatherPhase, val details: WeatherDetails?, val isRefreshing: Boolean,
    val errorMessage: String?, val refreshErrorMessage: String?,
)

fun AppError.message(): String = when (this) {
    AppError.Network -> "网络连接失败，请检查网络后重试。"
    AppError.Timeout -> "请求超时，请稍后重试。"
    AppError.RateLimited -> "请求过于频繁，请稍后重试。"
    AppError.ServiceUnavailable -> "天气服务暂时不可用，请稍后重试。"
    AppError.InvalidData -> "天气数据暂时无法解析，请稍后重试。"
}

private fun WeatherCondition.label(isDay: Boolean = true): String = when (this) {
    WeatherCondition.Clear -> if (isDay) "晴" else "晴朗夜空"
    WeatherCondition.MostlyClear -> "大部晴朗"
    WeatherCondition.PartlyCloudy -> "多云"
    WeatherCondition.Overcast -> "阴"
    WeatherCondition.Fog -> "雾"
    WeatherCondition.Drizzle -> "毛毛雨"
    WeatherCondition.Rain -> "雨"
    WeatherCondition.Snow -> "雪"
    WeatherCondition.Showers -> "阵雨"
    WeatherCondition.Thunderstorm -> "雷雨"
    WeatherCondition.Unknown -> "天气未知"
}

fun SearchUiState.toViewData(): SearchViewData = SearchViewData(
    query = query,
    phase = when (status) {
        SearchStatus.Idle -> SearchPhase.Idle
        SearchStatus.TooShort -> SearchPhase.TooShort
        SearchStatus.Loading -> SearchPhase.Loading
        SearchStatus.Empty -> SearchPhase.Empty
        is SearchStatus.Results -> SearchPhase.Results
        is SearchStatus.Error -> SearchPhase.Error
    },
    results = (status as? SearchStatus.Results)?.locations.orEmpty(),
    history = history,
    errorMessage = (status as? SearchStatus.Error)?.error?.message(),
    historyUpdateFailed = historyUpdateFailed,
)

private fun temperature(value: Double?): String = value?.let { "${temperatureNumber(it)}°C" } ?: "—"

fun WeatherUiState.toViewData(): WeatherViewData = when (this) {
    WeatherUiState.Loading -> WeatherViewData(WeatherPhase.Loading, null, false, null, null)
    is WeatherUiState.Error -> WeatherViewData(WeatherPhase.Error, null, false, error.message(), null)
    is WeatherUiState.Content -> {
        val display = data.forDisplay(referenceTime)
        val today = referenceTime.toLocalDateTime(data.timeZone).date
        val todayForecast = display.daily.firstOrNull { it.date == today }
        val current = data.current
        val details = WeatherDetails(
            temperature(current.temperature), temperature(todayForecast?.maximumTemperature), temperature(todayForecast?.minimumTemperature),
            temperature(current.apparentTemperature), current.humidity?.let { "$it%" } ?: "—",
            current.windSpeed?.let { "${decimalNumber(it)} m/s" } ?: "—", current.uvIndex?.let(::decimalNumber) ?: "—",
            current.condition.name, current.condition.label(current.isDay), current.isDay,
            localDateTime(current.time, data.timeZone), data.timeZone.id,
            display.hourly.map { hour ->
                HourViewData(hour.time.epochSeconds.toString(), when (relativeDay(hour.time, referenceTime, data.timeZone)) {
                    0L -> "今天"
                    1L -> "明天"
                    else -> calendarDay(hour.time.toLocalDateTime(data.timeZone).date)
                }, localHour(hour.time, data.timeZone), temperature(hour.temperature),
                    hour.precipitationProbability?.let { "$it%" } ?: "—",
                    hour.condition.name, hour.condition.label(hour.isDay), hour.isDay)
            },
            display.daily.map { day ->
                DayViewData(day.date.toString(), when (day.date) {
                    today -> "今天"
                    today.plus(1, DateTimeUnit.DAY) -> "明天"
                    else -> weekday(day.date)
                }, calendarDay(day.date), temperature(day.maximumTemperature), temperature(day.minimumTemperature),
                    day.condition.name, day.condition.label())
            },
        )
        WeatherViewData(WeatherPhase.Content, details, isRefreshing, null, refreshError?.message())
    }
}
