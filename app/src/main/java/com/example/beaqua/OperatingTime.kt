package com.example.beaqua

import java.util.Locale

/** Converts displayed AM/PM times to the existing database format. */
fun normalizeOperatingTime(value: String): String? {
    val match = Regex("^(\\d{1,2}):(\\d{2})\\s*(AM|PM)?$", RegexOption.IGNORE_CASE)
        .matchEntire(value.trim()) ?: return null
    var hour = match.groupValues[1].toInt()
    val minute = match.groupValues[2].toInt()
    val period = match.groupValues[3].uppercase(Locale.US)
    if (minute !in 0..59) return null
    if (period.isNotEmpty()) {
        if (hour !in 1..12) return null
        hour = hour % 12 + if (period == "PM") 12 else 0
    } else if (hour !in 0..23) return null
    return String.format(Locale.US, "%02d:%02d", hour, minute)
}

fun String.toDisplayTime(): String {
    val normalized = normalizeOperatingTime(this) ?: return this
    val hour = normalized.substringBefore(':').toInt()
    val minute = normalized.substringAfter(':')
    val displayHour = if (hour % 12 == 0) 12 else hour % 12
    return "$displayHour:$minute${if (hour < 12) "AM" else "PM"}"
}
