package com.cyberalarm.challenge

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cyberalarm.alarm.RingingStateStore
import com.cyberalarm.feedback.rememberCyberFeedback
import com.cyberalarm.settings.JackInStore
import com.cyberalarm.settings.TagKind
import com.cyberalarm.ui.components.CodeScanner
import com.cyberalarm.ui.components.CyberButton
import com.cyberalarm.ui.components.CyberCard
import com.cyberalarm.ui.components.NfcReaderEffect
import com.cyberalarm.ui.components.displayStyle
import com.cyberalarm.ui.components.hasCamera
import com.cyberalarm.ui.components.hasNfc
import com.cyberalarm.ui.theme.Hud
import com.cyberalarm.ui.theme.HudType
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay

/** Challenges are created by a plain factory, so they reach singletons through an entry point. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ChallengeDeps {
    fun jackIn(): JackInStore
    fun ringing(): RingingStateStore
}

/**
 * Jack-In: scan a registered QR code / barcode / NFC tag somewhere away from the bed.
 *
 * The neighbour-friendly twist: while ringing, "GO QUIET" pauses the alarm sound and vibration for
 * [QUIET_MS] so you can walk over without waking the building. Once per ring; if the timer runs
 * out before the scan, the alarm comes back. With no tag registered it falls back to math, so a
 * missing sticker can never lock you out.
 */
class JackInChallenge(override val spec: ChallengeSpec) : Challenge {
    @Composable
    override fun Content(onPassed: () -> Unit) = JackInUi(onPassed)

    companion object {
        const val QUIET_MS = 5 * 60_000L
        val FALLBACK = ChallengeSpec(ChallengeType.MATH, Difficulty.MEDIUM, 3)
    }
}

@Composable
private fun JackInUi(onPassed: () -> Unit) {
    val context = LocalContext.current
    val deps = remember { EntryPointAccessors.fromApplication(context.applicationContext, ChallengeDeps::class.java) }
    val tags by deps.jackIn().tags.collectAsState()
    if (tags.isEmpty()) {
        Text("NO JACK-IN POINT REGISTERED // FALLBACK TO MATH", style = HudType.small, color = Hud.Red)
        Text("Register one in CONFIG > JACK-IN POINTS.", style = HudType.small, color = Hud.TextDim, modifier = Modifier.padding(bottom = 12.dp))
        val fallback = remember { MathChallenge(JackInChallenge.FALLBACK) }
        fallback.Content(onPassed)
        return
    }

    val ringing = deps.ringing()
    val feedback = rememberCyberFeedback()
    val mistake = LocalChallengeMistake.current
    val live = remember { ringing.isRinging() } // false in a test run: no alarm to silence
    var cameraOk by remember { mutableStateOf(hasCamera(context)) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { cameraOk = it }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var status by remember { mutableStateOf("LOCATE YOUR JACK-IN POINT AND SCAN IT") }
    var lastWrong by remember { mutableStateOf("") }
    var done by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }

    fun check(kind: TagKind, raw: String) {
        if (done) return
        if (deps.jackIn().matches(kind, raw)) {
            done = true
            feedback.success()
            status = "PREEM // NODE ACCEPTED"
            onPassed()
        } else if (raw != lastWrong) {
            lastWrong = raw
            feedback.error()
            mistake()
            status = "WRONG NODE, CHOOM // THAT'S NOT ONE OF YOURS"
        }
    }
    NfcReaderEffect { check(TagKind.NFC, it) }

    val quietLeft = (ringing.silenceUntil() - now).coerceAtLeast(0)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(status, style = HudType.small, color = if (status.startsWith("WRONG")) Hud.Red else Hud.Cyan)
        if (live) {
            CyberCard(Modifier.fillMaxWidth(), accent = if (quietLeft > 0) Hud.Cyan else Hud.Yellow, cut = 18.dp, contentPadding = PaddingValues(14.dp)) {
                when {
                    quietLeft > 0 -> {
                        val s = (quietLeft + 999) / 1000
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("QUIET MODE // NEIGHBOURS SAFE", style = HudType.micro, color = Hud.Cyan)
                                Text("Alarm resumes if you don't scan in time.", style = HudType.small, color = Hud.TextDim)
                            }
                            Text("%d:%02d".format(s / 60, s % 60), style = displayStyle(28, spacing = 2f), color = Hud.Cyan)
                        }
                    }
                    ringing.silenceUsed() -> Text(
                        "QUIET WINDOW USED // SCAN TO SHUT IT UP",
                        style = HudType.small,
                        color = Hud.Red,
                    )
                    else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Walking over? Mute the alarm for 5 min so the whole building doesn't wake up with you.", style = HudType.small, color = Hud.Text)
                        CyberButton(
                            "GO QUIET // 5 MIN",
                            {
                                ringing.startSilence(System.currentTimeMillis() + JackInChallenge.QUIET_MS)
                                feedback.key()
                            },
                            filled = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        if (cameraOk) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clipToBounds()
                    .border(1.dp, Hud.Cyan.copy(alpha = 0.5f)),
            ) {
                CodeScanner(Modifier.fillMaxSize()) { check(TagKind.CODE, it) }
                Text("[ AIM AT YOUR CODE ]", style = HudType.micro, color = Hud.Yellow, modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp))
            }
        } else {
            CyberButton("ENABLE CAMERA TO SCAN CODES", { cameraLauncher.launch(Manifest.permission.CAMERA) }, color = Hud.Cyan, modifier = Modifier.fillMaxWidth())
        }
        if (hasNfc(context)) {
            Text("OR HOLD AN NFC TAG TO THE BACK OF THE PHONE", style = HudType.micro, color = Hud.TextDim)
        }
        Text("${tags.size} NODE${if (tags.size == 1) "" else "S"} REGISTERED", style = HudType.micro, color = Hud.TextDim)
    }
}
