package com.cyberalarm.challenge

import kotlin.random.Random

/** Immutable 7x6 Connect 4 board. Row 0 is the top. */
class Connect4Board(private val cells: IntArray = IntArray(ROWS * COLS)) {

    operator fun get(row: Int, col: Int): Int = cells[row * COLS + col]

    fun canDrop(col: Int): Boolean = col in 0 until COLS && cells[col] == EMPTY

    fun validMoves(): List<Int> = MOVE_ORDER.filter(::canDrop)

    /** Row the disc would land in, or -1 if the column is full. */
    fun landingRow(col: Int): Int {
        for (r in ROWS - 1 downTo 0) if (this[r, col] == EMPTY) return r
        return -1
    }

    fun drop(col: Int, player: Int): Connect4Board {
        val row = landingRow(col)
        require(row >= 0) { "Column $col is full" }
        val next = cells.copyOf()
        next[row * COLS + col] = player
        return Connect4Board(next)
    }

    /** HUMAN / AI if someone has four in a row, DRAW if the board is full, else EMPTY. */
    fun winner(): Int {
        winningLine()?.let { return this[it[0] / COLS, it[0] % COLS] }
        return if (cells.none { it == EMPTY }) DRAW else EMPTY
    }

    /** Flat indices of a winning four, or null. */
    fun winningLine(): IntArray? {
        for (w in WINDOWS) {
            val p = cells[w[0]]
            if (p != EMPTY && cells[w[1]] == p && cells[w[2]] == p && cells[w[3]] == p) return w
        }
        return null
    }

    internal fun cell(index: Int) = cells[index]

    companion object {
        const val ROWS = 6
        const val COLS = 7
        const val EMPTY = 0
        const val HUMAN = 1
        const val AI = 2
        const val DRAW = 3

        /** Center-first ordering makes alpha-beta prune much more. */
        val MOVE_ORDER = listOf(3, 2, 4, 1, 5, 0, 6)

        /** All 69 four-cell windows (horizontal, vertical, both diagonals) as flat indices. */
        val WINDOWS: List<IntArray> = buildList {
            for (r in 0 until ROWS) for (c in 0 until COLS) {
                for ((dr, dc) in listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)) {
                    val endR = r + dr * 3
                    val endC = c + dc * 3
                    if (endR in 0 until ROWS && endC in 0 until COLS) {
                        add(IntArray(4) { k -> (r + dr * k) * COLS + (c + dc * k) })
                    }
                }
            }
        }
    }
}

/** Minimax with alpha-beta pruning and a window-scoring heuristic. */
object Connect4Ai {

    data class Settings(val depth: Int, val randomness: Float)

    fun settingsFor(difficulty: Difficulty) = when (difficulty) {
        Difficulty.EASY -> Settings(depth = 2, randomness = 0.35f)
        Difficulty.MEDIUM -> Settings(depth = 4, randomness = 0.08f)
        Difficulty.HARD, Difficulty.LEGEND -> Settings(depth = 6, randomness = 0f)
    }

    fun bestMove(board: Connect4Board, settings: Settings, random: Random = Random.Default): Int {
        val moves = board.validMoves()
        require(moves.isNotEmpty()) { "No moves left" }
        // Never miss an immediate win, even when "being random".
        moves.firstOrNull { board.drop(it, Connect4Board.AI).winner() == Connect4Board.AI }?.let { return it }
        if (random.nextFloat() < settings.randomness) return moves.random(random)

        var bestScore = Int.MIN_VALUE
        var best = moves.first()
        var alpha = Int.MIN_VALUE + 1
        for (col in moves) {
            val score = minimax(board.drop(col, Connect4Board.AI), settings.depth - 1, alpha, Int.MAX_VALUE, false)
            if (score > bestScore) {
                bestScore = score
                best = col
            }
            alpha = maxOf(alpha, score)
        }
        return best
    }

    private fun minimax(board: Connect4Board, depth: Int, alphaIn: Int, betaIn: Int, maximizing: Boolean): Int {
        when (board.winner()) {
            Connect4Board.AI -> return WIN + depth // prefer faster wins
            Connect4Board.HUMAN -> return -WIN - depth
            Connect4Board.DRAW -> return 0
        }
        if (depth <= 0) return evaluate(board)
        var alpha = alphaIn
        var beta = betaIn
        if (maximizing) {
            var value = Int.MIN_VALUE
            for (col in board.validMoves()) {
                value = maxOf(value, minimax(board.drop(col, Connect4Board.AI), depth - 1, alpha, beta, false))
                alpha = maxOf(alpha, value)
                if (alpha >= beta) break
            }
            return value
        } else {
            var value = Int.MAX_VALUE
            for (col in board.validMoves()) {
                value = minOf(value, minimax(board.drop(col, Connect4Board.HUMAN), depth - 1, alpha, beta, true))
                beta = minOf(beta, value)
                if (alpha >= beta) break
            }
            return value
        }
    }

    /** Positive favours the AI. */
    fun evaluate(board: Connect4Board): Int {
        var score = 0
        for (r in 0 until Connect4Board.ROWS) if (board[r, 3] == Connect4Board.AI) score += 3
        for (w in Connect4Board.WINDOWS) {
            var ai = 0
            var human = 0
            for (i in w) when (board.cell(i)) {
                Connect4Board.AI -> ai++
                Connect4Board.HUMAN -> human++
            }
            val empty = 4 - ai - human
            score += when {
                ai == 3 && empty == 1 -> 5
                ai == 2 && empty == 2 -> 2
                human == 3 && empty == 1 -> -6
                human == 2 && empty == 2 -> -1
                else -> 0
            }
        }
        return score
    }

    private const val WIN = 1_000_000
}
