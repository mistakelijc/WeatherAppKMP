package com.leon.weather.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal expect fun platformHttpEngine(): HttpClientEngine

internal fun weatherHttpClient(engine: HttpClientEngine = platformHttpEngine()) = HttpClient(engine) {
    expectSuccess = true
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    install(HttpTimeout) {
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 20_000
        requestTimeoutMillis = 25_000
    }
}

internal class OpenMeteoApi(
    private val client: HttpClient,
    private val geocodingUrl: String = "https://geocoding-api.open-meteo.com/",
    private val forecastUrl: String = "https://api.open-meteo.com/",
) : GeocodingApi, ForecastApi {
    override suspend fun search(query: String, count: Int, language: String): LocationsDto =
        client.get("${geocodingUrl}v1/search") {
            parameter("name", query)
            parameter("count", count)
            parameter("language", language)
        }.body()

    override suspend fun forecast(latitude: Double, longitude: Double): ForecastDto =
        client.get("${forecastUrl}v1/forecast") {
            parameter("latitude", latitude)
            parameter("longitude", longitude)
            parameter("current", "temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code,is_day")
            parameter("hourly", "temperature_2m,weather_code,precipitation_probability,is_day,uv_index")
            parameter("forecast_hours", 25)
            parameter("daily", "weather_code,temperature_2m_max,temperature_2m_min")
            parameter("forecast_days", 7)
            parameter("timezone", "auto")
            parameter("timeformat", "unixtime")
            parameter("temperature_unit", "celsius")
            parameter("wind_speed_unit", "ms")
        }.body()
}
