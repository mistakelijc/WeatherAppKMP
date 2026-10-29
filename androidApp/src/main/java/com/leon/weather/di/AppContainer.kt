package com.leon.weather.di

import android.content.Context
import com.leon.weather.createAndroidEnvironment

class AppContainer(context: Context) {
    private val environment = createAndroidEnvironment(context)
    val clock = environment.clock
    val locationRepository = environment.locationRepository
    val weatherRepository = environment.weatherRepository
    val historyRepository = environment.historyRepository
}
