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
     */
    fun compute(alarm: Alarm, now: ZonedDateTime): ZonedDateTime {
        val time = LocalTime.of(alarm.hour, alarm.minute)
        for (offset in 0..7L) {
            val date = now.toLocalDate().plusDays(offset)
            val candidate = ZonedDateTime.of(date, time, now.zone)
            if (!candidate.isAfter(now)) continue
            if (!alarm.isRepeating || alarm.repeatsOn(date.dayOfWeek)) return candidate
        }
        // Unreachable for a valid mask, but never return a time in the past.
        return ZonedDateTime.of(now.toLocalDate().plusDays(1), time, now.zone)
    }

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
