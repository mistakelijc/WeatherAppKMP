package com.leon.weather.data.repository

import com.leon.weather.data.remote.*
import com.leon.weather.model.PopularLocations
import com.leon.weather.time.WeatherClock
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestData
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.*
import kotlin.test.*

class OpenMeteoRepositoryTest {
    private val city = PopularLocations.all.first()
    private val clock = WeatherClock { Instant.fromEpochSeconds(1793174400) }
    private val beijing = """{"results":[{"id":1816670,"name":"北京","latitude":39.9,"longitude":116.4}]}"""

    private suspend fun withApi(vararg bodies: Pair<Int, String>, block: suspend (OpenMeteoApi, List<HttpRequestData>) -> Unit) {
        val requests = mutableListOf<HttpRequestData>()
        val client = weatherHttpClient(MockEngine { request ->
            val (code, body) = bodies[requests.size]
            requests += request
            respond(body, HttpStatusCode.fromValue(code), headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try { block(OpenMeteoApi(client), requests) } finally { client.close() }
    }

    @Test fun emptySearchIsValidAndQueryIsEncoded() = runTest {
        withApi(200 to "{}") { api, requests ->
            assertEquals(AppResult.Success(emptyList()), OpenMeteoLocationRepository(api).search(" New York "))
            assertEquals("New York", requests.single().url.parameters["name"])
            assertEquals("zh", requests.single().url.parameters["language"])
            assertEquals("10", requests.single().url.parameters["count"])
        }
    }

    @Test fun chineseCityFallbackAddsSuffixOnce() = runTest {
        withApi(200 to "{}", 200 to beijing) { api, requests ->
            assertTrue(OpenMeteoLocationRepository(api).search(" 厦门 ") is AppResult.Success)
            assertEquals(listOf("厦门", "厦门市"), requests.map { it.url.parameters["name"] })
        }
    }

    @Test fun chineseCityFallbackRemovesSuffix() = runTest {
        withApi(200 to "{}", 200 to "{}") { api, requests ->
            assertEquals(AppResult.Success(emptyList()), OpenMeteoLocationRepository(api).search("杭州市"))
            assertEquals(listOf("杭州市", "杭州"), requests.map { it.url.parameters["name"] })
        }
    }

    @Test fun successfulSearchNeverRetries() = runTest {
        withApi(200 to beijing) { api, requests ->
            val result = OpenMeteoLocationRepository(api).search("北京") as AppResult.Success
            assertEquals("北京", result.value.single().name)
            assertEquals(1, requests.size)
        }
    }

    @Test fun fallbackFailureIsNotReportedAsEmptyResults() = runTest {
        withApi(200 to "{}", 503 to "{}") { api, requests ->
            assertEquals(AppResult.Failure(AppError.ServiceUnavailable), OpenMeteoLocationRepository(api).search("厦门"))
            assertEquals(2, requests.size)
        }
    }

    @Test fun rateLimitAndServerFailuresAreDistinct() = runTest {
        withApi(429 to "{}", 503 to "{}") { api, requests ->
            val repository = OpenMeteoLocationRepository(api)
            assertEquals(AppResult.Failure(AppError.RateLimited), repository.search("北京"))
            assertEquals(AppResult.Failure(AppError.ServiceUnavailable), repository.search("北京"))
            assertEquals(2, requests.size)
        }
    }

    @Test fun malformedPayloadIsAnInvalidDataError() = runTest {
        withApi(200 to """{"results":"bad"}""") { api, _ ->
            assertEquals(AppResult.Failure(AppError.InvalidData), OpenMeteoLocationRepository(api).search("北京"))
        }
    }

    @Test fun proxyHtmlIsADataErrorInsteadOfAnUncaughtException() = runTest {
        val client = weatherHttpClient(MockEngine {
            respond("<html>Unavailable</html>", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html"))
        })
        try {
            val repository = OpenMeteoLocationRepository(OpenMeteoApi(client))
            assertEquals(AppResult.Failure(AppError.InvalidData), repository.search("London"))
        } finally { client.close() }
    }

    @Test fun missingCurrentAndInvalidTimezoneAreDataErrors() = runTest {
        withApi(200 to """{"timezone":"Asia/Shanghai"}""", 200 to """{"timezone":"not/a/zone","current":{"time":1793174400}}""") { api, _ ->
            val repository = OpenMeteoWeatherRepository(api, clock)
            repeat(2) { assertEquals(AppResult.Failure(AppError.InvalidData), repository.getWeather(city)) }
        }
    }

    @Test fun forecastUnitsAndRangesAreStable() = runTest {
        withApi(200 to """{"timezone":"Asia/Shanghai","current":{"time":1793174400},"new_field":true}""") { api, requests ->
            assertTrue(OpenMeteoWeatherRepository(api, clock).getWeather(city) is AppResult.Success)
            val query = requests.single().url.parameters
            mapOf("forecast_hours" to "25", "forecast_days" to "7", "timeformat" to "unixtime",
                "wind_speed_unit" to "ms", "temperature_unit" to "celsius", "timezone" to "auto",
                "daily" to "weather_code,temperature_2m_max,temperature_2m_min").forEach { (key, value) ->
                assertEquals(value, query[key], key)
            }
            assertTrue("uv_index" in query["hourly"]!!.split(','))
            assertEquals(city.latitude.toString(), query["latitude"])
            assertEquals(city.longitude.toString(), query["longitude"])
        }
    }

    @Test fun precipitationKeepsZeroNonzeroAndMissing() = runTest {
        withApi(200 to """{"timezone":"Asia/Shanghai","current":{"time":1793174400},"hourly":{"time":[1793178000,1793181600,1793185200,1793188800],"precipitation_probability":[0,37,100,null]}}""") { api, _ ->
            val result = OpenMeteoWeatherRepository(api, clock).getWeather(city) as AppResult.Success
            assertEquals(listOf(0, 37, 100, null), result.value.hourly.map { it.precipitationProbability })
        }
    }

    @Test fun uvAndExtremesDeserializeTogether() = runTest {
        withApi(200 to """{"timezone":"Asia/Shanghai","current":{"time":1793174400},"hourly":{"time":[1793174400,1793178000],"uv_index":[2.5,3.8]},"daily":{"time":[1793116800],"temperature_2m_max":[22.0],"temperature_2m_min":[10.0],"weather_code":[2]}}""") { api, _ ->
            val result = OpenMeteoWeatherRepository(api, clock).getWeather(city) as AppResult.Success
            assertEquals(2.5, result.value.current.uvIndex)
            assertEquals(22.0, result.value.today?.maximumTemperature)
            assertEquals(10.0, result.value.today?.minimumTemperature)
        }
    }

    @Test fun miamiCapturedResponsePreservesSevenDaysAcrossDst() = runTest {
        withApi(200 to miamiResponse) { api, _ ->
            val repository = OpenMeteoWeatherRepository(api, WeatherClock { Instant.parse("2026-10-29T04:33:00Z") })
            val weather = (repository.getWeather(city.copy(timeZone = "America/New_York")) as AppResult.Success).value
            assertEquals((0..6).map { LocalDate(2026, 10, 29).plus(it, DateTimeUnit.DAY) }, weather.daily.map { it.date })
            assertEquals(listOf(28.4, 28.2, 28.1, 28.2, 28.6, 28.7, 28.5), weather.daily.map { it.maximumTemperature })
            assertEquals(listOf(22.8, 22.8, 26.5, 26.1, 26.1, 26.3, 26.2), weather.daily.map { it.minimumTemperature })
        }
    }

    @Test fun cancellationDuringFallbackPropagates() = runTest {
        var calls = 0
        val api = object : GeocodingApi {
            override suspend fun search(query: String, count: Int, language: String): LocationsDto {
                if (++calls == 1) return LocationsDto()
                throw CancellationException()
            }
        }
        assertFailsWith<CancellationException> { OpenMeteoLocationRepository(api).search("厦门") }
    }

    @Test fun cancellationIsNeverConvertedToUiError() = runTest {
        assertFailsWith<CancellationException> { apiResult<Unit> { throw CancellationException() } }
    }

    @Test fun transportAndTimeoutFailuresAreClassified() = runTest {
        assertEquals(AppResult.Failure(AppError.Network), apiResult<Unit> { throw kotlinx.io.IOException("offline") })
        assertEquals(AppResult.Failure(AppError.Timeout), apiResult<Unit> { throw HttpRequestTimeoutException("https://example.invalid", 25_000) })
    }
}
