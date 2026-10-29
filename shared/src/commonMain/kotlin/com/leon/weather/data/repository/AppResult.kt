package com.leon.weather.data.repository

import io.ktor.client.call.NoTransformationFoundException
import kotlinx.datetime.DateTimeArithmeticException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.serialization.JsonConvertException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
// Ktor uses kotlinx-io; DataStore uses okio. These differ on Kotlin/Native.
import kotlinx.io.IOException

enum class AppError { Network, Timeout, RateLimited, ServiceUnavailable, InvalidData }

sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

internal class InvalidWeatherData : Exception()

internal suspend fun <T> apiResult(block: suspend () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: HttpRequestTimeoutException) {
    AppResult.Failure(AppError.Timeout)
} catch (_: ConnectTimeoutException) {
    AppResult.Failure(AppError.Timeout)
} catch (_: SocketTimeoutException) {
    AppResult.Failure(AppError.Timeout)
} catch (http: ResponseException) {
    AppResult.Failure(if (http.response.status.value == 429) AppError.RateLimited else AppError.ServiceUnavailable)
} catch (_: NoTransformationFoundException) {
    AppResult.Failure(AppError.InvalidData)
} catch (_: DateTimeArithmeticException) {
    AppResult.Failure(AppError.InvalidData)
} catch (_: JsonConvertException) {
    AppResult.Failure(AppError.InvalidData)
} catch (_: SerializationException) {
    AppResult.Failure(AppError.InvalidData)
} catch (_: InvalidWeatherData) {
    AppResult.Failure(AppError.InvalidData)
} catch (_: IllegalArgumentException) {
    AppResult.Failure(AppError.InvalidData)
} catch (_: IOException) {
    AppResult.Failure(AppError.Network)
}
