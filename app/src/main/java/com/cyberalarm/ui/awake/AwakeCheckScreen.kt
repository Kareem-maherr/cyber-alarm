package com.cyberalarm.ui.awake

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberalarm.alarm.AwakeCheckManager
import com.cyberalarm.challenge.ChallengeRunner
import com.cyberalarm.feedback.Haptics
import com.cyberalarm.feedback.rememberCyberFeedback
import com.cyberalarm.ui.components.BreathingDot
import com.cyberalarm.ui.components.FlankedCaption
import com.cyberalarm.ui.components.NightCityScreen
import com.cyberalarm.ui.components.displayStyle
import com.cyberalarm.ui.theme.Hud
import com.cyberalarm.ui.theme.HudType
import kotlinx.coroutines.delay

/**
 * "Still with us, choom?" A draining timer and a short challenge. It buzzes every few seconds until
 * the screen is touched, so a phone lying next to a sleeper actually gets noticed.
 */
@Composable
fun AwakeCheckScreen(stage: Int, stages: Int, deadline: Long, onPassed: () -> Unit, onExpired: () -> Unit) {
    val context = LocalContext.current
    val feedback = rememberCyberFeedback()
    val expired by rememberUpdatedState(onExpired)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var touched by remember { mutableStateOf(false) }
    var flatlined by remember { mutableStateOf(false) }
    val insets = WindowInsets.safeDrawing.asPaddingValues()

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            if (now >= deadline) {
                flatlined = true
                delay(1_400)
                expired()
                return@LaunchedEffect
            }
            delay(250)
        }
    }
    LaunchedEffect(touched) {
        while (!touched) {
            Haptics.get(context).nudge()
            feedback.error()
            delay(5_000)
        }
    }

    val remainingMs = (deadline - now).coerceAtLeast(0)
    val fraction by animateFloatAsState(
        (remainingMs.toFloat() / AwakeCheckManager.WINDOW_MS).coerceIn(0f, 1f),
        tween(250, easing = LinearEasing),
        label = "awakeTimer",
    )
    val urgent = remainingMs < 20_000
    val timerColor by animateColorAsState(if (urgent) Hud.Red else Hud.Yellow, label = "awakeColor")

    NightCityScreen(
        Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial)
                    touched = true
                }
            }
        },
        alert = urgent || flatlined,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = insets.calculateTopPadding() + 12.dp, bottom = insets.calculateBottomPadding() + 40.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BreathingDot(if (urgent) Hud.Red else Hud.Cyan)
                Text("AWAKE CHECK $stage/$stages", style = HudType.micro, color = Hud.Cyan, modifier = Modifier.weight(1f))
                Text("FAIL = ALARM RE-ENGAGES", style = HudType.micro, color = Hud.TextDim)
            }
            Column(
                Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    if (flatlined) "FLATLINED." else "STILL WITH US, CHOOM?",
                    style = displayStyle(28, FontWeight.Bold, 2f).copy(shadow = Shadow(Hud.Yellow.copy(alpha = 0.25f), blurRadius = 36f)),
                    color = if (flatlined) Hud.Red else Hud.Yellow,
                    textAlign = TextAlign.Center,
                )
                Text(
                    if (flatlined) "No response. Alarm re-engaging." else "Prove you didn't crawl back into bed.",
                    style = HudType.body,
                    color = Hud.Text,
                    textAlign = TextAlign.Center,
                )
                val s = (remainingMs + 999) / 1000
                Text(
                    "%02d:%02d".format(s / 60, s % 60),
                    style = displayStyle(46, spacing = 3f).copy(shadow = Shadow(timerColor.copy(alpha = 0.25f), blurRadius = 40f)),
                    color = timerColor,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .drawBehind {
                            drawRect(Hud.Grey.copy(alpha = 0.15f))
                            drawRect(timerColor, size = size.copy(width = size.width * fraction))
                        },
                )
            }
            if (!flatlined) {
                ChallengeRunner(AwakeCheckManager.CHALLENGE, onAllPassed = onPassed)
            } else {
                FlankedCaption("SEE YOU IN A SEC, SAMURAI", HudType.micro.copy(fontSize = 12.sp), Hud.Red, Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}
