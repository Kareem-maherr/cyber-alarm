package com.cyberalarm.challenge

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.unit.dp
import com.cyberalarm.ui.components.chamferPath
import com.cyberalarm.ui.components.neonStroke
import com.cyberalarm.ui.theme.Hud
import com.cyberalarm.ui.theme.HudType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Simon-style: watch tiles flash in order, then repeat. [ChallengeSpec.count] = rounds. */
class MemoryChallenge(override val spec: ChallengeSpec) : Challenge {
    @Composable
    override fun Content(onPassed: () -> Unit) = MemoryUi(spec, onPassed)
}

object MemoryRules {
    fun gridSize(d: Difficulty) = if (d == Difficulty.HARD) 4 else 3
    fun length(d: Difficulty) = when (d) {
        Difficulty.EASY -> 4
        Difficulty.MEDIUM -> 5
        Difficulty.HARD -> 7
    }
    fun flashMillis(d: Difficulty) = when (d) {
        Difficulty.EASY -> 650L
        Difficulty.MEDIUM -> 480L
        Difficulty.HARD -> 340L
    }

    /** No tile repeats twice in a row (that would be invisible as a "flash"). */
    fun sequence(d: Difficulty, random: Random = Random.Default): List<Int> {
        val cells = gridSize(d) * gridSize(d)
        val out = mutableListOf<Int>()
        while (out.size < length(d)) {
            val next = random.nextInt(cells)
            if (out.lastOrNull() != next) out += next
        }
        return out
    }
}

private enum class Phase { SHOW, INPUT, FAIL, CLEAR }

@Composable
private fun MemoryUi(spec: ChallengeSpec, onPassed: () -> Unit) {
    val d = spec.difficulty
    val grid = MemoryRules.gridSize(d)
    var sequence by remember { mutableStateOf(MemoryRules.sequence(d)) }
    var attempt by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(Phase.SHOW) }
    var lit by remember { mutableIntStateOf(-1) }
    var tapped by remember { mutableIntStateOf(-1) }
    var position by remember { mutableIntStateOf(0) }
    var rounds by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(sequence, attempt) {
        phase = Phase.SHOW
        position = 0
        delay(800)
        for (cell in sequence) {
            lit = cell
            delay(MemoryRules.flashMillis(d))
            lit = -1
            delay(160)
        }
        phase = Phase.INPUT
    }

    fun onTap(cell: Int) {
        if (phase != Phase.INPUT) return
        tapped = cell
        scope.launch { delay(180); if (tapped == cell) tapped = -1 }
        if (sequence[position] == cell) {
            position++
            if (position == sequence.size) {
                rounds++
                if (rounds >= spec.count) {
                    onPassed()
                } else {
                    phase = Phase.CLEAR
                    scope.launch { delay(700); sequence = MemoryRules.sequence(d) }
                }
            }
        } else {
            phase = Phase.FAIL
            scope.launch { delay(900); sequence = MemoryRules.sequence(d); attempt++ }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        val (text, color) = when (phase) {
            Phase.SHOW -> "OBSERVE SEQUENCE..." to Hud.Cyan
            Phase.INPUT -> "REPEAT // ${position}/${sequence.size}" to Hud.Yellow
            Phase.FAIL -> "SEQUENCE CORRUPTED // RETRY" to Hud.Red
            Phase.CLEAR -> "SEQUENCE ACCEPTED" to Hud.Yellow
        }
        Row {
            Text(text, style = HudType.small, color = color, modifier = Modifier.weight(1f))
            Text("ROUND ${rounds + 1}/${spec.count}", style = HudType.small, color = Hud.TextDim)
        }
        Spacer(Modifier.height(6.dp))
        SegmentBar(rounds, spec.count)
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (r in 0 until grid) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (c in 0 until grid) {
                        val cell = r * grid + c
                        val target = when {
                            lit == cell -> Hud.Yellow
                            tapped == cell -> if (phase == Phase.FAIL) Hud.Red else Hud.Cyan
                            phase == Phase.FAIL -> Hud.Red.copy(alpha = 0.5f)
                            else -> Hud.GreyDim
                        }
                        val tile by animateColorAsState(target, tween(90), label = "memTile")
                        val active = lit == cell || tapped == cell
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .drawWithCache {
                                    val path = chamferPath(size, 10.dp.toPx(), 0f, 10.dp.toPx(), 0f)
                                    onDrawBehind {
                                        drawPath(path, tile.copy(alpha = if (active) 0.85f else 0.08f))
                                        neonStroke(path, if (active) tile else Hud.Grey, 1.5.dp.toPx(), glow = active)
                                    }
                                }
                                .clickable(remember { MutableInteractionSource() }, null) { onTap(cell) },
                        )
                    }
                }
            }
        }
    }
}
