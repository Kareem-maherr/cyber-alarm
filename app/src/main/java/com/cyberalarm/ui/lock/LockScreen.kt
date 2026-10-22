package com.cyberalarm.ui.lock

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cyberalarm.challenge.ChallengeRunner
import com.cyberalarm.lingo.Lingo
import com.cyberalarm.lock.LockManager
import com.cyberalarm.ui.components.CyberButton
import com.cyberalarm.ui.components.CyberCard
import com.cyberalarm.ui.components.FlankedCaption
import com.cyberalarm.ui.components.NightCityScreen
import com.cyberalarm.ui.components.displayStyle
import com.cyberalarm.ui.components.rememberNow
import com.cyberalarm.ui.theme.Hud
import com.cyberalarm.ui.theme.HudMotion
import com.cyberalarm.ui.theme.HudType

/** Block screen for a locked app: live countdown + progress, or solve a challenge for a short pass. */
@Composable
fun LockScreen(
    packageName: String,
    appLabel: String,
    lockManager: LockManager,
    onExit: () -> Unit,
    onUnlocked: () -> Unit,
) {
    val windows by lockManager.windows.collectAsState()
    val now by rememberNow()
    var solving by remember { mutableStateOf(false) }
    val taunt = remember { Lingo.pick(Lingo.lockedOut) }
    val window = windows[packageName]
    val nowMs = now.toInstant().toEpochMilli()
    val remainingMs = ((window?.lockedUntil ?: 0L) - nowMs).coerceAtLeast(0)
    val s = remainingMs / 1000
    val total = window?.let { it.lockedUntil - it.lockedAt }?.takeIf { window.lockedAt > 0 && it > 0 }
    // width transition: 1s linear, like the design's progress bar
    val elapsed by animateFloatAsState(
        if (total != null) (1f - remainingMs.toFloat() / total).coerceIn(0f, 1f) else 0f,
        tween(1_000, easing = LinearEasing),
        label = "lockProgress",
    )
    val insets = WindowInsets.safeDrawing.asPaddingValues()

    NightCityScreen(alert = true) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = insets.calculateTopPadding() + 24.dp, bottom = insets.calculateBottomPadding() + 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FlankedCaption("STAY_AWAKE PROTOCOL ENGAGED", HudType.micro.copy(letterSpacing = HudType.micro.letterSpacing * 1.5f), Hud.Cyan)
            CyberCard(
                Modifier.fillMaxWidth().padding(top = 22.dp),
                accent = Hud.Red,
                cut = 26.dp,
                contentPadding = PaddingValues(top = 26.dp, bottom = 22.dp),
            ) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "ACCESS DENIED",
                        style = displayStyle(38, FontWeight.Bold, 3f).copy(shadow = Shadow(Hud.Red.copy(alpha = 0.3f), blurRadius = 44f)),
                        color = Hud.Red,
                    )
                    Text(appLabel.uppercase(), style = displayStyle(20, spacing = 3f), color = Hud.Yellow, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                    Text(packageName, style = HudType.small, color = Hud.TextDim)
                    Text(taunt, style = HudType.body, color = Hud.Text, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp))
                }
            }
            Text("LOCK RELEASES IN", style = HudType.micro, color = Hud.TextDim, modifier = Modifier.padding(top = 24.dp))
            Text(
                "%02d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60),
                style = displayStyle(50, spacing = 3f).copy(shadow = Shadow(Hud.Yellow.copy(alpha = 0.22f), blurRadius = 44f)),
                color = Hud.Yellow,
            )
            if (total != null) {
                Box(
                    Modifier
                        .padding(top = 10.dp)
                        .fillMaxWidth()
                        .height(2.dp)
                        .drawBehind {
                            drawRect(Hud.Grey.copy(alpha = 0.15f))
                            drawRect(Hud.Yellow, size = size.copy(width = size.width * elapsed))
                        },
                )
            }
            AnimatedContent(
                targetState = solving,
                transitionSpec = { fadeIn(HudMotion.fade(400)) togetherWith fadeOut(HudMotion.fade(200)) },
                label = "lockMode",
            ) { isSolving ->
                if (isSolving) {
                    ChallengeRunner(LockManager.UNLOCK_CHALLENGE, onAllPassed = onUnlocked, modifier = Modifier.fillMaxWidth().padding(top = 24.dp))
                } else {
                    Column(Modifier.fillMaxWidth().padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Prove you're awake, choom, and get a ${LockManager.PASS_MINUTES}-minute pass for this app.",
                            style = HudType.body,
                            color = Hud.Text,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        )
                        CyberButton("SOLVE TO UNLOCK", { solving = true }, filled = true, height = 50.dp, modifier = Modifier.fillMaxWidth())
                        CyberButton("EXIT TO HOME", onExit, color = Hud.Red, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}
