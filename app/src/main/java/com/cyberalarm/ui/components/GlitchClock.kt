package com.cyberalarm.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.cyberalarm.ui.theme.Hud
import com.cyberalarm.ui.theme.HudType
import kotlinx.coroutines.delay
import kotlin.random.Random

private class GlitchSlice(val top: Float, val height: Float, val dx: Float)
private class Streak(val y: Float, val length: Float, val right: Boolean)

/**
 * Big neon digits with a permanent chromatic-aberration split, motion streaks and scanlines, plus
 * a short slice-displacement glitch burst every few seconds. Only recomposes during bursts.
 */
@Composable
fun GlitchClock(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = HudType.clock,
    color: Color = Hud.Yellow,
) {
    val measurer = rememberTextMeasurer()
    val layout = remember(text, style) { measurer.measure(AnnotatedString(text), style) }
    var slices by remember { mutableStateOf(emptyList<GlitchSlice>()) }
    var split by remember { mutableFloatStateOf(1.5f) }
    val streaks = remember {
        val rnd = Random(2077)
        List(9) { Streak(rnd.nextFloat() * 0.8f + 0.1f, rnd.nextFloat() * 0.35f + 0.1f, it % 2 == 0) }
    }

    LaunchedEffect(Unit) {
        val rnd = Random.Default
        while (true) {
            delay(rnd.nextLong(2200, 5200))
            repeat(rnd.nextInt(2, 5)) {
                slices = List(rnd.nextInt(2, 5)) {
                    GlitchSlice(rnd.nextFloat(), rnd.nextFloat() * 0.16f + 0.04f, (rnd.nextFloat() - 0.5f) * 0.1f)
                }
                split = rnd.nextFloat() * 5f + 3f
                delay(rnd.nextLong(45, 110))
            }
            slices = emptyList()
            split = 1.5f
        }
    }

    val height = with(LocalDensity.current) { layout.size.height.toDp() }
    Canvas(modifier.fillMaxWidth().height(height)) {
        val w = layout.size.width.toFloat()
        val h = layout.size.height.toFloat()
        val tx = (size.width - w) / 2f
        val origin = Offset(tx, 0f)

        // Motion streaks bleeding out of the digits.
        streaks.forEach { s ->
            val y = s.y * h
            val len = s.length * size.width * 0.5f
            val (x0, x1) = if (s.right) (tx + w * 0.7f) to (tx + w + len) else (tx - len) to (tx + w * 0.3f)
            val fade = if (s.right) listOf(color.copy(alpha = 0.5f), Color.Transparent) else listOf(Color.Transparent, color.copy(alpha = 0.5f))
            drawLine(Brush.horizontalGradient(fade, x0, x1), Offset(x0, y), Offset(x1, y), 1.5.dp.toPx())
        }

        val sp = split.dp.toPx()
        drawText(layout, color = Hud.Red.copy(alpha = 0.55f), topLeft = origin + Offset(-sp, 0f))
        drawText(layout, color = Hud.Cyan.copy(alpha = 0.45f), topLeft = origin + Offset(sp, 0f))
        drawText(layout, color = color, topLeft = origin, shadow = Shadow(color.copy(alpha = 0.7f), Offset.Zero, 28f))

        slices.forEach { s ->
            val top = s.top * h
            val bottom = (top + s.height * h).coerceAtMost(h)
            clipRect(top = top, bottom = bottom) {
                drawRect(Hud.Bg, topLeft = Offset(tx - 12f, top), size = Size(w + 24f, bottom - top))
                drawText(layout, color = color, topLeft = origin + Offset(s.dx * w, 0f))
            }
        }

        // Scanlines across the glyphs.
        val step = 3.dp.toPx()
        var y = 0f
        while (y < h) {
            drawLine(Hud.Bg.copy(alpha = 0.35f), Offset(tx - 8f, y), Offset(tx + w + 8f, y), 1f)
            y += step
        }
    }
}
