package com.cyberalarm.challenge

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberalarm.ui.components.CyberCard
import com.cyberalarm.ui.components.chamferPath
import com.cyberalarm.ui.components.neonStroke
import com.cyberalarm.ui.components.pressScale
import com.cyberalarm.ui.theme.Hud
import com.cyberalarm.ui.theme.HudType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

data class MathProblem(val text: String, val answer: Int)

/** EASY: 2-digit +/-; MEDIUM: a×b±c; HARD: linear equations or a×b−c×d. */
class MathProblemGenerator(private val difficulty: Difficulty, private val random: Random = Random.Default) {
    private fun r(lo: Int, hi: Int) = random.nextInt(lo, hi + 1)

    fun next(): MathProblem = when (difficulty) {
        Difficulty.EASY -> {
            val a = r(12, 99)
            val b = r(11, 99)
            if (random.nextBoolean()) MathProblem("$a + $b", a + b)
            else MathProblem("${maxOf(a, b)} − ${minOf(a, b)}", maxOf(a, b) - minOf(a, b))
        }
        Difficulty.MEDIUM -> {
            val a = r(3, 12)
            val b = r(6, 19)
            val c = r(11, 99)
            if (random.nextBoolean()) MathProblem("$a × $b + $c", a * b + c)
            else MathProblem("$a × $b − $c", a * b - c)
        }
        Difficulty.HARD -> if (random.nextBoolean()) {
            val x = r(2, 19)
            val a = r(3, 12)
            val b = r(11, 99)
            MathProblem("${a}x + $b = ${a * x + b}\nx = ?", x)
        } else {
            val a = r(12, 29)
            val b = r(6, 19)
            val c = r(3, 12)
            val d = r(3, 12)
            MathProblem("$a × $b − $c × $d", a * b - c * d)
        }
    }
}

class MathChallenge(override val spec: ChallengeSpec) : Challenge {
    @Composable
    override fun Content(onPassed: () -> Unit) = MathChallengeUi(spec, onPassed)
}

@Composable
private fun MathChallengeUi(spec: ChallengeSpec, onPassed: () -> Unit) {
    val generator = remember { MathProblemGenerator(spec.difficulty) }
    var problem by remember { mutableStateOf(generator.next()) }
    var input by remember { mutableStateOf("") }
    var streak by remember { mutableIntStateOf(0) }
    var feedback by remember { mutableStateOf<Color?>(null) }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun submit() {
        val value = input.toIntOrNull() ?: return
        if (value == problem.answer) {
            streak++
            if (streak >= spec.count) {
                onPassed()
                return
            }
            feedback = Hud.Yellow
        } else {
            streak = 0
            feedback = Hud.Red
            scope.launch {
                for (x in listOf(18f, -14f, 10f, -6f, 0f)) shake.animateTo(x, tween(45))
            }
        }
        scope.launch {
            delay(350)
            feedback = null
        }
        problem = generator.next()
        input = ""
    }

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("STREAK $streak/${spec.count}", style = HudType.small, color = Hud.Cyan, modifier = Modifier.weight(1f))
            if (feedback == Hud.Red) Text("ERR // STREAK RESET", style = HudType.small, color = Hud.Red)
            if (feedback == Hud.Yellow) Text("OK", style = HudType.small, color = Hud.Yellow)
        }
        Spacer(Modifier.height(6.dp))
        SegmentBar(streak, spec.count)
        Spacer(Modifier.height(12.dp))
        CyberCard(
            modifier = Modifier.fillMaxWidth().offset { IntOffset(shake.value.dp.roundToPx(), 0) },
            accent = feedback ?: Hud.Yellow,
            stripes = false,
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(problem.text, style = HudType.cardTime.copy(fontSize = 32.sp), color = Hud.Text, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (input.isEmpty()) "_" else input,
                    style = HudType.cardTime.copy(fontSize = 40.sp),
                    color = Hud.Yellow,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Keypad(
            onKey = { k -> if (input.length < 6) input += k },
            onMinus = { input = if (input.startsWith("-")) input.drop(1) else "-$input" },
            onDelete = { input = input.dropLast(1) },
            onEnter = ::submit,
        )
    }
}

@Composable
fun Keypad(onKey: (String) -> Unit, onMinus: () -> Unit, onDelete: () -> Unit, onEnter: () -> Unit) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("±", "0", "DEL"))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k ->
                    KeyCap(k, Modifier.weight(1f), color = if (k.length > 1 || k == "±") Hud.Cyan else Hud.Yellow) {
                        when (k) {
                            "DEL" -> onDelete()
                            "±" -> onMinus()
                            else -> onKey(k)
                        }
                    }
                }
            }
        }
        KeyCap("ENTER", Modifier.fillMaxWidth(), color = Hud.Yellow, filled = true, onClick = onEnter)
    }
}

@Composable
private fun KeyCap(text: String, modifier: Modifier, color: Color, filled: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction, 0.92f)
    Box(
        modifier
            .height(54.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawWithCache {
                val path = chamferPath(size, 8.dp.toPx(), 0f, 8.dp.toPx(), 0f)
                onDrawBehind {
                    if (filled) drawPath(path, color) else drawPath(path, color.copy(alpha = 0.05f))
                    neonStroke(path, color, 1.2.dp.toPx(), glow = false)
                }
            }
            .clickable(interaction, null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = HudType.title, color = if (filled) Hud.Bg else color)
    }
}
