package com.cyberalarm.alarm

import com.cyberalarm.data.Alarm
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

/** Pure next-occurrence math, kept free of Android types so it is unit-testable. */
object NextTrigger {

    /**
     * Next time [alarm] should fire strictly after [now]. Builds each candidate from its calendar
     * date (not by adding 24h) so DST transitions keep the wall-clock time.
     *
     * Repeating alarms also honour [Alarm.skipAt] (one skipped occurrence) and [holidayUntil]
     * (holiday mode: nothing before that instant). One-shot alarms ignore both: they were set on
     * purpose for a specific day.
     */
    fun compute(alarm: Alarm, now: ZonedDateTime, holidayUntil: Long = 0): ZonedDateTime {
        val time = LocalTime.of(alarm.hour, alarm.minute)
        for (offset in 0..MAX_DAYS) {
            val date = now.toLocalDate().plusDays(offset)
            val candidate = ZonedDateTime.of(date, time, now.zone)
            if (!candidate.isAfter(now)) continue
            if (!alarm.isRepeating) return candidate
            if (!alarm.repeatsOn(date.dayOfWeek)) continue
            val ms = candidate.toInstant().toEpochMilli()
            if (ms == alarm.skipAt || ms < holidayUntil) continue
            return candidate
        }
        // Unreachable for a valid mask, but never return a time in the past.
        return ZonedDateTime.of(now.toLocalDate().plusDays(1), time, now.zone)
    }

    /** The occurrence "skip next" would skip: the next one ignoring any existing skip. */
    fun nextUnskipped(alarm: Alarm, now: ZonedDateTime, holidayUntil: Long = 0): ZonedDateTime =
        compute(alarm.copy(skipAt = 0), now, holidayUntil)

    /** True while [Alarm.skipAt] still refers to an upcoming occurrence. */
    fun isSkipping(alarm: Alarm, now: ZonedDateTime, holidayUntil: Long = 0): Boolean =
        alarm.isRepeating && alarm.skipAt > 0 && nextUnskipped(alarm, now, holidayUntil).toInstant().toEpochMilli() == alarm.skipAt

    private const val MAX_DAYS = 400L

    /** `T-7H12M` style countdown for the HUD. */
    fun countdown(target: ZonedDateTime, now: ZonedDateTime): String {
        val d = Duration.between(now, target).coerceAtLeast(Duration.ZERO)
        val totalMin = (d.seconds + 59) / 60
        val days = totalMin / (60 * 24)
        val hours = (totalMin / 60) % 24
        val mins = totalMin % 60
        return if (days > 0) "T-${days}D${hours}H" else "T-${hours}H${"%02d".format(mins)}M"
    }
}
