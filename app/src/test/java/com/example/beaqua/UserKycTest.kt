package com.example.beaqua

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserKycTest {

    @Test
    fun stationOwnerRequiresApproval() {
        val pending = User(accountType = "Station Owner", kycStatus = User.KYC_PENDING)
        val approved = User(accountType = "Station Owner", kycStatus = User.KYC_APPROVED)

        assertFalse(pending.isApprovedStationOwner())
        assertTrue(approved.isApprovedStationOwner())
    }

    @Test
    fun allThreeDocumentsAreRequired() {
        val incomplete = User(
            barangayClearanceUrl = "barangay-url",
            sanitaryPermitUrl = "sanitary-url"
        )
        val complete = incomplete.copy(mayorsPermitUrl = "mayors-url")

        assertFalse(incomplete.hasCompleteKycDocuments())
        assertTrue(complete.hasCompleteKycDocuments())
    }

    @Test
    fun customerIsNeverAnApprovedStationOwner() {
        val customer = User(accountType = "Customer", kycStatus = User.KYC_APPROVED)

        assertFalse(customer.isApprovedStationOwner())
    }

    @Test
    fun approvedStationIsLabeledActive() {
        val station = User(accountType = "Station Owner", kycStatus = User.KYC_APPROVED)

        assertEquals("APPROVED • ACTIVE", station.stationAccountStatusLabel())
    }
}
