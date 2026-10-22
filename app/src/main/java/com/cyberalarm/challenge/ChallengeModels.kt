package com.cyberalarm.challenge

/** All challenge kinds the engine knows about. Add a value here + a [Challenge] impl to extend. */
enum class ChallengeType(val title: String, val blurb: String) {
    MATH("MATH_PROTOCOL", "Addition only. A wrong answer keeps the question."),
    MIX_MATCH("MIX_MATCH", "Pair symbols, words and colors correctly."),
    CONNECT4("CONNECT_4", "Beat the minimax AI at Connect 4."),
    MEMORY("SEQUENCE_RECALL", "Watch the grid, repeat the sequence."),
    STEPS("STEP_DRIVE", "Get up. Walk (or shake) the required steps."),
    BREACH("BREACH_PROTOCOL", "Hack the code matrix: alternate row/column picks to upload the sequence."),
    JACK_IN("JACK_IN", "Walk to a registered QR / barcode / NFC tag and scan it. The alarm can go quiet for 5 min while you do."),
}

enum class Difficulty(val short: String) {
    EASY("EASY"),
    MEDIUM("MED"),
    HARD("HARD"),
    /** Breach only, unlocked at MERC rank. Everything else treats it as HARD. */
    LEGEND("LGND"),
}

/**
 * One configured challenge in an alarm's dismissal chain.
 * [count] means "repetitions": problems to solve for math, nodes to breach for breach protocol,
 * rounds for match/memory, wins for Connect 4, and a multiplier on the step target for steps.
 */
data class ChallengeSpec(
    val type: ChallengeType,
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val count: Int = 1,
) {
    fun summary(): String = if (type == ChallengeType.JACK_IN) type.title else "${type.title} x$count [${difficulty.short}]"

    companion object {
        const val MAX_COUNT = 5

        /**
         * The chain for an escalated re-ring: every link one difficulty step harder and [level] more
         * repetitions. An empty chain (hold to dismiss) becomes math.
         */
        fun escalate(list: List<ChallengeSpec>, level: Int): List<ChallengeSpec> {
            if (level <= 0) return list
            val base = list.ifEmpty { listOf(ChallengeSpec(ChallengeType.MATH, Difficulty.MEDIUM, 2)) }
            return base.map { spec ->
                if (spec.type == ChallengeType.JACK_IN) return@map spec
                val cap = if (spec.type == ChallengeType.BREACH) Difficulty.LEGEND else Difficulty.HARD
                val diff = Difficulty.entries[(spec.difficulty.ordinal + level).coerceAtMost(cap.ordinal)]
                spec.copy(difficulty = diff, count = (spec.count + level).coerceAtMost(MAX_COUNT))
            }
        }

        /** Compact persistence format: `MATH:MEDIUM:3|CONNECT4:EASY:1`. */
        fun encode(list: List<ChallengeSpec>): String =
            list.joinToString("|") { "${it.type.name}:${it.difficulty.name}:${it.count}" }

        fun decode(raw: String?): List<ChallengeSpec> =
            raw.orEmpty().split("|").filter { it.isNotBlank() }.mapNotNull { token ->
                val parts = token.split(":")
                val type = parts.getOrNull(0)?.let { n -> ChallengeType.entries.find { it.name == n } }
                    ?: return@mapNotNull null
                val diff = parts.getOrNull(1)?.let { n -> Difficulty.entries.find { it.name == n } }
                    ?: Difficulty.MEDIUM
                val count = parts.getOrNull(2)?.toIntOrNull()?.coerceIn(1, MAX_COUNT) ?: 1
                ChallengeSpec(type, diff, count)
            }
    }
}
