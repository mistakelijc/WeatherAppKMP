package com.leon.weather

import android.content.Context

fun createAndroidEnvironment(context: Context): WeatherEnvironment = WeatherEnvironment(
    context.applicationContext.filesDir.resolve("datastore/search_history.preferences_pb").absolutePath,
)
