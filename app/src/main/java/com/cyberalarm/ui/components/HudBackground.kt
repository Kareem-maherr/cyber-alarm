package com.cyberalarm.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cyberalarm.ui.theme.Hud

/**
 * Near-black teal background with a faint grid, crosshair markers and scanlines. Everything is
 * built once per size in drawWithCache, so it costs a few draw calls per frame.
 */
fun Modifier.hudBackground(): Modifier = drawWithCache {
    val step = 56.dp.toPx()
    val grid = Path().apply {
        var x = 0f
        while (x < size.width) { moveTo(x, 0f); lineTo(x, size.height); x += step }
        var y = 0f
        while (y < size.height) { moveTo(0f, y); lineTo(size.width, y); y += step }
    }
    val crossLen = 6.dp.toPx()
    val crosses = Path().apply {
        var x = step * 2
        while (x < size.width) {
            var y = step * 3
            while (y < size.height) {
                moveTo(x - crossLen, y); lineTo(x + crossLen, y)
                moveTo(x, y - crossLen); lineTo(x, y + crossLen)
                y += step * 6
            }
            x += step * 4
        }
    }
    val scan = 3.dp.toPx()
    val scanlines = Path().apply {
        var y = 0f
        while (y < size.height) { moveTo(0f, y); lineTo(size.width, y); y += scan }
    }
    val topGlow = Brush.radialGradient(
        colors = listOf(Hud.Glow.copy(alpha = 0.55f), Color.Transparent),
        center = Offset(size.width / 2, 0f),
        radius = size.width * 0.9f,
    )
    val bottomGlow = Brush.radialGradient(
        colors = listOf(Hud.Glow.copy(alpha = 0.35f), Color.Transparent),
        center = Offset(size.width / 2, size.height),
        radius = size.width * 0.8f,
    )
    onDrawBehind {
        drawRect(Hud.Bg)
        drawRect(topGlow)
        drawRect(bottomGlow)
        drawPath(grid, Hud.Grid.copy(alpha = 0.22f), style = Stroke(1f))
        drawPath(crosses, Hud.Cyan.copy(alpha = 0.18f), style = Stroke(1.5f))
        drawPath(scanlines, Color.Black.copy(alpha = 0.22f), style = Stroke(1f))
    }
}

/** HUD reticle: four L-shaped brackets on the corners of the element. */
fun Modifier.cornerBrackets(color: Color = Hud.Yellow, length: Dp = 18.dp, stroke: Dp = 2.dp): Modifier =
    drawWithContent {
        drawContent()
        val l = length.toPx()
        val s = stroke.toPx()
        val w = size.width
        val h = size.height
        fun bracket(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(color, Offset(x, y), Offset(x + dx * l, y), s, StrokeCap.Square)
            drawLine(color, Offset(x, y), Offset(x, y + dy * l), s, StrokeCap.Square)
        }
        bracket(0f, 0f, 1f, 1f)
        bracket(w, 0f, -1f, 1f)
        bracket(0f, h, 1f, -1f)
        bracket(w, h, -1f, -1f)
    }

/** Screen-edge accents: cyan bracket bottom-left, red bracket bottom-right (draw on top, no input). */
@Composable
fun ScreenEdgeAccents(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val inset = 14.dp.toPx()
        val bottom = size.height - 18.dp.toPx()
        edgeBracket(Hud.Cyan, inset, bottom, mirror = false)
        edgeBracket(Hud.Red, size.width - inset, bottom, mirror = true)
    }
}

private fun DrawScope.edgeBracket(color: Color, x: Float, bottom: Float, mirror: Boolean) {
    val d = if (mirror) -1f else 1f
    val rise = 64.dp.toPx()
    val run = 70.dp.toPx()
    val bevel = 18.dp.toPx()
    val path = Path().apply {
        moveTo(x, bottom - rise)
        lineTo(x, bottom - bevel)
        lineTo(x + d * bevel, bottom)
        lineTo(x + d * run, bottom)
    }
    neonStroke(path, color, 3.dp.toPx())
    // small detached tick above the bracket
    drawLine(color, Offset(x, bottom - rise - 14.dp.toPx()), Offset(x, bottom - rise - 6.dp.toPx()), 3.dp.toPx())
}
