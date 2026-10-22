package com.cyberalarm.challenge

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.cyberalarm.challenge.Connect4Board.Companion.AI
import com.cyberalarm.challenge.Connect4Board.Companion.COLS
import com.cyberalarm.challenge.Connect4Board.Companion.DRAW
import com.cyberalarm.challenge.Connect4Board.Companion.EMPTY
import com.cyberalarm.challenge.Connect4Board.Companion.HUMAN
import com.cyberalarm.challenge.Connect4Board.Companion.ROWS
import com.cyberalarm.ui.components.chamferPath
import com.cyberalarm.ui.components.neonStroke
import com.cyberalarm.ui.theme.Hud
import com.cyberalarm.ui.theme.HudType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pass condition: win [ChallengeSpec.count] games. A loss or a draw just starts a new game. */
class Connect4Challenge(override val spec: ChallengeSpec) : Challenge {
    @Composable
    override fun Content(onPassed: () -> Unit) = Connect4Ui(spec, onPassed)
}

@Composable
private fun Connect4Ui(spec: ChallengeSpec, onPassed: () -> Unit) {
    val settings = remember { Connect4Ai.settingsFor(spec.difficulty) }
    var board by remember { mutableStateOf(Connect4Board()) }
    var turn by remember { mutableIntStateOf(HUMAN) }
    var wins by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("YOUR MOVE // TAP A COLUMN") }
    var lastCell by remember { mutableIntStateOf(-1) }
    val drop = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val onMistake = LocalChallengeMistake.current
    val tick = com.cyberalarm.ui.components.rememberTick()

    fun play(col: Int, player: Int) {
        lastCell = board.landingRow(col) * COLS + col
        board = board.drop(col, player)
        scope.launch {
            drop.snapTo(0f)
            drop.animateTo(1f, tween(260, easing = FastOutLinearInEasing))
        }
    }

    LaunchedEffect(board, turn) {
        when (board.winner()) {
            HUMAN -> {
                wins++
                status = "PREEM // ROUND WON $wins/${spec.count}"
                delay(1_000)
                if (wins >= spec.count) {
                    onPassed()
                    return@LaunchedEffect
                }
            }
            AI -> {
                onMistake()
                status = "FLATLINED, CHOOM // REBOOTING BOARD"
                delay(1_600)
            }
            DRAW -> {
                status = "DRAW // REBOOTING BOARD"
                delay(1_400)
            }
            else -> {
                if (turn == AI) {
                    status = "AI COMPUTING..."
                    delay(380)
                    val snapshot = board
                    val col = withContext(Dispatchers.Default) { Connect4Ai.bestMove(snapshot, settings) }
                    play(col, AI)
                    turn = HUMAN
                    status = "YOUR MOVE"
                }
                return@LaunchedEffect
            }
        }
        board = Connect4Board()
        lastCell = -1
        turn = HUMAN
        status = "YOUR MOVE // TAP A COLUMN"
    }

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(status, style = HudType.small, color = if (status.startsWith("FLATLINED")) Hud.Red else Hud.Cyan, modifier = Modifier.weight(1f))
            Text("DEPTH ${settings.depth}", style = HudType.small, color = Hud.TextDim)
        }
        Spacer(Modifier.height(6.dp))
        SegmentBar(wins, spec.count)
        Spacer(Modifier.height(12.dp))
        val winLine = board.winningLine()?.toSet().orEmpty()
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(COLS / ROWS.toFloat())
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        val col = (pos.x / (size.width / COLS.toFloat())).toInt().coerceIn(0, COLS - 1)
                        if (turn == HUMAN && board.winner() == EMPTY && board.canDrop(col)) {
                            tick()
                            play(col, HUMAN)
                            turn = AI
                        }
                    }
                },
        ) {
            val frame = chamferPath(size, 14.dp.toPx(), 0f, 14.dp.toPx(), 0f)
            drawPath(frame, Hud.BgRaised)
            neonStroke(frame, Hud.Cyan, 1.5.dp.toPx())
            val cw = size.width / COLS
            val ch = size.height / ROWS
            val r = minOf(cw, ch) * 0.36f
            for (row in 0 until ROWS) for (col in 0 until COLS) {
                val index = row * COLS + col
                val center = Offset(cw * (col + 0.5f), ch * (row + 0.5f))
                val p = board[row, col]
                if (p == EMPTY || index == lastCell) {
                    drawCircle(Hud.GreyDim, r, center, style = Stroke(1.5.dp.toPx()))
                }
                if (p == EMPTY) continue
                val drawAt = if (index == lastCell) Offset(center.x, -r + (center.y + r) * drop.value) else center
                val color = if (p == HUMAN) Hud.Yellow else Hud.Red
                drawCircle(color.copy(alpha = 0.18f), r * 1.35f, drawAt)
                drawCircle(color, r, drawAt)
                if (index in winLine) drawCircle(Hud.Text, r * 1.15f, drawAt, style = Stroke(2.5.dp.toPx()))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row {
            Text("■ YOU", style = HudType.small, color = Hud.Yellow)
            Spacer(Modifier.weight(1f))
            Text("■ SYSTEM", style = HudType.small, color = Hud.Red)
        }
    }
}
