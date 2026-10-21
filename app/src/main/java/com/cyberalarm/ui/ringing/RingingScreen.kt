package com.cyberalarm.ui.ringing

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cyberalarm.challenge.ChallengeRunner
import com.cyberalarm.ui.components.CyberButton
import com.cyberalarm.ui.components.GlitchClock
import com.cyberalarm.ui.components.HudDateFormat
import com.cyberalarm.ui.components.ScreenEdgeAccents
import com.cyberalarm.ui.components.cornerBrackets
import com.cyberalarm.ui.components.hudBackground
import com.cyberalarm.ui.components.rememberNow
import com.cyberalarm.ui.theme.Hud
import com.cyberalarm.ui.theme.HudType
import java.util.Locale

/** Alarm-ringing screen: clock, label, the challenge chain and (if allowed) snooze. */
@Composable
fun RingingScreen(onFinished: () -> Unit, vm: RingingViewModel = hiltViewModel()) {
    val alarm by vm.alarm.collectAsStateWithLifecycle()
    val snoozesLeft by vm.snoozesLeft.collectAsStateWithLifecycle()
    val now by rememberNow()
    val blink by rememberInfiniteTransition(label = "alert").animateFloat(
        initialValue = 1f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "alertBlink",
    )
    val insets = WindowInsets.safeDrawing.asPaddingValues()

    Box(Modifier.fillMaxSize().hudBackground()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = insets.calculateTopPadding() + 12.dp, bottom = insets.calculateBottomPadding() + 40.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "!! WAKE SIGNAL ACTIVE !!",
                    style = HudType.small.copy(letterSpacing = 2.sp),
                    color = Hud.Red,
                    modifier = Modifier.weight(1f).graphicsLayer { alpha = blink },
                )
                Text("BACK // DISABLED", style = HudType.small, color = Hud.TextDim)
            }
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier.fillMaxWidth().cornerBrackets(Hud.Red, 14.dp).padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GlitchClock("%02d:%02d".format(now.hour, now.minute), style = HudType.clock.copy(fontSize = 76.sp))
                Text(HudDateFormat.format(now).uppercase(Locale.ENGLISH), style = HudType.date.copy(fontSize = 16.sp), color = Hud.Yellow)
                Spacer(Modifier.height(6.dp))
                Text(
                    alarm?.label ?: "",
                    style = HudType.title,
                    color = Hud.Cyan,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(20.dp))
            alarm?.let { a ->
                ChallengeRunner(a.challenges, onAllPassed = { vm.dismiss(onFinished) })
                if (snoozesLeft > 0) {
                    Spacer(Modifier.height(22.dp))
                    CyberButton(
                        "SNOOZE ${a.snoozeMinutes}M  //  $snoozesLeft LEFT",
                        { vm.snooze(onFinished) },
                        color = Hud.Cyan,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        ScreenEdgeAccents(Modifier.padding(bottom = insets.calculateBottomPadding()))
    }
}
