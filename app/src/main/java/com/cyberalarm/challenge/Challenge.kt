package com.cyberalarm.challenge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cyberalarm.ui.components.HoldToConfirm
import com.cyberalarm.ui.theme.Hud
import com.cyberalarm.ui.theme.HudType

/**
 * A dismissal challenge. Implementations own their state and UI and must call `onPassed` exactly
 * once when the user has completed every repetition in [spec]. To add a challenge: add a
 * [ChallengeType], implement this interface, register it in [ChallengeFactory].
 */
interface Challenge {
    val spec: ChallengeSpec

    @Composable
    fun Content(onPassed: () -> Unit)
}

object ChallengeFactory {
    fun create(spec: ChallengeSpec): Challenge = when (spec.type) {
        ChallengeType.MATH -> MathChallenge(spec)
        ChallengeType.MIX_MATCH -> MixMatchChallenge(spec)
        ChallengeType.CONNECT4 -> Connect4Challenge(spec)
        ChallengeType.MEMORY -> MemoryChallenge(spec)
        ChallengeType.STEPS -> StepsChallenge(spec)
    }
}

/**
 * Runs a chain of challenges in order and calls [onAllPassed] once at the end.
 * With an empty chain it falls back to hold-to-dismiss.
 */
@Composable
fun ChallengeRunner(specs: List<ChallengeSpec>, onAllPassed: () -> Unit, modifier: Modifier = Modifier) {
    var finished by remember { mutableIntStateOf(0) }
    val complete = {
        if (finished == 0) {
            finished = 1
            onAllPassed()
        }
    }
    if (specs.isEmpty()) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("NO CHALLENGE CONFIGURED", style = HudType.small, color = Hud.TextDim)
            HoldToConfirm("HOLD TO DISMISS", onConfirmed = complete)
        }
        return
    }
    var index by rememberSaveable(specs) { mutableIntStateOf(0) }
    val spec = specs[index.coerceAtMost(specs.lastIndex)]
    Column(modifier) {
        ChainProgress(index, specs.size)
        Spacer(Modifier.height(10.dp))
        Text(
            "CHALLENGE ${index + 1}/${specs.size} // ${spec.type.title}",
            style = HudType.label.copy(fontSize = HudType.body.fontSize),
            color = Hud.Yellow,
        )
        Text("[${spec.difficulty.short}] x${spec.count} // ${spec.type.blurb}", style = HudType.small, color = Hud.TextDim)
        Spacer(Modifier.height(14.dp))
        key(index) {
            val challenge = remember { ChallengeFactory.create(spec) }
            challenge.Content(onPassed = { if (index >= specs.lastIndex) complete() else index++ })
        }
    }
}

@Composable
private fun ChainProgress(done: Int, total: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { i ->
            val color = when {
                i < done -> Hud.Yellow
                i == done -> Hud.Cyan
                else -> Hud.GreyDim
            }
            Box(Modifier.weight(1f).height(4.dp).background(color))
        }
    }
}

/** Shared segmented progress bar used inside challenges (streaks, rounds, wins). */
@Composable
fun SegmentBar(done: Int, total: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(total.coerceAtMost(40)) { i ->
            Box(Modifier.weight(1f).height(8.dp).background(if (i < done) Hud.Yellow else Hud.GreyDim))
        }
    }
}
