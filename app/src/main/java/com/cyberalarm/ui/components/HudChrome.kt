package com.cyberalarm.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberalarm.BuildConfig
import com.cyberalarm.ui.theme.Hud
import com.cyberalarm.ui.theme.HudMotion
import com.cyberalarm.ui.theme.HudType
import kotlinx.coroutines.delay
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Wall clock that ticks on the second boundary. */
@Composable
fun rememberNow(): State<ZonedDateTime> = produceState(ZonedDateTime.now()) {
    while (true) {
        val now = ZonedDateTime.now()
        value = now
        delay(1_000L - now.nano / 1_000_000)
    }
}

val HudDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.ENGLISH)

fun hudDate(now: ZonedDateTime): String = HudDateFormat.format(now).uppercase(Locale.ENGLISH)

/** `■ SYS.ONLINE` ............ `ALARM CLOCK v2.4.1 · NEON UI` */
@Composable
fun StatusStrip(modifier: Modifier = Modifier, onSystemClick: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(6.dp).drawBehind {
                // box-shadow: 0 0 8px yellow/.6
                val r = 8.dp.toPx()
                drawCircle(Brush.radialGradient(listOf(Hud.Yellow.copy(alpha = 0.45f), Color.Transparent), center, r), r)
                drawRoundRect(Hud.Yellow, cornerRadius = CornerRadius(1.dp.toPx()))
            },
        )
        Spacer(Modifier.width(8.dp))
        Text("SYS.ONLINE", style = HudType.micro, color = Hud.Yellow)
        Spacer(Modifier.weight(1f))
        Text(
            "ALARM CLOCK v${BuildConfig.VERSION_NAME} · NEON UI",
            style = HudType.micro,
            color = Hud.TextDim,
            modifier = if (onSystemClick != null) Modifier.hapticClickable(onClick = onSystemClick) else Modifier,
        )
    }
}

data class LogLine(val text: String, val color: Color = Hud.TextDim, val onClick: (() -> Unit)? = null)

/** Quiet terminal readout; each line rises in (fade + 6dp lift), staggered 250ms. */
@Composable
fun TerminalLog(lines: List<LogLine>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEachIndexed { i, line ->
            val rise = remember(line.text) { Animatable(0f) }
            LaunchedEffect(line.text) {
                delay(300L + i * 250L)
                rise.animateTo(1f, HudMotion.glide(600))
            }
            Text(
                "> ${line.text}",
                style = HudType.micro.copy(letterSpacing = 1.5.sp),
                color = line.color,
                modifier = Modifier
                    .graphicsLayer {
                        alpha = rise.value
                        translationY = (1f - rise.value) * 6.dp.toPx()
                    }
                    .then(if (line.onClick != null) Modifier.hapticClickable(onClick = line.onClick) else Modifier),
            )
        }
    }
}

/** Flat yellow hexagon "+" with a soft radial halo; presses down to 0.94. */
@Composable
fun HexFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction, 0.94f)
    Box(
        modifier
            .size(64.dp, 56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .hapticClickable(interaction, null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.requiredSize(92.dp, 84.dp)) {
            // halo: radial-gradient(closest-side, yellow/.22, transparent) around the hexagon
            drawCircle(
                Brush.radialGradient(listOf(Hud.Yellow.copy(alpha = 0.22f), Color.Transparent), center, size.minDimension / 2),
                radius = size.minDimension / 2,
            )
            val hexSize = Size(64.dp.toPx(), 56.dp.toPx())
            val origin = Offset((size.width - hexSize.width) / 2, (size.height - hexSize.height) / 2)
            translate(origin.x, origin.y) {
                drawPath(hexagonPath(hexSize), Hud.Yellow)
                val c = Offset(hexSize.width / 2, hexSize.height / 2)
                val arm = 10.dp.toPx()
                val sw = 2.5.dp.toPx()
                drawLine(Hud.Bg, c - Offset(arm, 0f), c + Offset(arm, 0f), sw)
                drawLine(Hud.Bg, c - Offset(0f, arm), c + Offset(0f, arm), sw)
            }
        }
    }
}

/** Top bar for secondary screens: `<<  TITLE  [action]`. */
@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            Text(
                "<<",
                style = HudType.title.copy(letterSpacing = 0.sp),
                color = Hud.Cyan,
                modifier = Modifier.hapticClickable(onClick = onBack).padding(end = 14.dp, top = 6.dp, bottom = 6.dp),
            )
        }
        Text(title, style = HudType.title, color = Hud.Text, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}
