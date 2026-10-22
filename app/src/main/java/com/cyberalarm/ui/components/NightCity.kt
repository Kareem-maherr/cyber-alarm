package com.cyberalarm.ui.components

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cyberalarm.ui.theme.Hud
import com.cyberalarm.ui.theme.HudMotion
import com.cyberalarm.ui.theme.HudType

/**
 * Screen container with the Night City backdrop behind [content]. [alert] (ringing, locked app)
 * swaps the dominant glow from cyan to red.
 */
@Composable
fun NightCityScreen(
    modifier: Modifier = Modifier,
    alert: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().clipToBounds()) {
        NightCityBackdrop(alert)
        content()
    }
}

/**
 * The design's `ambientMotion` switch, driven by the system "Remove animations" setting
 * (animator duration scale 0): ambient loops then hold still. Interaction feedback still runs.
 */
@Composable
fun rememberAmbientMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}

/**
 * Two radial "city glows" drifting slowly (18s and 24s loops, the second reversed) over a faint
 * grid that fades out toward the top and bottom. The blobs move via graphicsLayer only, so the
 * animation doesn't redraw anything.
 */
@Composable
fun NightCityBackdrop(alert: Boolean = false) {
    // `motion` never changes for this composition, so the conditional transition below is stable.
    val motion = rememberAmbientMotion()
    var a = 0f
    var b = 0f
    if (motion) {
        val drift = rememberInfiniteTransition(label = "drift")
        // CSS keyframes 0% → 50% → 100% with ease-in-out == half-period tween, reversed.
        a = drift.animateFloat(
            0f, 1f, infiniteRepeatable(tween(9_000, easing = HudMotion.CssEaseInOut), RepeatMode.Reverse), label = "driftA",
        ).value
        b = drift.animateFloat(
            0f, 1f, infiniteRepeatable(tween(12_000, easing = HudMotion.CssEaseInOut), RepeatMode.Reverse), label = "driftB",
        ).value
    }
    val topGlow by animateColorAsState(
        if (alert) Hud.Red.copy(alpha = 0.14f) else Hud.Cyan.copy(alpha = 0.14f), HudMotion.fade(900), label = "glowTop",
    )
    val bottomGlow by animateColorAsState(
        if (alert) Hud.Cyan.copy(alpha = 0.08f) else Hud.Red.copy(alpha = 0.09f), HudMotion.fade(900), label = "glowBottom",
    )

    Box(Modifier.fillMaxSize().drawBehind { drawRect(Hud.Bg) }) {
        GlowBlob(topGlow, a, Modifier.align(Alignment.TopStart).offset((-80).dp, (-120).dp), 520.dp, 460.dp)
        GlowBlob(bottomGlow, b, Modifier.align(Alignment.BottomEnd).offset(160.dp, 140.dp), 480.dp, 420.dp)
        Box(Modifier.fillMaxSize().fadingGrid())
    }
}

@Composable
private fun GlowBlob(color: Color, progress: Float, modifier: Modifier, width: Dp, height: Dp) {
    Box(
        modifier
            .requiredSize(width, height)
            .graphicsLayer {
                // drift: translate(-30px, 40px) scale(1.15) at the midpoint.
                translationX = -30.dp.toPx() * progress
                translationY = 40.dp.toPx() * progress
                val s = 1f + 0.15f * progress
                scaleX = s
                scaleY = s
            }
            .drawBehind {
                // radial-gradient(closest-side, color, transparent): an ellipse filling the box.
                val r = size.width / 2
                val brush = Brush.radialGradient(listOf(color, Color.Transparent), center, r)
                scale(1f, size.height / size.width, pivot = center) { drawCircle(brush, r, center) }
            },
    )
}

/** 56dp grid at 3.5% cyan, masked to fade out at the top and bottom 30%. */
private fun Modifier.fadingGrid(): Modifier =
    graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithCache {
        val step = 56.dp.toPx()
        val grid = Path().apply {
            var x = 0f
            while (x < size.width) { moveTo(x, 0f); lineTo(x, size.height); x += step }
            var y = 0f
            while (y < size.height) { moveTo(0f, y); lineTo(size.width, y); y += step }
        }
        val mask = Brush.verticalGradient(
            0f to Color.Transparent, 0.3f to Color.Black, 0.7f to Color.Black, 1f to Color.Transparent,
        )
        onDrawBehind {
            drawPath(grid, Hud.Cyan.copy(alpha = 0.035f), style = Stroke(1.dp.toPx()))
            drawRect(mask, blendMode = BlendMode.DstIn)
        }
    }

