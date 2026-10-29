package com.leon.weather.time

import com.leon.weather.model.WeatherSnapshot
import kotlinx.datetime.*
import kotlin.math.abs
import kotlin.math.roundToInt

fun temperatureNumber(value: Double?): String = value?.takeIf(Double::isFinite)?.roundToInt()?.toString() ?: "—"
fun decimalNumber(value: Double): String {
    if (!value.isFinite()) return "—"
    val tenths = (abs(value) * 10).roundToInt()
    return "${if (value < 0) "-" else ""}${tenths / 10}.${tenths % 10}"
}
private fun Int.padded() = toString().padStart(2, '0')
fun localHour(time: Instant, zone: TimeZone): String = time.toLocalDateTime(zone).let { "${it.hour.padded()}:${it.minute.padded()}" }
fun localDateTime(time: Instant, zone: TimeZone): String = time.toLocalDateTime(zone).let { "${it.monthNumber.padded()}-${it.dayOfMonth.padded()} ${localHour(time, zone)}" }
fun weekday(date: LocalDate): String = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[date.dayOfWeek.ordinal]
fun calendarDay(date: LocalDate): String = "${date.monthNumber.padded()}/${date.dayOfMonth.padded()}"
fun relativeDay(time: Instant, reference: Instant, zone: TimeZone): Long =
    (time.toLocalDateTime(zone).date.toEpochDays() - reference.toLocalDateTime(zone).date.toEpochDays()).toLong()

/** Both native UIs apply the same stale-data filtering without changing snapshot timestamps. */
fun WeatherSnapshot.forDisplay(reference: Instant): WeatherSnapshot {
    val today = reference.toLocalDateTime(timeZone).date
    return copy(hourly = hourly.filter { it.time > reference }, daily = daily.filter { it.date >= today })
}
