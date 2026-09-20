package com.example.beaqua

import com.google.firebase.firestore.IgnoreExtraProperties
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@IgnoreExtraProperties
data class User(
    var name: String = "",
    var ownerFullName: String = "",
    var emailAddress: String = "",
    var contactNumber: String = "",
    var address: String = "",
    var username: String = "",
    var password: String = "",
    var supabaseUserId: String = "",
    var accountType: String = "",
    var latitude: Double? = null,
    var longitude: Double? = null,
    var kycStatus: String = KYC_NOT_REQUIRED,
    var barangayClearanceUrl: String = "",
    var sanitaryPermitUrl: String = "",
    var mayorsPermitUrl: String = "",
    var kycSubmittedAt: Long = 0L,
    var kycReviewedAt: Long = 0L,
    var etaSettings: EtaSettings = EtaSettings(),
    var rushOrderEnabled: Boolean = false,
    var rushOrderFee: Double = 0.0,
    var deliveryFee: Double = 0.0,
    var refillServiceEnabled: Boolean = false,
    var refillFee: Double = 0.0,
    var premiumPrice: Double = 99.0,
    var gcashQrUrl: String = "",
    var operatingHours: OperatingHours = OperatingHours()
) {
    companion object {
        const val KYC_NOT_REQUIRED = "NOT_REQUIRED"
        const val KYC_PENDING = "PENDING"
        const val KYC_APPROVED = "APPROVED"
        const val KYC_REJECTED = "REJECTED"
    }

    fun isApprovedStationOwner(): Boolean {
        return accountType == "Station Owner" && kycStatus == KYC_APPROVED
    }

    fun stationAccountStatusLabel(): String = when {
        isApprovedStationOwner() -> "APPROVED • ACTIVE"
        kycStatus == KYC_REJECTED -> "REJECTED • INACTIVE"
        kycStatus == KYC_PENDING -> "PENDING ADMIN APPROVAL • INACTIVE"
        else -> "UNVERIFIED • INACTIVE"
    }

    fun hasCompleteKycDocuments(): Boolean {
        return barangayClearanceUrl.isNotBlank() &&
            sanitaryPermitUrl.isNotBlank() &&
            mayorsPermitUrl.isNotBlank()
    }

    fun isStationOpen(now: Calendar = Calendar.getInstance()): Boolean {
        return when (operatingHours.statusOverride.uppercase(Locale.US)) {
            OperatingHours.STATUS_OPEN -> true
            OperatingHours.STATUS_CLOSED -> false
            else -> {
                val opensAt = operatingHours.openTime.toMinutesOrNull() ?: return true
                val closesAt = operatingHours.closeTime.toMinutesOrNull() ?: return true
                val current = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
                when {
                    opensAt == closesAt -> true
                    opensAt < closesAt -> current in opensAt until closesAt
                    else -> current >= opensAt || current < closesAt
                }
            }
        }
    }

    fun stationStatusLabel(now: Calendar = Calendar.getInstance()): String {
        return when (operatingHours.statusOverride.uppercase(Locale.US)) {
            OperatingHours.STATUS_OPEN -> "Open now - manually opened"
            OperatingHours.STATUS_CLOSED -> "Closed now - manually closed"
            else -> if (isStationOpen(now)) "Open now" else "Closed now"
        }
    }

    fun operatingHoursLabel(): String {
        val opensAt = operatingHours.openTime.toMinutesOrNull()
        val closesAt = operatingHours.closeTime.toMinutesOrNull()
        if (opensAt != null && opensAt == closesAt) return "Open 24 hours"
        return "Daily: ${operatingHours.openTime.toDisplayTime()} - " +
            operatingHours.closeTime.toDisplayTime()
    }
}

data class OperatingHours(
    var openTime: String = "00:00",
    var closeTime: String = "00:00",
    var statusOverride: String = STATUS_AUTO,
    var timeZoneId: String = "Asia/Manila"
) {
    companion object {
        const val STATUS_AUTO = "AUTO"
        const val STATUS_OPEN = "OPEN"
        const val STATUS_CLOSED = "CLOSED"
    }
}

fun String.isValidOperatingTime(): Boolean = toMinutesOrNull() != null

private fun String.toMinutesOrNull(): Int? {
    val pieces = trim().split(":")
    if (pieces.size != 2) return null
    val hour = pieces[0].toIntOrNull() ?: return null
    val minute = pieces[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

private fun String.toDisplayTime(): String {
    val minutes = toMinutesOrNull() ?: return this
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, minutes / 60)
        set(Calendar.MINUTE, minutes % 60)
    }
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(calendar.time)
}

data class EtaSettings(
    var window1Start: String = "07:00",
    var window1End: String = "11:00",
    var window1Eta: String = "1:00 PM - 4:00 PM",
    var window2Start: String = "12:00",
    var window2End: String = "15:00",
    var window2Eta: String = "4:00 PM - 6:00 PM",
    var defaultEta: String = "1:00 PM - 4:00 PM (Next Day)"
) {
    fun deliveryEstimateAt(timestamp: Long = System.currentTimeMillis()): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        val currentMinutes =
            calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val firstStart = window1Start.toMinutesOrNull()
        val firstEnd = window1End.toMinutesOrNull()
        val secondStart = window2Start.toMinutesOrNull()
        val secondEnd = window2End.toMinutesOrNull()

        return when {
            firstStart != null && firstEnd != null &&
                currentMinutes in firstStart..firstEnd -> window1Eta.ifBlank { defaultEta }
            secondStart != null && secondEnd != null &&
                currentMinutes in secondStart..secondEnd -> window2Eta.ifBlank { defaultEta }
            else -> defaultEta.ifBlank { "Delivery time to be confirmed" }
        }
    }

    fun customerDeliveryWindows(): List<String> {
        return listOf(window1Eta, window2Eta)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .ifEmpty { listOf("Delivery time to be confirmed") }
    }
}
