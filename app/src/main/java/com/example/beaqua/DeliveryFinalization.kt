package com.example.beaqua

import java.util.Calendar
import java.util.TimeZone

/** Calendar-day rules shared by editing, reminders and order generation. */
object DeliveryFinalization {
    enum class QueueAction { WAIT, CREATE, ADVANCE }

    fun queueAction(deliveryAt: Long, hours: OperatingHours, now: Long, alreadyQueued: Boolean): QueueAction =
        when {
            now < queueAt(deliveryAt, hours) -> QueueAction.WAIT
            !alreadyQueued -> QueueAction.CREATE
            canEdit(deliveryAt, hours, now) -> QueueAction.WAIT
            else -> QueueAction.ADVANCE
        }

    /** Publish at midnight on the previous calendar day in the station's timezone. */
    fun queueAt(deliveryAt: Long, hours: OperatingHours): Long =
        Calendar.getInstance(TimeZone.getTimeZone(hours.timeZoneId)).apply {
            timeInMillis = cutoff(deliveryAt, hours)
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
        }.timeInMillis

    fun firstDeliveryAfter(
        now: Long,
        intervalDays: Int,
        hours: OperatingHours
    ): Long {
        require(intervalDays in 1..3650)
        return Calendar.getInstance(TimeZone.getTimeZone(hours.timeZoneId)).apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, intervalDays)
            set(Calendar.HOUR_OF_DAY, 6)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun cutoff(deliveryAt: Long, hours: OperatingHours): Long {
        require(deliveryAt > 0) { "Choose a delivery date" }
        require(hours.openTime.isValidOperatingTime()) { "Station opening time is invalid" }
        val parts = hours.openTime.trim().split(":").map(String::toInt)
        return Calendar.getInstance(TimeZone.getTimeZone(hours.timeZoneId)).apply {
            timeInMillis = deliveryAt
            set(Calendar.HOUR_OF_DAY, parts[0])
            set(Calendar.MINUTE, parts[1])
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun reminderAt(deliveryAt: Long, hours: OperatingHours): Long =
        Calendar.getInstance(TimeZone.getTimeZone(hours.timeZoneId)).apply {
            timeInMillis = cutoff(deliveryAt, hours)
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
        }.timeInMillis

    fun canEdit(deliveryAt: Long, hours: OperatingHours, now: Long): Boolean =
        now < cutoff(deliveryAt, hours)

    fun shouldRemind(deliveryAt: Long, hours: OperatingHours, now: Long): Boolean =
        now >= reminderAt(deliveryAt, hours) && canEdit(deliveryAt, hours, now)

    fun nextDelivery(deliveryAt: Long, intervalDays: Int, hours: OperatingHours, now: Long): Long {
        require(intervalDays in 1..3650)
        val date = Calendar.getInstance(TimeZone.getTimeZone(hours.timeZoneId)).apply {
            timeInMillis = deliveryAt
        }
        do { date.add(Calendar.DAY_OF_YEAR, intervalDays) }
        while (cutoff(date.timeInMillis, hours) <= now)
        return date.timeInMillis
    }
}
