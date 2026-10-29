package com.leon.weather.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface GeocodingApi {
    suspend fun search(query: String, count: Int = 10, language: String = "zh"): LocationsDto
}

interface ForecastApi {
    suspend fun forecast(latitude: Double, longitude: Double): ForecastDto
}

@Serializable
data class LocationsDto(val results: List<LocationDto> = emptyList())

@Serializable
data class LocationDto(
    val id: Long? = null,
    val name: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val admin1: String = "",
    val country: String = "",
    val timezone: String = "UTC",
)

@Serializable
data class ForecastDto(
    val timezone: String,
    val current: CurrentDto? = null,
    val hourly: HourlyDto? = null,
    val daily: DailyDto? = null,
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int? = null,
)

@Serializable
data class CurrentDto(
    val time: Long,
    @SerialName("temperature_2m") val temperature: Double? = null,
    @SerialName("apparent_temperature") val apparentTemperature: Double? = null,
    @SerialName("relative_humidity_2m") val humidity: Int? = null,
    @SerialName("wind_speed_10m") val windSpeed: Double? = null,
    @SerialName("weather_code") val weatherCode: Int? = null,
    @SerialName("is_day") val isDay: Int = 1,
)

@Serializable
data class HourlyDto(
    val time: List<Long> = emptyList(),
    @SerialName("temperature_2m") val temperature: List<Double?> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
    @SerialName("precipitation_probability") val precipitationProbability: List<Int?> = emptyList(),
    @SerialName("is_day") val isDay: List<Int?> = emptyList(),
    @SerialName("uv_index") val uvIndex: List<Double?> = emptyList(),
)

@Serializable
data class DailyDto(
    val time: List<Long> = emptyList(),
    @SerialName("temperature_2m_max") val maximumTemperature: List<Double?> = emptyList(),
    @SerialName("temperature_2m_min") val minimumTemperature: List<Double?> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
)
