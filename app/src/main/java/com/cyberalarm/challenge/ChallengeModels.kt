package com.cyberalarm.challenge

/** All challenge kinds the engine knows about. Add a value here + a [Challenge] impl to extend. */
enum class ChallengeType(val title: String, val blurb: String) {
    MATH("MATH_PROTOCOL", "Solve arithmetic / algebra. N correct in a row."),
    MIX_MATCH("MIX_MATCH", "Pair symbols, words and colors correctly."),
    CONNECT4("CONNECT_4", "Beat the minimax AI at Connect 4."),
    MEMORY("SEQUENCE_RECALL", "Watch the grid, repeat the sequence."),
    STEPS("STEP_DRIVE", "Get up. Walk (or shake) the required steps."),
}

enum class Difficulty(val short: String) {
    EASY("EASY"),
    MEDIUM("MED"),
    HARD("HARD"),
}

/**
 * One configured challenge in an alarm's dismissal chain.
 * [count] means "repetitions": problems in a row for math, rounds for match/memory, wins for
 * Connect 4, and a multiplier on the step target for steps.
 */
data class ChallengeSpec(
    val type: ChallengeType,
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val count: Int = 1,
) {
    fun summary(): String = "${type.title} x$count [${difficulty.short}]"

    companion object {
        const val MAX_COUNT = 5

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
