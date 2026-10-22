package com.cyberalarm

import com.cyberalarm.alarm.NextTrigger
import com.cyberalarm.challenge.BreachPuzzle
import com.cyberalarm.challenge.BreachRules
import com.cyberalarm.challenge.BreachState
import com.cyberalarm.challenge.ChallengeSpec
import com.cyberalarm.challenge.ChallengeType
import com.cyberalarm.challenge.Connect4Ai
import com.cyberalarm.challenge.Connect4Board
import com.cyberalarm.challenge.Connect4Board.Companion.AI
import com.cyberalarm.challenge.Connect4Board.Companion.HUMAN
import com.cyberalarm.challenge.Difficulty
import com.cyberalarm.challenge.MathProblemGenerator
import com.cyberalarm.challenge.MemoryRules
import com.cyberalarm.data.Alarm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random

class NextTriggerTest {
    private val zone = ZoneId.of("Europe/Berlin")

    @Test
    fun oneShotLaterToday() {
        val now = ZonedDateTime.of(2026, 10, 22, 5, 0, 0, 0, zone) // Thursday
        val t = NextTrigger.compute(Alarm(hour = 6, minute = 30), now)
        assertEquals(ZonedDateTime.of(2026, 10, 22, 6, 30, 0, 0, zone), t)
    }

    @Test
    fun oneShotAlreadyPassedGoesToTomorrow() {
        val now = ZonedDateTime.of(2026, 10, 22, 6, 30, 0, 0, zone)
        val t = NextTrigger.compute(Alarm(hour = 6, minute = 30), now)
        assertEquals(23, t.dayOfMonth)
    }

    @Test
    fun weekdaysSkipWeekend() {
        val friday = ZonedDateTime.of(2026, 10, 23, 7, 0, 0, 0, zone)
        val t = NextTrigger.compute(Alarm(hour = 6, minute = 30, repeatDays = Alarm.WEEKDAYS), friday)
        assertEquals(DayOfWeek.MONDAY, t.dayOfWeek)
        assertEquals(26, t.dayOfMonth)
    }

    @Test
    fun keepsWallClockAcrossDstChange() {
        // Berlin falls back on Sun 25 Oct 2026.
        val sat = ZonedDateTime.of(2026, 10, 24, 8, 0, 0, 0, zone)
        val t = NextTrigger.compute(Alarm(hour = 7, minute = 0, repeatDays = Alarm.dayBit(DayOfWeek.SUNDAY)), sat)
        assertEquals(7, t.hour)
        assertEquals(25, t.dayOfMonth)
    }
}

class ChallengeSpecTest {
    @Test
    fun encodeDecodeRoundTrip() {
        val list = listOf(
            ChallengeSpec(ChallengeType.MATH, Difficulty.HARD, 3),
            ChallengeSpec(ChallengeType.CONNECT4, Difficulty.EASY, 1),
        )
        assertEquals(list, ChallengeSpec.decode(ChallengeSpec.encode(list)))
    }

    @Test
    fun decodeIgnoresGarbageAndClamps() {
        assertEquals(
            listOf(ChallengeSpec(ChallengeType.STEPS, Difficulty.MEDIUM, ChallengeSpec.MAX_COUNT)),
            ChallengeSpec.decode("NOPE:EASY:1|STEPS:???:99|"),
        )
    }
}

class MathTest {
    @Test
    fun additionOnly() {
        Difficulty.entries.forEach { d ->
            val gen = MathProblemGenerator(d, Random(7))
            repeat(500) {
                val p = gen.next()
                assertTrue(p.text, p.text.all { it.isDigit() || it == ' ' || it == '+' })
                assertTrue("${p.text} -> ${p.answer}", p.answer > 0)
                assertEquals(p.text, p.text.split(" + ").sumOf { it.toInt() }, p.answer)
            }
        }
    }
}

class BreachTest {
    @Test
    fun generatedPuzzlesAreSolvableAlongThePlantedPath() {
        Difficulty.entries.forEach { d ->
            repeat(200) { seed ->
                val rnd = Random(seed)
                val cfg = BreachRules.config(d)
                // Re-derive the same path the generator planted by replaying the RNG.
                val puzzle = BreachRules.generate(d, Random(seed))
                val path = BreachRules.solutionPath(cfg.size, cfg.targetLength, rnd)
                var state = BreachState(puzzle)
                path.forEach { cell ->
                    assertTrue("illegal planted pick $cell", state.canPick(cell))
                    state = state.pick(cell)
                }
                assertTrue(state.solved)
                assertEquals(cfg.targetLength, state.progress)
            }
        }
    }

    @Test
    fun picksAlternateRowThenColumnAndCannotReuseCells() {
        val grid = List(25) { "1C" }
        val state = BreachState(BreachPuzzle(5, grid, listOf("55", "55"), 6))
        assertTrue(state.canPick(3)) // row 0
        assertTrue(!state.canPick(5)) // row 1 is not active yet
        val s1 = state.pick(3) // now column 3 is active
        assertTrue(s1.canPick(3 + 5 * 2))
        assertTrue(!s1.canPick(4))
        assertTrue(!s1.canPick(3)) // used
        val s2 = s1.pick(13) // row 2 active
        assertTrue(s2.canPick(10) && !s2.canPick(8))
    }

    @Test
    fun fullBufferWithoutSequenceFails() {
        val grid = List(25) { "1C" }
        var state = BreachState(BreachPuzzle(5, grid, listOf("55", "55"), 2))
        state = state.pick(0).pick(5)
        assertTrue(state.failed && !state.solved)
    }
}

class Connect4Test {
    @Test
    fun detectsAllWinDirections() {
        var b = Connect4Board()
        repeat(4) { b = b.drop(0, HUMAN) }
        assertEquals(HUMAN, b.winner())

        b = Connect4Board()
        (0..3).forEach { b = b.drop(it, AI) }
        assertEquals(AI, b.winner())
        assertEquals(69, Connect4Board.WINDOWS.size)
    }

    @Test
    fun aiTakesImmediateWinAndBlocks() {
        val settings = Connect4Ai.settingsFor(Difficulty.HARD)
        var b = Connect4Board()
        repeat(3) { b = b.drop(6, AI) }
        assertEquals(6, Connect4Ai.bestMove(b, settings))

        // Human threatens 1-2-3 with 0 already taken by the AI: 4 is the only block.
        b = Connect4Board().drop(0, AI)
        listOf(1, 2, 3).forEach { b = b.drop(it, HUMAN) }
        b = b.drop(6, AI)
        assertEquals(4, Connect4Ai.bestMove(b, settings))
    }

    @Test
    fun easyAiStillPlaysLegalMoves() {
        val settings = Connect4Ai.settingsFor(Difficulty.EASY)
        val rnd = Random(1)
        var b = Connect4Board()
        var turn = HUMAN
        while (b.winner() == Connect4Board.EMPTY) {
            val col = if (turn == HUMAN) b.validMoves().random(rnd) else Connect4Ai.bestMove(b, settings, rnd)
            assertTrue(b.canDrop(col))
            b = b.drop(col, turn)
            turn = if (turn == HUMAN) AI else HUMAN
        }
    }
}

class MemoryTest {
    @Test
    fun sequencesHaveNoImmediateRepeats() {
        repeat(100) {
            val s = MemoryRules.sequence(Difficulty.HARD, Random(it))
            assertEquals(MemoryRules.length(Difficulty.HARD), s.size)
            s.zipWithNext().forEach { (a, b) -> assertNotEquals(a, b) }
        }
    }
}
