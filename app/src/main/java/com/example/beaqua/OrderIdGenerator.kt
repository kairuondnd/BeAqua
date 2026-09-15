package com.example.beaqua

import java.util.Locale
import java.util.UUID

object OrderIdGenerator {
    private const val FALLBACK_STATION_NAME = "STATION"

    fun generate(stationName: String, randomHex: String = UUID.randomUUID().toString()): String {
        val stationPrefix = stationName
            .trim()
            .uppercase(Locale.US)
            .replace(Regex("[^A-Z0-9]+"), "-")
            .trim('-')
            .ifBlank { FALLBACK_STATION_NAME }

        val hash = randomHex
            .uppercase(Locale.US)
            .filter { it in '0'..'9' || it in 'A'..'F' }
            .padEnd(8, '0')
            .take(8)

        return "$stationPrefix-${hash.take(4)}-${hash.drop(4)}"
    }
}
