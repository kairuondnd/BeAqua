package com.example.beaqua

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderIdGeneratorTest {

    @Test
    fun usesNormalizedStationNameAndGroupedHash() {
        val orderId = OrderIdGenerator.generate(
            stationName = "  Store  ",
            randomHex = "7f9a2c3d-rest-of-uuid"
        )

        assertEquals("STORE-7F9A-2C3D", orderId)
    }

    @Test
    fun replacesSpacesAndSymbolsInStationName() {
        val orderId = OrderIdGenerator.generate(
            stationName = "Kyle's Aqua Station",
            randomHex = "abcdef12"
        )

        assertEquals("KYLE-S-AQUA-STATION-ABCD-EF12", orderId)
    }

    @Test
    fun generatedIdHasStationPrefixAndEightHexCharacters() {
        val orderId = OrderIdGenerator.generate("Blue Water")

        assertTrue(orderId.matches(Regex("BLUE-WATER-[0-9A-F]{4}-[0-9A-F]{4}")))
    }
}
