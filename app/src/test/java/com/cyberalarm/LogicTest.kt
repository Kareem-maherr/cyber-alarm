package com.cyberalarm

import com.cyberalarm.alarm.NextTrigger
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
    fun answersAreConsistent() {
        Difficulty.entries.forEach { d ->
            val gen = MathProblemGenerator(d, Random(7))
            repeat(200) {
                val p = gen.next()
                val text = p.text.substringBefore("\n")
                if ('x' in text) {
                    // a·x + b = c
                    val (lhs, c) = text.split(" = ")
                    val a = lhs.substringBefore("x").toInt()
                    val b = lhs.substringAfter("+ ").toInt()
                    assertEquals(c.toInt(), a * p.answer + b)
                } else {
                    val expr = text.replace("×", "*").replace("−", "-")
                    assertEquals(text, eval(expr), p.answer)
                }
            }
        }
    }

    /** Tiny evaluator for "a op b [op c [op d]]" with * before +/-. */
    private fun eval(expr: String): Int {
        val tokens = expr.split(" ")
        val terms = mutableListOf<Int>()
        val ops = mutableListOf<String>()
        var i = 0
        var cur = tokens[0].toInt()
        while (++i < tokens.size) {
            val op = tokens[i]
            val v = tokens[++i].toInt()
            if (op == "*") cur *= v else { terms += cur; ops += op; cur = v }
        }
        terms += cur
        var result = terms[0]
        ops.forEachIndexed { k, op -> result = if (op == "+") result + terms[k + 1] else result - terms[k + 1] }
        return result
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
