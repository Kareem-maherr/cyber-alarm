package com.cyberalarm.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cyberalarm.R
import com.cyberalarm.challenge.ChallengeSpec
import com.cyberalarm.challenge.ChallengeType
import com.cyberalarm.challenge.Difficulty
import java.time.DayOfWeek

@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int = 7,
    val minute: Int = 0,
    val label: String = "WAKE_UP",
    /** Bitmask, bit 0 = MONDAY ... bit 6 = SUNDAY. 0 = one-shot. */
    val repeatDays: Int = 0,
    val enabled: Boolean = true,
    val vibrate: Boolean = true,
    val snoozeEnabled: Boolean = true,
    val snoozeMinutes: Int = 5,
    val maxSnoozes: Int = 2,
    /** Either `builtin:<key>` (see [BuiltinTone]) or a content:// URI from MediaStore / SAF. */
    val soundUri: String = BuiltinTone.NEON_PULSE.key,
    val soundTitle: String = BuiltinTone.NEON_PULSE.title,
    /** Dismissal chain, run in order. Empty = hold-to-dismiss. */
    val challenges: List<ChallengeSpec> = listOf(ChallengeSpec(ChallengeType.MATH, Difficulty.MEDIUM, 3)),
    /** Packages locked after this alarm is dismissed. */
    val lockedPackages: List<String> = emptyList(),
    val lockMinutes: Int = 120,
) {
    val isRepeating: Boolean get() = repeatDays != 0

    fun repeatsOn(day: DayOfWeek): Boolean = repeatDays and dayBit(day) != 0

    fun withDayToggled(day: DayOfWeek): Alarm = copy(repeatDays = repeatDays xor dayBit(day))

    fun timeText(): String = "%02d:%02d".format(hour, minute)

    companion object {
        fun dayBit(day: DayOfWeek): Int = 1 shl (day.value - 1)
        const val WEEKDAYS = 0b0011111
        const val EVERY_DAY = 0b1111111
    }
}

enum class BuiltinTone(val key: String, val title: String, val resId: Int) {
    NEON_PULSE("builtin:neon_pulse", "NEON_PULSE", R.raw.neon_pulse),
    NIGHT_CITY("builtin:night_city", "NIGHT_CITY", R.raw.night_city),
    GRID_RUNNER("builtin:grid_runner", "GRID_RUNNER", R.raw.grid_runner);

    companion object {
        fun fromKey(key: String): BuiltinTone? = entries.find { it.key == key }
    }
}
