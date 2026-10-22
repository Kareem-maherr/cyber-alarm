package com.cyberalarm.wear

import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay

private val Yellow = Color(0xFFF0FF00)
private val Cyan = Color(0xFF00E5FF)
private val Red = Color(0xFFFF2A3D)
private val Bg = Color(0xFF04080B)

/** Buzzes and shows what the phone is doing. Deliberately has no way to dismiss the alarm. */
class AlertActivity : ComponentActivity() {

    private val vibrator by lazy { getSystemService(VibratorManager::class.java).defaultVibrator }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme {
                val alert by AlertState.current.collectAsState()
                var seen by remember { mutableStateOf(false) }
                LaunchedEffect(alert) {
                    if (alert == null) {
                        vibrator.cancel()
                        // Opened from the launcher with nothing going on: just show standby.
                        if (seen) finish()
                    } else {
                        seen = true
                        buzz(vibrator)
                        delay(10 * 60_000L) // never buzz forever if the phone went quiet
                        vibrator.cancel()
                    }
                }
                when (val a = alert) {
                    is Alert.Ringing -> RingingFace(a)
                    is Alert.AwakeCheck -> AwakeFace(a, onOpenPhone = ::openOnPhone)
                    null -> Face {
                        Text("CYBER-ALARM", color = Yellow, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("STANDING BY // LINKED TO PHONE", color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 10.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        vibrator.cancel()
        super.onDestroy()
    }

    private fun openOnPhone() {
        val nodes = Wearable.getNodeClient(this).connectedNodes
        nodes.addOnSuccessListener { list ->
            list.forEach { Wearable.getMessageClient(this).sendMessage(it.id, WearPaths.OPEN_AWAKE, ByteArray(0)) }
        }
        vibrator.cancel()
    }
}

private fun buzz(vibrator: Vibrator) {
    vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 400, 500, 1_600), 0))
}

@Composable
private fun RingingFace(a: Alert.Ringing) {
    Face {
        Text("WAKE SIGNAL", color = Red, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        Text(a.label, color = Yellow, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text("SOLVE IT ON YOUR PHONE, CHOOM", color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun AwakeFace(a: Alert.AwakeCheck, onOpenPhone: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(a) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    val s = ((a.deadline - now).coerceAtLeast(0) + 999) / 1000
    Face {
        Text("AWAKE CHECK ${a.stage}/2", color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        Text("STILL WITH US?", color = Yellow, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text("%d:%02d".format(s / 60, s % 60), color = if (s < 20) Red else Yellow, fontSize = 26.sp, fontFamily = FontFamily.Monospace)
        Button(
            onClick = onOpenPhone,
            colors = ButtonDefaults.buttonColors(backgroundColor = Yellow, contentColor = Bg),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Text("OPEN ON PHONE", fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun Face(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Bg).padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) { content() }
}
