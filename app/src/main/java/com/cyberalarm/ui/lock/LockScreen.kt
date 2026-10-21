package com.cyberalarm.ui.lock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberalarm.challenge.ChallengeRunner
import com.cyberalarm.lock.LockManager
import com.cyberalarm.ui.components.CyberButton
import com.cyberalarm.ui.components.GlitchClock
import com.cyberalarm.ui.components.ScreenEdgeAccents
import com.cyberalarm.ui.components.cornerBrackets
import com.cyberalarm.ui.components.hudBackground
import com.cyberalarm.ui.components.rememberNow
import com.cyberalarm.ui.theme.Hud
import com.cyberalarm.ui.theme.HudType

/** Block screen for a locked app: countdown to release, or solve a challenge for a short pass. */
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
    val remainingMs = ((windows[packageName]?.lockedUntil ?: 0L) - now.toInstant().toEpochMilli()).coerceAtLeast(0)
    val s = remainingMs / 1000
    val insets = WindowInsets.safeDrawing.asPaddingValues()

    Box(Modifier.fillMaxSize().hudBackground()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = insets.calculateTopPadding() + 24.dp, bottom = insets.calculateBottomPadding() + 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("// STAY_AWAKE PROTOCOL ENGAGED //", style = HudType.small, color = Hud.Cyan)
            Spacer(Modifier.height(16.dp))
            Column(
                Modifier.fillMaxWidth().cornerBrackets(Hud.Red, 14.dp).padding(vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GlitchClock("ACCESS DENIED", style = HudType.clock.copy(fontSize = 38.sp, letterSpacing = 2.sp), color = Hud.Red)
                Spacer(Modifier.height(8.dp))
                Text(appLabel.uppercase(), style = HudType.title, color = Hud.Yellow, textAlign = TextAlign.Center)
                Text(packageName, style = HudType.small, color = Hud.TextDim)
            }
            Spacer(Modifier.height(18.dp))
            Text("LOCK RELEASES IN", style = HudType.small, color = Hud.TextDim)
            Text(
                "%02d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60),
                style = HudType.cardTime,
                color = Hud.Yellow,
            )
            Spacer(Modifier.height(24.dp))
            if (solving) {
                ChallengeRunner(LockManager.UNLOCK_CHALLENGE, onAllPassed = onUnlocked, modifier = Modifier.fillMaxWidth())
            } else {
                Text(
                    "Prove you're awake to get a ${LockManager.PASS_MINUTES}-minute pass for this app.",
                    style = HudType.body,
                    color = Hud.Text,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                CyberButton("SOLVE TO UNLOCK", { solving = true }, filled = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                CyberButton("EXIT TO HOME", onExit, color = Hud.Red, modifier = Modifier.fillMaxWidth())
            }
        }
        ScreenEdgeAccents(Modifier.padding(bottom = insets.calculateBottomPadding()))
    }
}
