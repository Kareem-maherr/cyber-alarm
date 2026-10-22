package com.cyberalarm.ui.ringing

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cyberalarm.cred.Rank
import com.cyberalarm.cred.WakeReport
import com.cyberalarm.ui.components.CyberButton
import com.cyberalarm.ui.components.CyberCard
import com.cyberalarm.ui.components.FlankedCaption
import com.cyberalarm.ui.components.displayStyle
import com.cyberalarm.ui.theme.Hud
import com.cyberalarm.ui.theme.HudMotion
import com.cyberalarm.ui.theme.HudType
import kotlinx.coroutines.delay

/** Post-dismissal readout: how the wake went, what it paid, streak and rank. */
@Composable
fun WakeReportPanel(report: WakeReport, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(Unit) {
        delay(30_000)
        done()
    }
    val s = report.session
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        FlankedCaption("WAKE REPORT // SIGNAL CLEARED", HudType.micro, Hud.Cyan, Modifier.align(Alignment.CenterHorizontally))
        if (report.rankUp != null) {
            CyberCard(Modifier.fillMaxWidth(), accent = Hud.Yellow, cut = 18.dp, contentPadding = PaddingValues(14.dp)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("RANK UP", style = HudType.micro, color = Hud.Cyan)
                    Text(
                        report.rankUp.title,
                        style = displayStyle(30, FontWeight.Bold, 3f).copy(shadow = Shadow(Hud.Yellow.copy(alpha = 0.3f), blurRadius = 40f)),
                        color = Hud.Yellow,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("TIME TO WAKE", formatDuration(s.secondsToWake), Modifier.weight(1f))
            Stat("MISTAKES", "${s.mistakes}", Modifier.weight(1f), if (s.mistakes > 0) Hud.Red else Hud.Yellow)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("SNOOZES", "${s.snoozes}", Modifier.weight(1f), if (s.snoozes > 0) Hud.Red else Hud.Yellow)
            Stat("STREAK", if (report.streak > 0) "${report.streak} CLEAN" else "BROKEN", Modifier.weight(1f), if (report.streak > 0) Hud.Yellow else Hud.Red)
        }
        CyberCard(Modifier.fillMaxWidth(), cut = 18.dp, contentPadding = PaddingValues(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("PAYOUT", style = HudType.micro, color = Hud.TextDim)
                report.lines.forEachIndexed { i, (amount, reason) -> PayoutLine(i, amount, reason) }
                Box(Modifier.fillMaxWidth().height(1.dp).drawBehind { drawRect(Hud.Grey.copy(alpha = 0.2f)) })
                Row {
                    Text("NET", style = HudType.value, color = Hud.Text, modifier = Modifier.weight(1f))
                    Text(eddies(report.earned, signed = true), style = HudType.value, color = if (report.earned >= 0) Hud.Yellow else Hud.Red)
                }
                Row {
                    Text("BALANCE // ${report.rank.title}", style = HudType.small, color = Hud.TextDim, modifier = Modifier.weight(1f))
                    Text(eddies(report.total), style = HudType.small, color = Hud.Cyan)
                }
                RankBar(report.total)
            }
        }
        Text(
            "Awake checks at +5 and +10 min pay ${"+"}25 €$ each. Miss one and it costs you.",
            style = HudType.small,
            color = Hud.TextDim,
        )
        CyberButton("DONE // GO GET 'EM, CHOOM", onDone, filled = true, height = 50.dp, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier, color: androidx.compose.ui.graphics.Color = Hud.Yellow) {
    CyberCard(modifier, cut = 14.dp, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = HudType.micro, color = Hud.TextDim)
            Text(value, style = displayStyle(20, spacing = 1f), color = color)
        }
    }
}

/** Ledger lines rise in one after another. */
@Composable
private fun PayoutLine(index: Int, amount: Int, reason: String) {
    val rise = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(200L + index * 180L)
        rise.animateTo(1f, tween(450, easing = HudMotion.Ease))
    }
    Row(Modifier.graphicsLayer { alpha = rise.value; translationY = (1f - rise.value) * 8.dp.toPx() }) {
        Text(reason, style = HudType.small, color = Hud.Text, modifier = Modifier.weight(1f))
        Text(eddies(amount, signed = true), style = HudType.small, color = if (amount >= 0) Hud.Yellow else Hud.Red)
    }
}

/** Progress towards the next rank. */
@Composable
fun RankBar(total: Int, modifier: Modifier = Modifier) {
    val rank = Rank.of(total)
    val next = rank.next
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier.fillMaxWidth().height(3.dp).drawBehind {
                drawRect(Hud.Grey.copy(alpha = 0.15f))
                drawRect(Hud.Yellow, size = size.copy(width = size.width * Rank.progress(total)))
            },
        )
        Text(
            if (next == null) "MAX RANK // NIGHT CITY LEGEND" else "${eddies(next.minEddies - total)} TO ${next.title}",
            style = HudType.micro,
            color = Hud.TextDim,
            modifier = Modifier.align(Alignment.End),
        )
    }
}

/** Eurodollars, the game's currency: `1,240 €$`. */
fun eddies(amount: Int, signed: Boolean = false): String {
    val sign = if (signed && amount > 0) "+" else ""
    return "$sign${"%,d".format(amount)} €$"
}

fun formatDuration(seconds: Long): String = when {
    seconds < 60 -> "${seconds}S"
    seconds < 3600 -> "${seconds / 60}M ${"%02d".format(seconds % 60)}S"
    else -> "${seconds / 3600}H ${"%02d".format((seconds / 60) % 60)}M"
}
