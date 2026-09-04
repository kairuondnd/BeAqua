package com.example.beaqua

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StationPremiumMembershipTest {

    @Test
    fun activeMembershipOnlyAppliesToPurchasedStation() {
        val membership = StationPremiumMembership(
            customerUsername = "customer1",
            stationOwnerUsername = "stationA",
            expiresAt = 2_000L
        )

        assertTrue(membership.isActiveFor("customer1", "stationA", now = 1_000L))
        assertFalse(membership.isActiveFor("customer1", "stationB", now = 1_000L))
        assertFalse(membership.isActiveFor("customer2", "stationA", now = 1_000L))
    }

    @Test
    fun expiredMembershipDoesNotUnlockStation() {
        val membership = StationPremiumMembership(
            customerUsername = "customer1",
            stationOwnerUsername = "stationA",
            expiresAt = 1_000L
        )

        assertFalse(membership.isActiveFor("customer1", "stationA", now = 1_000L))
        assertFalse(membership.isActiveFor("customer1", "stationA", now = 1_001L))
    }
}
