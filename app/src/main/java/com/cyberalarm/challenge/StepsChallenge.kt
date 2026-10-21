package com.cyberalarm.challenge

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.cyberalarm.ui.components.CyberCard
import com.cyberalarm.ui.theme.Hud
import com.cyberalarm.ui.theme.HudType
import kotlin.math.sqrt

/**
 * Walk N steps. Uses the hardware step detector when available and ACTIVITY_RECOGNITION is
 * granted; otherwise counts acceleration peaks (works for walking or vigorous shaking).
 */
class StepsChallenge(override val spec: ChallengeSpec) : Challenge {
    @Composable
    override fun Content(onPassed: () -> Unit) = StepsUi(spec, onPassed)

    companion object {
        fun target(spec: ChallengeSpec): Int = spec.count * when (spec.difficulty) {
            Difficulty.EASY -> 20
            Difficulty.MEDIUM -> 40
            Difficulty.HARD -> 80
        }
    }
}

@Composable
private fun StepsUi(spec: ChallengeSpec, onPassed: () -> Unit) {
    val context = LocalContext.current
    val target = remember { StepsChallenge.target(spec) }
    var steps by remember { mutableIntStateOf(0) }
    var source by remember { mutableStateOf("NO SENSOR") }
    var passed by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val sm = context.getSystemService(SensorManager::class.java)
        val detector = sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)?.takeIf { hasActivityPermission(context) }
        val listener: SensorEventListener
        if (detector != null) {
            source = "STEP_DETECTOR"
            listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) { steps++ }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            sm.registerListener(listener, detector, SensorManager.SENSOR_DELAY_UI)
        } else {
            val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            source = if (accel != null) "ACCEL // WALK OR SHAKE" else "NO SENSOR"
            listener = object : SensorEventListener {
                private var last = 0L
                private var armed = true
                override fun onSensorChanged(event: SensorEvent) {
                    val (x, y, z) = event.values
                    val magnitude = sqrt(x * x + y * y + z * z)
                    val now = SystemClock.elapsedRealtime()
                    // Peak detection with hysteresis + debounce.
                    if (armed && magnitude > PEAK && now - last > 300) {
                        steps++
                        last = now
                        armed = false
                    } else if (magnitude < REARM) {
                        armed = true
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            accel?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        }
        onDispose { sm.unregisterListener(listener) }
    }

    LaunchedEffect(steps) {
        if (!passed && steps >= target) {
            passed = true
            onPassed()
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Text("SENSOR: $source", style = HudType.small, color = Hud.Cyan)
        Spacer(Modifier.height(12.dp))
        CyberCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("%03d".format(steps.coerceAtMost(target)), style = HudType.clock.copy(fontSize = HudType.cardTime.fontSize * 1.4f), color = Hud.Yellow)
                Text("/ %03d STEPS".format(target), style = HudType.label, color = Hud.TextDim)
            }
        }
        Spacer(Modifier.height(14.dp))
        SegmentBar((steps * 20 / target).coerceAtMost(20), 20)
        Spacer(Modifier.height(14.dp))
        Text(
            "GET OUT OF BED. KEEP THE PHONE IN HAND AND WALK.",
            style = HudType.body,
            color = Hud.Text,
        )
    }
}

private const val PEAK = 13.0f
private const val REARM = 10.5f

private fun hasActivityPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
