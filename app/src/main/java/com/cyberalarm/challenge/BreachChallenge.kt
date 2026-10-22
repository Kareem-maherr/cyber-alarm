package com.cyberalarm.challenge

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.cyberalarm.feedback.rememberCyberFeedback
import com.cyberalarm.ui.components.CyberButton
import com.cyberalarm.ui.components.chamferPath
import com.cyberalarm.ui.theme.DisplayFont
import com.cyberalarm.ui.theme.Hud
import com.cyberalarm.ui.theme.HudType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Breach Protocol (Cyberpunk 2077-style code matrix).
 *
 * Rules: the first code is picked from the top row; after that picks alternate between the
 * COLUMN of the last pick and the ROW of the last pick. Each cell can be used once. The hack
 * succeeds when the required sequence appears, in order and contiguous, in the buffer; it fails
 * when the buffer is full (or no legal pick remains) and a fresh matrix is generated.
 */
class BreachPuzzle(val size: Int, val grid: List<String>, val target: List<String>, val bufferSize: Int) {
    fun code(cell: Int) = grid[cell]
}

object BreachRules {
    val CODES = listOf("1C", "55", "BD", "E9", "7A", "FF")

    data class Config(val size: Int, val targetLength: Int, val bufferSize: Int)

    fun config(d: Difficulty) = when (d) {
        Difficulty.EASY -> Config(size = 5, targetLength = 3, bufferSize = 6)
        Difficulty.MEDIUM -> Config(size = 5, targetLength = 4, bufferSize = 6)
        Difficulty.HARD -> Config(size = 6, targetLength = 5, bufferSize = 6)
        // Zero slack: every pick must count.
        Difficulty.LEGEND -> Config(size = 7, targetLength = 6, bufferSize = 6)
    }

    /** Random matrix with the target planted along a legal pick path, so it is always solvable. */
    fun generate(d: Difficulty, random: Random = Random.Default): BreachPuzzle {
        val cfg = config(d)
        val path = solutionPath(cfg.size, cfg.targetLength, random)
        val target = List(cfg.targetLength) { CODES.random(random) }
        val grid = MutableList(cfg.size * cfg.size) { CODES.random(random) }
        path.forEachIndexed { i, cell -> grid[cell] = target[i] }
        return BreachPuzzle(cfg.size, grid, target, cfg.bufferSize)
    }

    /** Distinct cells: start in row 0, then alternate column move / row move. */
    fun solutionPath(size: Int, length: Int, random: Random): List<Int> {
        while (true) {
            var row = 0
            var col = random.nextInt(size)
            val cells = mutableListOf(row * size + col)
            for (i in 1 until length) {
                if (i % 2 == 1) row = (0 until size).filter { it != row }.random(random)
                else col = (0 until size).filter { it != col }.random(random)
                cells += row * size + col
            }
            if (cells.toSet().size == cells.size) return cells
        }
    }
}

/** Immutable game state; [picks] are flat cell indices in pick order. */
data class BreachState(val puzzle: BreachPuzzle, val picks: List<Int> = emptyList()) {
    private val n get() = puzzle.size

    /** Even pick count = choose within a row, odd = within a column. */
    val pickInRow: Boolean get() = picks.size % 2 == 0

    /** Row index (when [pickInRow]) or column index of the active line. */
    val activeLine: Int
        get() = when {
            picks.isEmpty() -> 0
            pickInRow -> picks.last() / n
            else -> picks.last() % n
        }

    fun isOnActiveLine(cell: Int) = if (pickInRow) cell / n == activeLine else cell % n == activeLine

    fun canPick(cell: Int) = !solved && picks.size < puzzle.bufferSize && cell !in picks && isOnActiveLine(cell)

    fun pick(cell: Int): BreachState = if (canPick(cell)) copy(picks = picks + cell) else this

    val buffer: List<String> get() = picks.map(puzzle::code)

    val solved: Boolean get() = buffer.windowed(puzzle.target.size).any { it == puzzle.target }

    /** How many leading target codes are currently matched at the end of the buffer. */
    val progress: Int
        get() {
            if (solved) return puzzle.target.size
            for (k in minOf(puzzle.target.size - 1, buffer.size) downTo 1) {
                if (buffer.takeLast(k) == puzzle.target.take(k)) return k
            }
            return 0
        }

    val failed: Boolean
        get() = !solved && (picks.size >= puzzle.bufferSize || (0 until n * n).none(::canPick))
}

class BreachChallenge(override val spec: ChallengeSpec) : Challenge {
    @Composable
    override fun Content(onPassed: () -> Unit) = BreachUi(spec, onPassed)
}

