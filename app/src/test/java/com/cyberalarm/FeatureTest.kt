package com.cyberalarm

import com.cyberalarm.alarm.NextTrigger
import com.cyberalarm.challenge.BreachRules
import com.cyberalarm.challenge.BreachState
import com.cyberalarm.challenge.ChallengeSpec
import com.cyberalarm.challenge.ChallengeType
import com.cyberalarm.challenge.Difficulty
import com.cyberalarm.cred.GigBoard
import com.cyberalarm.cred.Rank
import com.cyberalarm.cred.Scoring
import com.cyberalarm.data.Alarm
import com.cyberalarm.data.WakeSession
import com.cyberalarm.settings.JackInTag
import com.cyberalarm.settings.TagKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random

class SkipAndHolidayTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val now = ZonedDateTime.of(2026, 10, 22, 5, 0, 0, 0, zone) // Thursday
    private val weekdays = Alarm(hour = 7, minute = 0, repeatDays = Alarm.WEEKDAYS)

    @Test
    fun skipNextJumpsOneOccurrence() {
        val thu = NextTrigger.compute(weekdays, now)
        val skipped = weekdays.copy(skipAt = thu.toInstant().toEpochMilli())
        assertEquals(ZonedDateTime.of(2026, 10, 23, 7, 0, 0, 0, zone), NextTrigger.compute(skipped, now))
        assertTrue(NextTrigger.isSkipping(skipped, now))
        // Once the skipped occurrence has passed, the skip is stale and does nothing.
        val later = ZonedDateTime.of(2026, 10, 23, 8, 0, 0, 0, zone)
        assertFalse(NextTrigger.isSkipping(skipped, later))
        assertEquals(ZonedDateTime.of(2026, 10, 26, 7, 0, 0, 0, zone), NextTrigger.compute(skipped, later))
    }

    @Test
    fun holidayPausesRepeatingButNotOneShot() {
        val until = LocalDate.of(2026, 10, 30).atStartOfDay(zone).toInstant().toEpochMilli() // Fri 00:00
        assertEquals(ZonedDateTime.of(2026, 10, 30, 7, 0, 0, 0, zone), NextTrigger.compute(weekdays, now, until))
        val once = Alarm(hour = 7, minute = 0)
        assertEquals(ZonedDateTime.of(2026, 10, 22, 7, 0, 0, 0, zone), NextTrigger.compute(once, now, until))
    }
}

class EscalationTest {
    @Test
    fun escalationHardensChain() {
        val chain = listOf(
            ChallengeSpec(ChallengeType.MATH, Difficulty.EASY, 2),
            ChallengeSpec(ChallengeType.BREACH, Difficulty.HARD, 1),
            ChallengeSpec(ChallengeType.JACK_IN),
        )
        assertEquals(chain, ChallengeSpec.escalate(chain, 0))
        val e = ChallengeSpec.escalate(chain, 1)
        assertEquals(ChallengeSpec(ChallengeType.MATH, Difficulty.MEDIUM, 3), e[0])
        assertEquals(ChallengeSpec(ChallengeType.BREACH, Difficulty.LEGEND, 2), e[1])
        assertEquals(chain[2], e[2])
        // Non-breach challenges cap at HARD, counts at MAX_COUNT.
        val e5 = ChallengeSpec.escalate(chain, 5)
        assertEquals(Difficulty.HARD, e5[0].difficulty)
        assertEquals(ChallengeSpec.MAX_COUNT, e5[0].count)
        assertTrue(ChallengeSpec.escalate(emptyList(), 1).isNotEmpty())
    }

    @Test
    fun legendBreachIsSolvable() {
        repeat(30) { seed ->
            val puzzle = BreachRules.generate(Difficulty.LEGEND, Random(seed))
            var state = BreachState(puzzle)
            val path = findPath(state) ?: error("seed $seed unsolvable")
            path.forEach { state = state.pick(it) }
            assertTrue(state.solved)
        }
    }

    /** DFS over legal picks. */
    private fun findPath(state: BreachState, path: List<Int> = emptyList()): List<Int>? {
        if (state.solved) return path
        if (state.failed) return null
        for (cell in 0 until state.puzzle.size * state.puzzle.size) {
            if (!state.canPick(cell)) continue
            findPath(state.pick(cell), path + cell)?.let { return it }
        }
        return null
    }
}

class ScoringTest {
    private fun session(snoozes: Int = 0, mistakes: Int = 0, seconds: Long = 60, reRings: Int = 0) =
        WakeSession(alarmId = 1, label = "X", firedAt = 0, dismissedAt = seconds * 1000, snoozes = snoozes, mistakes = mistakes, reRings = reRings)

    @Test
    fun cleanFastWakePaysMost() {
        val lines = Scoring.dismissal(session(), streak = 3)
        assertEquals(Scoring.WAKE + Scoring.FAST_WAKE + 3 * Scoring.STREAK_STEP, lines.sumOf { it.first })
    }

    @Test
    fun snoozesAndMistakesCost() {
        val lines = Scoring.dismissal(session(snoozes = 2, mistakes = 3, seconds = 900), streak = 0)
        assertEquals(Scoring.WAKE + 2 * Scoring.SNOOZE + 3 * Scoring.MISTAKE, lines.sumOf { it.first })
    }

    @Test
    fun streakCountsCleanWakesNewestFirst() {
        val list = listOf(session(), session(), session(snoozes = 1), session())
        assertEquals(2, Scoring.streak(list))
        // A session still ringing (not dismissed) doesn't break it.
        assertEquals(2, Scoring.streak(listOf(WakeSession(alarmId = 1, label = "", firedAt = 0)) + list))
        assertEquals(0, Scoring.streak(listOf(session(reRings = 1)) + list))
    }

    @Test
    fun ranks() {
        assertEquals(Rank.STREET_KID, Rank.of(0))
        assertEquals(Rank.PUNK, Rank.of(300))
        assertEquals(Rank.LEGEND, Rank.of(99_999))
        assertEquals(Rank.STREET_KID, Rank.of(-200))
        assertEquals(0.5f, Rank.progress(150), 0.001f)
        assertEquals(1f, Rank.progress(5_000), 0.001f)
    }

    @Test
    fun gigIsStablePerDay() {
        val d = LocalDate.of(2026, 10, 22)
        assertEquals(GigBoard.today(d), GigBoard.today(d))
        val week = (0L..6L).map { GigBoard.today(d.plusDays(it)).fixer }.toSet()
        assertTrue("gigs should rotate", week.size > 1)
    }
}

class JackInTagTest {
    @Test
    fun fingerprintAndEncoding() {
        val fp = JackInTag.fingerprint(TagKind.CODE, "https://example.com/kitchen")
        assertEquals(64, fp.length)
        assertNotEquals(fp, JackInTag.fingerprint(TagKind.NFC, "https://example.com/kitchen"))
        val tag = JackInTag(TagKind.CODE, fp, "NODE_01")
        assertEquals(tag, JackInTag.decode(tag.encode()))
    }
}
