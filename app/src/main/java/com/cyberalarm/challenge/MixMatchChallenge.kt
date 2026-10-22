package com.cyberalarm.challenge

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import com.cyberalarm.ui.components.hapticClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.cyberalarm.ui.components.chamferPath
import com.cyberalarm.ui.components.neonStroke
import com.cyberalarm.ui.theme.Hud
import com.cyberalarm.ui.theme.HudType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/** One side of a pair. Items with the same [id] belong together. */
data class MatchItem(val id: Int, val text: String = "", val swatch: Color? = null, val ink: Color? = null)

data class MatchRound(val theme: String, val left: List<MatchItem>, val right: List<MatchItem>)

object MixMatchGenerator {
    private val colors = listOf(
        "RED" to Color(0xFFFF2A3D), "CYAN" to Color(0xFF00E5FF), "YELLOW" to Color(0xFFF0FF00),
        "GREEN" to Color(0xFF39FF6A), "MAGENTA" to Color(0xFFFF2BD6), "ORANGE" to Color(0xFFFF8A00),
        "BLUE" to Color(0xFF2F6BFF), "WHITE" to Color(0xFFF2F2F2),
    )
    private val symbols = listOf(
        "△" to "TRIANGLE", "○" to "CIRCLE", "□" to "SQUARE", "◇" to "DIAMOND",
        "☆" to "STAR", "✕" to "CROSS", "♥" to "HEART", "↑" to "ARROW",
    )
    private val numbers = listOf(
        "ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX", "SEVEN", "EIGHT", "NINE", "TEN", "ELEVEN", "TWELVE",
    )

    fun pairsFor(difficulty: Difficulty) = when (difficulty) {
        Difficulty.EASY -> 4
        Difficulty.MEDIUM -> 5
        Difficulty.HARD, Difficulty.LEGEND -> 6
    }

    fun round(difficulty: Difficulty, random: Random = Random.Default): MatchRound {
        val n = pairsFor(difficulty)
        val (theme, left, right) = when (random.nextInt(3)) {
            0 -> {
                val picked = colors.shuffled(random).take(n)
                // HARD: Stroop effect, the word is printed in a *different* color.
                val left = picked.mapIndexed { i, (name, _) ->
                    val ink = if (difficulty >= Difficulty.HARD) picked[(i + 1) % n].second else Hud.Text
                    MatchItem(i, text = name, ink = ink)
                }
                Triple("COLOR // WORD", left, picked.mapIndexed { i, (_, c) -> MatchItem(i, swatch = c) })
            }
            1 -> {
                val picked = symbols.shuffled(random).take(n)
                Triple(
                    "SYMBOL // NAME",
                    picked.mapIndexed { i, (glyph, _) -> MatchItem(i, text = glyph) },
                    picked.mapIndexed { i, (_, name) -> MatchItem(i, text = name) },
                )
            }
            else -> {
                val picked = numbers.indices.shuffled(random).take(n)
                Triple(
                    "DIGIT // WORD",
                    picked.mapIndexed { i, v -> MatchItem(i, text = (v + 1).toString()) },
                    picked.mapIndexed { i, v -> MatchItem(i, text = numbers[v]) },
                )
            }
        }
        return MatchRound(theme, left.shuffled(random), right.shuffled(random))
    }

    fun allowedMistakes(difficulty: Difficulty) = when (difficulty) {
        Difficulty.EASY -> Int.MAX_VALUE
        Difficulty.MEDIUM -> 3
        Difficulty.HARD, Difficulty.LEGEND -> 1
    }
}

class MixMatchChallenge(override val spec: ChallengeSpec) : Challenge {
    @Composable
    override fun Content(onPassed: () -> Unit) = MixMatchUi(spec, onPassed)
}

@Composable
private fun MixMatchUi(spec: ChallengeSpec, onPassed: () -> Unit) {
    var round by remember { mutableStateOf(MixMatchGenerator.round(spec.difficulty)) }
    var roundsDone by remember { mutableIntStateOf(0) }
    var matched by remember { mutableStateOf(emptySet<Int>()) }
    var selLeft by remember { mutableStateOf<Int?>(null) }
    var selRight by remember { mutableStateOf<Int?>(null) }
    var wrong by remember { mutableStateOf(false) }
    var mistakes by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val onMistake = LocalChallengeMistake.current
    val allowed = MixMatchGenerator.allowedMistakes(spec.difficulty)

    fun newRound() {
        round = MixMatchGenerator.round(spec.difficulty)
        matched = emptySet(); mistakes = 0; selLeft = null; selRight = null
    }

    fun evaluate() {
        val l = selLeft ?: return
        val r = selRight ?: return
        if (l == r) {
            matched = matched + l
            selLeft = null; selRight = null
            if (matched.size == round.left.size) {
                roundsDone++
                if (roundsDone >= spec.count) onPassed() else {
                    message = "NOVA // ROUND CLEAR"
                    newRound()
                }
            }
        } else {
            wrong = true
            mistakes++
            onMistake()
            scope.launch {
                delay(380)
                wrong = false
                selLeft = null; selRight = null
                if (mistakes > allowed) {
                    message = "TOO MANY ERRORS // PATTERN RESET"
                    newRound()
                }
            }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ROUND ${roundsDone + 1}/${spec.count} // ${round.theme}", style = HudType.small, color = Hud.Cyan, modifier = Modifier.weight(1f))
            if (allowed != Int.MAX_VALUE) Text("ERR $mistakes/$allowed", style = HudType.small, color = if (mistakes > 0) Hud.Red else Hud.TextDim)
        }
        Spacer(Modifier.height(6.dp))
        SegmentBar(matched.size, round.left.size)
        if (message.isNotEmpty()) Text(message, style = HudType.small, color = Hud.Yellow, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                round.left.forEach { item ->
                    MatchTile(item, selected = selLeft == item.id, matched = item.id in matched, wrong = wrong && selLeft == item.id) {
                        if (item.id in matched || wrong) return@MatchTile
                        selLeft = item.id; message = ""; evaluate()
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                round.right.forEach { item ->
                    MatchTile(item, selected = selRight == item.id, matched = item.id in matched, wrong = wrong && selRight == item.id) {
                        if (item.id in matched || wrong) return@MatchTile
                        selRight = item.id; message = ""; evaluate()
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchTile(item: MatchItem, selected: Boolean, matched: Boolean, wrong: Boolean, onClick: () -> Unit) {
    val target = when {
        wrong -> Hud.Red
        matched -> Hud.GreyDim
        selected -> Hud.Cyan
        else -> Hud.Yellow
    }
    val color by animateColorAsState(target, label = "tile")
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .graphicsLayer { alpha = if (matched) 0.45f else 1f }
            .drawWithCache {
                val path = chamferPath(size, 8.dp.toPx(), 0f, 8.dp.toPx(), 0f)
                onDrawBehind {
                    drawPath(path, color.copy(alpha = if (selected || wrong) 0.18f else 0.04f))
                    neonStroke(path, color, 1.3.dp.toPx(), glow = selected || wrong)
                }
            }
            .hapticClickable(remember { MutableInteractionSource() }, null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            item.swatch != null -> Box(Modifier.size(width = 64.dp, height = 22.dp).background(item.swatch))
            else -> Text(
                if (matched) "✓ ${item.text}" else item.text,
                style = HudType.label.copy(fontSize = HudType.body.fontSize),
                color = item.ink ?: Hud.Text,
            )
        }
    }
}
