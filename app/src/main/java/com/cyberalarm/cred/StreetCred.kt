package com.cyberalarm.cred

import com.cyberalarm.challenge.ChallengeSpec
import com.cyberalarm.challenge.ChallengeType
import com.cyberalarm.challenge.Difficulty
import com.cyberalarm.data.WakeSession
import java.time.LocalDate
import kotlin.random.Random

/** Street Cred ranks, by eddie balance. */
enum class Rank(val title: String, val minEddies: Int) {
    STREET_KID("STREET KID", 0),
    PUNK("PUNK", 300),
    SOLO("SOLO", 900),
    MERC("MERC", 2_000),
    LEGEND("LEGEND", 4_000);

    val next: Rank? get() = entries.getOrNull(ordinal + 1)

    companion object {
        fun of(eddies: Int): Rank = entries.lastOrNull { eddies >= it.minEddies } ?: STREET_KID

        /** 0..1 progress from this rank towards the next one (1 at LEGEND). */
        fun progress(eddies: Int): Float {
            val r = of(eddies)
            val n = r.next ?: return 1f
            return ((eddies - r.minEddies).toFloat() / (n.minEddies - r.minEddies)).coerceIn(0f, 1f)
        }
    }
}

/** Color themes unlocked by rank. primary = the "yellow" signal color, secondary = the "cyan". */
enum class CrewTheme(val title: String, val rank: Rank, val primary: Long, val highlight: Long, val secondary: Long) {
    SAMURAI("SAMURAI", Rank.STREET_KID, 0xFFF0FF00, 0xFFFBFFB8, 0xFF00E5FF),
    MILITECH("MILITECH", Rank.PUNK, 0xFFFF8A00, 0xFFFFD9AE, 0xFF8FD3C7),
    NETWATCH("NETWATCH", Rank.SOLO, 0xFF4DA6FF, 0xFFD4EAFF, 0xFFC77DFF),
    ARASAKA("ARASAKA", Rank.MERC, 0xFFF2F2F2, 0xFFFFFFFF, 0xFFFF5A6A);

    companion object {
        fun of(name: String): CrewTheme = entries.find { it.name == name } ?: SAMURAI
    }
}

/** Pure scoring rules, kept free of Android/Room so they are unit-testable. */
object Scoring {
    const val WAKE = 100
    const val FAST_WAKE = 50
    const val FAST_WAKE_SECONDS = 120
    const val SNOOZE = -20
    const val MISTAKE = -5
    const val JACK_IN = 30
    const val STREAK_STEP = 10
    const val CHECK_PASSED = 25
    const val CHECK_FAILED = -75
    const val RE_DISMISS = 10

    /** Ledger lines for a dismissal. [streak] = clean wakes in a row including this one (0 if dirty). */
    fun dismissal(session: WakeSession, streak: Int): List<Pair<Int, String>> = buildList {
        if (session.reRings > 0) {
            // Back up after falling asleep again: a token amount; the -75 already happened.
            add(RE_DISMISS to "RE-DISMISSED")
            return@buildList
        }
        add(WAKE to "WAKE")
        if (session.secondsToWake <= FAST_WAKE_SECONDS) add(FAST_WAKE to "FAST WAKE")
        if (session.snoozes > 0) add(SNOOZE * session.snoozes to "SNOOZED x${session.snoozes}")
        if (session.mistakes > 0) add(MISTAKE * session.mistakes.coerceAtMost(10) to "MISTAKES x${session.mistakes}")
        if (session.usedJackIn) add(JACK_IN to "JACKED IN")
        if (streak > 1) add(STREAK_STEP * streak.coerceAtMost(10) to "STREAK x$streak")
    }

    /** Clean wakes in a row, newest first; sessions still ringing are ignored. */
    fun streak(newestFirst: List<WakeSession>): Int =
        newestFirst.filter { it.dismissed }.takeWhile { it.clean }.size
}

/** A fixer's daily job: one bonus challenge chain, the same for everybody on a given date. */
data class Gig(val id: String, val fixer: String, val message: String, val specs: List<ChallengeSpec>, val reward: Int)

object GigBoard {
    private data class Template(val fixer: String, val message: String, val spec: ChallengeSpec, val reward: Int)

    private val templates = listOf(
        Template("REGINA JONES", "Got a corpo subnet that needs cracking, choom. Two nodes, clean upload.", ChallengeSpec(ChallengeType.BREACH, Difficulty.HARD, 2), 150),
        Template("WAKAKO OKADA", "A Tyger Claws AI thinks it can't be beaten at Connect 4. Prove it wrong. Twice.", ChallengeSpec(ChallengeType.CONNECT4, Difficulty.MEDIUM, 2), 140),
        Template("DEXTER DESHAWN", "Need someone with a sharp memory to verify a data shard. Don't gonk it.", ChallengeSpec(ChallengeType.MEMORY, Difficulty.HARD, 2), 130),
        Template("ROGUE", "Count the eddies from last night's job. Fast, and no rounding errors.", ChallengeSpec(ChallengeType.MATH, Difficulty.HARD, 4), 120),
        Template("MR. HANDS", "My inventory's a mess. Match the crates to the manifests, would you?", ChallengeSpec(ChallengeType.MIX_MATCH, Difficulty.HARD, 2), 120),
        Template("PADRE", "Heywood needs a runner on foot. Get those legs moving, hermano.", ChallengeSpec(ChallengeType.STEPS, Difficulty.MEDIUM, 1), 110),
        Template("MUAMAR REYES", "Nomad convoy got a lockdown on its comms. Three quick breaches.", ChallengeSpec(ChallengeType.BREACH, Difficulty.MEDIUM, 3), 140),
    )

    fun today(date: LocalDate = LocalDate.now()): Gig {
        val t = templates[Random(date.toEpochDay()).nextInt(templates.size)]
        return Gig(id = "GIG:$date", fixer = t.fixer, message = t.message, specs = listOf(t.spec), reward = t.reward)
    }
}
