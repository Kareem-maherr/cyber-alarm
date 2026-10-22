package com.cyberalarm.challenge

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberalarm.feedback.CyberFeedback
import com.cyberalarm.feedback.rememberCyberFeedback
import com.cyberalarm.lingo.Lingo
import com.cyberalarm.ui.components.CyberCard
import com.cyberalarm.ui.components.buttonCutPath
import com.cyberalarm.ui.components.displayStyle
import com.cyberalarm.ui.components.pressScale
import com.cyberalarm.ui.theme.Hud
import com.cyberalarm.ui.theme.HudType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

data class MathProblem(val text: String, val answer: Int)

/**
 * Addition only, so every answer is a positive whole number.
 * EASY: a + b (2-digit). MEDIUM: three 2-digit terms. HARD: three 3-digit or four 2-digit terms.
 */
class MathProblemGenerator(private val difficulty: Difficulty, private val random: Random = Random.Default) {
    private fun r(lo: Int, hi: Int) = random.nextInt(lo, hi + 1)

    fun next(): MathProblem = when (difficulty) {
        Difficulty.EASY -> sum(2, 10, 99)
        Difficulty.MEDIUM -> sum(3, 12, 99)
        Difficulty.HARD, Difficulty.LEGEND -> if (random.nextBoolean()) sum(3, 105, 499) else sum(4, 21, 99)
    }

    private fun sum(terms: Int, lo: Int, hi: Int): MathProblem {
        val values = List(terms) { r(lo, hi) }
        return MathProblem(values.joinToString(" + "), values.sum())
    }
}

class MathChallenge(override val spec: ChallengeSpec) : Challenge {
    @Composable
    override fun Content(onPassed: () -> Unit) = MathChallengeUi(spec, onPassed)
}

/**
 * A wrong answer buzzes (sound + vibration) and keeps the SAME problem on screen until it is
 * solved; the streak is not reset. [ChallengeSpec.count] = problems to solve.
 */
@Composable
private fun MathChallengeUi(spec: ChallengeSpec, onPassed: () -> Unit) {
    val generator = remember { MathProblemGenerator(spec.difficulty) }
    val feedback = rememberCyberFeedback()
    val mistake = LocalChallengeMistake.current
    var problem by remember { mutableStateOf(generator.next()) }
    var input by remember { mutableStateOf("") }
    var solved by remember { mutableIntStateOf(0) }
    var feedbackColor by remember { mutableStateOf<Color?>(null) }
    var message by remember { mutableStateOf("") }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun flash(color: Color, text: String) {
        feedbackColor = color
        message = text
        scope.launch {
            delay(900)
            feedbackColor = null
        }
    }

    fun submit() {
        val value = input.toIntOrNull() ?: return
        if (value == problem.answer) {
            solved++
            feedback.success()
            if (solved >= spec.count) {
                onPassed()
                return
            }
            flash(Hud.Yellow, Lingo.pick(Lingo.rightAnswer))
            problem = generator.next()
        } else {
            feedback.error()
            mistake()
            flash(Hud.Red, Lingo.pick(Lingo.wrongAnswer))
            scope.launch {
                for (x in listOf(18f, -14f, 10f, -6f, 0f)) shake.animateTo(x, tween(45))
            }
        }
        input = ""
    }

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SOLVED $solved/${spec.count}", style = HudType.micro, color = Hud.Cyan, modifier = Modifier.weight(1f))
            feedbackColor?.let { Text(message, style = HudType.micro, color = it) }
        }
        Spacer(Modifier.height(6.dp))
        SegmentBar(solved, spec.count)
        Spacer(Modifier.height(12.dp))
        CyberCard(
            modifier = Modifier.fillMaxWidth().offset { IntOffset(shake.value.dp.roundToPx(), 0) },
            accent = feedbackColor ?: Hud.Yellow,
            cut = 22.dp,
            contentPadding = PaddingValues(top = 16.dp, bottom = 14.dp),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(problem.text, style = displayStyle(30), color = Hud.Text, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (input.isEmpty()) "_" else input,
                    style = displayStyle(38, spacing = 3f),
                    color = Hud.Yellow,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Keypad(
            feedback = feedback,
            onKey = { k -> if (input.length < 5) input += k },
            onClear = { input = "" },
            onDelete = { input = input.dropLast(1) },
            onEnter = ::submit,
        )
    }
}

/** Numeric keypad; every press plays the key blip + a haptic tick. */
@Composable
fun Keypad(
    feedback: CyberFeedback,
    onKey: (String) -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit,
    onEnter: () -> Unit,
) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("CLR", "0", "DEL"))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k ->
                    KeyCap(k, Modifier.weight(1f), color = if (k.length > 1) Hud.Cyan else Hud.Yellow) {
                        feedback.key()
                        when (k) {
                            "DEL" -> onDelete()
                            "CLR" -> onClear()
                            else -> onKey(k)
                        }
                    }
                }
            }
        }
        // ENTER gets its own success/error feedback from the caller, not the key blip.
        KeyCap("ENTER", Modifier.fillMaxWidth(), color = Hud.Yellow, filled = true, onClick = onEnter)
    }
}

@Composable
private fun KeyCap(text: String, modifier: Modifier, color: Color, filled: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction, 0.93f)
    Box(
        modifier
            .height(50.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawWithCache {
                val outer = buttonCutPath(size, 8.dp.toPx())
                val inset = 1.dp.toPx()
                val inner = buttonCutPath(androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2), 8.dp.toPx())
                onDrawBehind {
                    if (filled) {
                        drawPath(outer, color)
                    } else {
                        drawPath(outer, color.copy(alpha = if (color == Hud.Cyan) 0.35f else 0.3f))
                        translate(inset, inset) { drawPath(inner, Hud.KeyFill) }
                    }
                }
            }
            .clickable(interaction, null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = displayStyle(18, spacing = if (filled) 3f else 2f), color = if (filled) Hud.Bg else color)
    }
}
