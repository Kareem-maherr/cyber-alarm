package com.cyberalarm.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberalarm.BuildConfig
import com.cyberalarm.ui.theme.Hud
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

/** `■ SYS.ONLINE ────`  ...  `ALARM CLOCK v2.4.1 / NEON UI / ANDROID / 00:HH:MM ┐` */
@Composable
fun StatusStrip(now: ZonedDateTime, modifier: Modifier = Modifier, onSystemClick: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(Hud.Yellow))
            Spacer(Modifier.width(10.dp))
            Text("SYS.ONLINE", style = HudType.small.copy(letterSpacing = 2.sp), color = Hud.Yellow)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.width(96.dp).height(1.dp).background(Hud.Yellow.copy(alpha = 0.7f)))
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier
                .then(if (onSystemClick != null) Modifier.clickable(onClick = onSystemClick) else Modifier)
                .drawBehind {
                    val x = size.width - 1.dp.toPx()
                    val tick = 10.dp.toPx()
                    drawLine(Hud.Yellow, Offset(x - tick, 0f), Offset(x, 0f), 1.5.dp.toPx())
                    drawLine(Hud.Yellow, Offset(x, 0f), Offset(x, size.height), 1.5.dp.toPx())
                }
                .padding(end = 14.dp),
        ) {
            Column(horizontalAlignment = Alignment.Start) {
                val dim = Hud.Yellow.copy(alpha = 0.8f)
                Text("ALARM CLOCK v${BuildConfig.VERSION_NAME}", style = HudType.small.copy(fontSize = 10.sp), color = Hud.Yellow)
                Text("NEON UI / ANDROID", style = HudType.small.copy(fontSize = 10.sp), color = dim)
                Text("00:%02d:%02d".format(now.hour, now.minute), style = HudType.small.copy(fontSize = 10.sp), color = dim)
            }
        }
    }
}

data class LogLine(val text: String, val color: Color = Hud.Red, val onClick: (() -> Unit)? = null)

/** Red terminal readout, typed in line by line, with a blinking cursor. */
@Composable
fun TerminalLog(lines: List<LogLine>, modifier: Modifier = Modifier) {
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(lines.size) {
        while (shown < lines.size) {
            delay(320)
            shown++
        }
        if (shown > lines.size) shown = lines.size
    }
    val blink by rememberInfiniteTransition(label = "cursor").animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(530), RepeatMode.Reverse), label = "cursorAlpha",
    )
    Column(
        modifier
            .drawBehind {
                val s = 1.5.dp.toPx()
                val c = Hud.Red
                drawLine(c, Offset(0f, 0f), Offset(0f, size.height), s)
                drawLine(c, Offset(0f, 0f), Offset(4.dp.toPx(), -4.dp.toPx()), s)
                drawLine(c, Offset(0f, size.height), Offset(size.width * 0.75f, size.height), s)
            }
            .padding(start = 14.dp, top = 6.dp, bottom = 10.dp, end = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        lines.take(shown).forEachIndexed { i, line ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "> ${line.text}",
                    style = HudType.small,
                    color = line.color,
                    modifier = if (line.onClick != null) Modifier.clickable(onClick = line.onClick) else Modifier,
                )
                if (i == shown - 1) {
                    Spacer(Modifier.width(4.dp))
                    Box(Modifier.size(6.dp, 11.dp).graphicsLayer { alpha = blink }.background(line.color))
                }
            }
        }
    }
}

/** Glowing hexagonal "+" button with an offset ghost outline. */
@Composable
fun HexFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction, 0.9f)
    val pulse by rememberInfiniteTransition(label = "fab").animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "fabGlow",
    )
    Canvas(
        modifier
            .size(84.dp, 76.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interaction, null, onClick = onClick),
    ) {
        val inset = 8.dp.toPx()
        val inner = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
        val ghost = hexagonPath(inner).apply { translate(Offset(inset + 5.dp.toPx(), inset - 5.dp.toPx())) }
        drawPath(ghost, Hud.Yellow.copy(alpha = 0.55f), style = Stroke(1.5.dp.toPx()))
        val hex = hexagonPath(inner).apply { translate(Offset(inset, inset)) }
        drawPath(hex, Hud.Yellow.copy(alpha = 0.10f * pulse), style = Stroke(18.dp.toPx()))
        drawPath(hex, Hud.Yellow.copy(alpha = 0.20f * pulse), style = Stroke(9.dp.toPx()))
        drawPath(hex, Hud.Yellow)
        val c = Offset(inset + inner.width / 2, inset + inner.height / 2)
        val arm = 13.dp.toPx()
        val sw = 3.dp.toPx()
        drawLine(Hud.Bg, c - Offset(arm, 0f), c + Offset(arm, 0f), sw)
        drawLine(Hud.Bg, c - Offset(0f, arm), c + Offset(0f, arm), sw)
    }
}

/** Top bar for secondary screens: `<< BACK   TITLE   [action]`. */
@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            Text(
                "<<",
                style = HudType.title,
                color = Hud.Cyan,
                modifier = Modifier.clickable(onClick = onBack).padding(end = 14.dp, top = 4.dp, bottom = 4.dp),
            )
        }
        Text(title, style = HudType.title, color = Hud.Yellow, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
        action?.invoke()
    }
}
