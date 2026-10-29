package com.leon.weather.time

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

fun interface WeatherClock {
    fun instant(): Instant

    companion object {
        val System: WeatherClock = WeatherClock { Clock.System.now() }
    }
}
