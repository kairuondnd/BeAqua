package com.example.beaqua

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DeliveryEta {
    const val DEFAULT_TIME_ZONE_ID = "Asia/Manila"

    fun today(now: Long = System.currentTimeMillis(), timeZoneId: String = DEFAULT_TIME_ZONE_ID): Long {
        return normalize(now, timeZoneId)
    }

    fun tomorrow(now: Long = System.currentTimeMillis(), timeZoneId: String = DEFAULT_TIME_ZONE_ID): Long {
        return calendar(now, timeZoneId).apply {
            add(Calendar.DAY_OF_YEAR, 1)
            setToNoon()
        }.timeInMillis
    }

    fun fromDate(
        year: Int,
        zeroBasedMonth: Int,
        dayOfMonth: Int,
        timeZoneId: String = DEFAULT_TIME_ZONE_ID
    ): Long {
        return Calendar.getInstance(timeZone(timeZoneId)).apply {
            clear()
            set(year, zeroBasedMonth, dayOfMonth, 12, 0, 0)
        }.timeInMillis
    }

    fun normalize(timestamp: Long, timeZoneId: String = DEFAULT_TIME_ZONE_ID): Long {
        return calendar(timestamp, timeZoneId).apply { setToNoon() }.timeInMillis
    }

    fun isTodayOrFuture(
        timestamp: Long,
        now: Long = System.currentTimeMillis(),
        timeZoneId: String = DEFAULT_TIME_ZONE_ID
    ): Boolean = normalize(timestamp, timeZoneId) >= today(now, timeZoneId)

    fun label(
        timestamp: Long,
        timeZoneId: String = DEFAULT_TIME_ZONE_ID,
        now: Long = System.currentTimeMillis()
    ): String {
        if (timestamp <= 0L) return ""
        val normalized = normalize(timestamp, timeZoneId)
        val prefix = when (normalized) {
            today(now, timeZoneId) -> "Today"
            tomorrow(now, timeZoneId) -> "Tomorrow"
            else -> null
        }
        val formatted = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).apply {
            timeZone = timeZone(timeZoneId)
        }.format(Date(normalized))
        return if (prefix == null) formatted else "$prefix - $formatted"
    }

    private fun calendar(timestamp: Long, timeZoneId: String): Calendar {
        return Calendar.getInstance(timeZone(timeZoneId)).apply { timeInMillis = timestamp }
    }

    private fun Calendar.setToNoon() {
        set(Calendar.HOUR_OF_DAY, 12)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun timeZone(timeZoneId: String): TimeZone {
        val requested = TimeZone.getTimeZone(timeZoneId)
        return if (requested.id == "GMT" && timeZoneId != "GMT") {
            TimeZone.getTimeZone(DEFAULT_TIME_ZONE_ID)
        } else {
            requested
        }
    }
}