/**
 * Text with the "light sweep": solid color most of the time, then a pale highlight band glides
 * left-to-right across the glyphs during the last 30% of each [periodMs] cycle.
 */
@Composable
fun SweepText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = HudType.clock,
    color: Color = Hud.Yellow,
    periodMs: Int = 8_000,
    glow: Color = color.copy(alpha = 0.25f),
) {
    val measurer = rememberTextMeasurer()
    val layout = remember(text, style) { measurer.measure(AnnotatedString(text), style) }
    val motion = rememberAmbientMotion()
    val sweep = if (!motion) 0f else rememberInfiniteTransition(label = "sweep").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = periodMs
                0f at 0
                0f at (periodMs * 0.7f).toInt() using HudMotion.CssEaseInOut
                1f at periodMs
            },
        ),
        label = "sweepPos",
    ).value
    val density = LocalDensity.current
    val w = with(density) { layout.size.width.toDp() }
    val h = with(density) { layout.size.height.toDp() }
    Canvas(modifier.size(w, h)) {
        val tw = layout.size.width.toFloat()
        // CSS: 250%-wide gradient at 100deg, background-position 140% → -40%.
        val start = -2.1f * tw + 2.7f * tw * sweep
        val brush = Brush.linearGradient(
            0f to color, 0.40f to color, 0.48f to Hud.SweepHighlight, 0.56f to color, 1f to color,
            start = Offset(start, 0f),
            end = Offset(start + 2.5f * tw, 2.5f * tw * 0.176f), // tan(10°): the gradient's slight tilt
        )
        drawText(layout, brush = brush, shadow = Shadow(glow, Offset.Zero, 22.dp.toPx()))
    }
}

/** 6dp alert square that "breathes" (opacity 1 → 0.35 over 2.4s). */
@Composable
fun BreathingDot(color: Color = Hud.Red, modifier: Modifier = Modifier) {
    val alpha = if (!rememberAmbientMotion()) 1f else rememberInfiniteTransition(label = "breathe").animateFloat(
        1f, 0.35f, infiniteRepeatable(tween(1_200, easing = HudMotion.CssEaseInOut), RepeatMode.Reverse), label = "breatheA",
    ).value
    Canvas(modifier.size(6.dp).graphicsLayer { this.alpha = alpha }) {
        // box-shadow: 0 0 10px red/.7
        val r = 10.dp.toPx()
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.5f), Color.Transparent), center, r), r)
        drawRoundRect(color, cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()))
    }
}

/** Five equalizer bars that bounce while [playing]; dim and still otherwise. */
@Composable
fun EqBars(playing: Boolean, modifier: Modifier = Modifier, color: Color = Hud.Yellow) {
    val transition = rememberInfiniteTransition(label = "eq")
    val alpha by animateFloatAsState(if (playing) 1f else 0.4f, tween(400), label = "eqAlpha")
    Row(modifier.height(26.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        repeat(5) { i ->
            val scale by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(((700 + i * 130) / 2), easing = HudMotion.CssEaseInOut),
                    RepeatMode.Reverse,
                    StartOffset(i * 100),
                ),
                label = "eqBar$i",
            )
            Box(
                Modifier
                    .width(3.dp)
                    .height(26.dp)
                    .graphicsLayer {
                        this.alpha = alpha
                        scaleY = if (playing) scale else 0.3f
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                    }
                    .drawBehind { drawRect(color) },
            )
        }
    }
}

/** `── TEXT ──` caption flanked by short cyan hairlines. */
@Composable
fun FlankedCaption(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier, lineColor: Color = Hud.Cyan.copy(alpha = 0.5f)) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.width(22.dp).height(1.dp).drawBehind { drawRect(lineColor) })
        androidx.compose.material3.Text(text, style = style, color = color)
        Box(Modifier.width(22.dp).height(1.dp).drawBehind { drawRect(lineColor) })
    }
}
