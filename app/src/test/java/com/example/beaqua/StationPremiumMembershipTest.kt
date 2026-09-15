package com.example.beaqua

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StationPremiumMembershipTest {
    @Test
    fun cancellationMidPeriodKeepsAccessUntilExactExpiry() {
        val day = 24L * 60L * 60L * 1000L
        val membership = StationPremiumMembership(
            customerUsername = "customer1",
            stationOwnerUsername = "stationA",
            purchasedAt = day,
            expiresAt = 32 * day,
            cancelAtPeriodEnd = true,
            cancellationRequestedAt = 15 * day
        )
        assertTrue(membership.isActiveFor("customer1", "stationA", now = 15 * day))
        assertTrue(membership.isActiveFor("customer1", "stationA", now = 32 * day - 1))
        assertFalse(membership.isActiveFor("customer1", "stationA", now = 32 * day))
        assertFalse(membership.isActiveFor("customer1", "stationB", now = 15 * day))
    }

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
