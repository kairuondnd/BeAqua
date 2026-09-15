package com.example.beaqua

import java.util.Calendar

object DeliveryRecurrence {
    /** Advance from the saved date, preserving local time across calendar changes. */
    fun nextAfter(timestamp: Long, intervalDays: Int, now: Long): Long {
        require(intervalDays in 1..3650) { "Choose an interval between 1 and 3650 days" }
        require(timestamp > 0L) { "Choose a first delivery date" }
        val date = Calendar.getInstance().apply { timeInMillis = timestamp }
        while (date.timeInMillis <= now) date.add(Calendar.DAY_OF_YEAR, intervalDays)
        return date.timeInMillis
    }
}
