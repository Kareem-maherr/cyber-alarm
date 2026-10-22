package com.cyberalarm.ui.ringing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.CompositionLocalProvider
import com.cyberalarm.challenge.ChallengeRunner
import com.cyberalarm.challenge.LocalChallengeMistake
import com.cyberalarm.lingo.Lingo
import com.cyberalarm.ui.components.BreathingDot
import com.cyberalarm.ui.components.CyberButton
import com.cyberalarm.ui.components.NightCityScreen
import com.cyberalarm.ui.components.SweepText
import com.cyberalarm.ui.components.hudDate
import com.cyberalarm.ui.components.rememberNow
import com.cyberalarm.ui.theme.Hud
import com.cyberalarm.ui.theme.HudType

/** Alarm-ringing screen (red-ambient variant): clock, label, the challenge chain and snooze. */
@Composable
fun RingingScreen(onFinished: () -> Unit, vm: RingingViewModel = hiltViewModel()) {
    val alarm by vm.alarm.collectAsStateWithLifecycle()
    val snoozesLeft by vm.snoozesLeft.collectAsStateWithLifecycle()
    val challenges by vm.challenges.collectAsStateWithLifecycle()
    val report by vm.report.collectAsStateWithLifecycle()
    val now by rememberNow()
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val wakeLine = remember { Lingo.pick(Lingo.wakeLines) }

    NightCityScreen(alert = report == null) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = insets.calculateTopPadding() + 12.dp, bottom = insets.calculateBottomPadding() + 40.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BreathingDot(if (report == null) Hud.Red else Hud.Cyan)
                Text(
                    if (report == null) "WAKE SIGNAL ACTIVE" else "WAKE SIGNAL CLEARED",
                    style = HudType.micro,
                    color = if (report == null) Hud.Red else Hud.Cyan,
                    modifier = Modifier.weight(1f),
                )
                Text("BACK // DISABLED", style = HudType.micro, color = Hud.TextDim)
            }
            if (vm.escalation > 0 && report == null) {
                Text(
                    "ESCALATION LV${vm.escalation} // YOU FELL ASLEEP. NO SNOOZE. HARDER CHAIN.",
                    style = HudType.small,
                    color = Hud.Red,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            Column(
                Modifier.fillMaxWidth().padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SweepText("%02d:%02d".format(now.hour, now.minute), style = HudType.clock.copy(fontSize = 76.sp), periodMs = 6_000)
                Text(hudDate(now), style = HudType.date.copy(fontSize = 13.sp), color = Hud.TextDim)
                Text(
                    alarm?.label.orEmpty(),
                    style = HudType.title,
                    color = Hud.Cyan,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(wakeLine, style = HudType.micro.copy(fontSize = 12.sp, letterSpacing = 3.sp), color = Hud.Yellow.copy(alpha = 0.8f))
            }
            val r = report
            if (r != null) {
                WakeReportPanel(r, onDone = onFinished)
                return@Column
            }
            alarm?.let { a ->
                CompositionLocalProvider(LocalChallengeMistake provides vm::onMistake) {
                    ChallengeRunner(challenges, onAllPassed = vm::dismiss)
                }
                if (snoozesLeft > 0) {
                    Spacer(Modifier.height(18.dp))
                    CyberButton(
                        "SNOOZE ${a.snoozeMinutes}M  //  $snoozesLeft LEFT",
                        { vm.snooze(onFinished) },
                        color = Hud.Cyan,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