@Composable
private fun BreachUi(spec: ChallengeSpec, onPassed: () -> Unit) {
    val feedback = rememberCyberFeedback()
    val scope = rememberCoroutineScope()
    val onMistake = LocalChallengeMistake.current
    var state by remember { mutableStateOf(BreachState(BreachRules.generate(spec.difficulty))) }
    var rounds by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("SELECT A CODE FROM THE HIGHLIGHTED ROW") }
    var locked by remember { mutableStateOf(false) }

    fun onCell(cell: Int) {
        if (locked || !state.canPick(cell)) return
        feedback.key()
        state = state.pick(cell)
        when {
            state.solved -> {
                feedback.success()
                rounds++
                locked = true
                status = "PREEM // DAEMON UPLOADED"
                scope.launch {
                    delay(900)
                    if (rounds >= spec.count) {
                        onPassed()
                    } else {
                        state = BreachState(BreachRules.generate(spec.difficulty))
                        status = "NEXT NODE // SELECT FROM THE TOP ROW"
                        locked = false
                    }
                }
            }
            state.failed -> {
                feedback.error()
                onMistake()
                locked = true
                status = "BREACH FAILED // REROUTING MATRIX"
                scope.launch {
                    delay(1_300)
                    state = BreachState(BreachRules.generate(spec.difficulty))
                    status = "SELECT A CODE FROM THE HIGHLIGHTED ROW"
                    locked = false
                }
            }
            else -> status = if (state.pickInRow) "NOW PICK FROM THE HIGHLIGHTED ROW" else "NOW PICK FROM THE HIGHLIGHTED COLUMN"
        }
    }

    val p = state.puzzle
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                status,
                style = HudType.small,
                color = if (status.startsWith("BREACH FAILED")) Hud.Red else Hud.Cyan,
                modifier = Modifier.weight(1f),
            )
            Text("NODE ${rounds + 1}/${spec.count}", style = HudType.small, color = Hud.TextDim)
        }
        Spacer(Modifier.height(6.dp))
        SegmentBar(rounds, spec.count)

        Spacer(Modifier.height(12.dp))
        Text("SEQUENCE REQUIRED", style = HudType.small, color = Hud.TextDim)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            p.target.forEachIndexed { i, code ->
                val done = i < state.progress
                Text(code, style = HudType.title, color = if (done) Hud.Yellow else Hud.Text.copy(alpha = 0.7f))
            }
        }

        Spacer(Modifier.height(10.dp))
        Text("BUFFER", style = HudType.small, color = Hud.TextDim)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(p.bufferSize) { i ->
                val code = state.buffer.getOrNull(i)
                Box(
                    Modifier
                        .size(40.dp, 34.dp)
                        .drawBehind {
                            drawRect(
                                if (code != null) Hud.Yellow else Hud.GreyDim,
                                style = Stroke(1.3.dp.toPx()),
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(code ?: "", style = HudType.chip, color = Hud.Yellow)
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        // The matrix. The active row/column is tinted; legal cells glow, used cells show [  ].
        Column(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    val path = chamferPath(size, 14.dp.toPx(), 0f, 14.dp.toPx(), 0f)
                    drawPath(path, Hud.BgRaised)
                    drawPath(path, Hud.Cyan.copy(alpha = 0.6f), style = Stroke(1.5.dp.toPx()))
                }
                .padding(8.dp),
        ) {
            for (r in 0 until p.size) {
                Row(Modifier.fillMaxWidth()) {
                    for (c in 0 until p.size) {
                        val cell = r * p.size + c
                        val used = cell in state.picks
                        val onLine = !locked && state.isOnActiveLine(cell)
                        val legal = !locked && state.canPick(cell)
                        val textColor by animateColorAsState(
                            when {
                                used -> Hud.GreyDim
                                legal -> Hud.Yellow
                                else -> Hud.Text.copy(alpha = 0.35f)
                            },
                            label = "breachCell",
                        )
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .background(if (onLine) Hud.Yellow.copy(alpha = 0.10f) else Color.Transparent)
                                .clickable(remember { MutableInteractionSource() }, null) { onCell(cell) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (used) "[  ]" else p.code(cell),
                                style = HudType.title.copy(fontFamily = DisplayFont),
                                color = textColor,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        CyberButton(
            "RESET BUFFER",
            {
                if (!locked && state.picks.isNotEmpty()) {
                    feedback.key()
                    state = BreachState(p)
                    status = "SELECT A CODE FROM THE HIGHLIGHTED ROW"
                }
            },
            color = Hud.Cyan,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
