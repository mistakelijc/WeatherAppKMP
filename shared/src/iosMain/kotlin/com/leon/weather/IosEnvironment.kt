package com.leon.weather

import platform.Foundation.NSHomeDirectory

// The Preferences directory exists in the app sandbox. DataStore owns atomic writes.
private val environment by lazy {
    WeatherEnvironment(NSHomeDirectory() + "/Library/Preferences/weather_history.preferences_pb")
}

fun createIosEnvironment(): WeatherEnvironment = environment
