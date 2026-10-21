package com.cyberalarm.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cyberalarm.ui.theme.Hud
import com.cyberalarm.ui.theme.HudType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Subtle press feedback shared by all tappable HUD elements. */
@Composable
fun pressScale(interaction: MutableInteractionSource, pressed: Float = 0.97f): Float {
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) pressed else 1f, spring(stiffness = 900f), label = "press")
    return scale
}

/**
 * Angular panel: clipped corners, glowing border, accent tab on the left edge and hazard stripes
 * in the bottom-right corner. Grey and glow-less when [active] is false.
 */
@Composable
fun CyberCard(
    modifier: Modifier = Modifier,
    active: Boolean = true,
    accent: Color = if (active) Hud.Yellow else Hud.Grey,
    onClick: (() -> Unit)? = null,
    stripes: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(start = 26.dp, end = 18.dp, top = 18.dp, bottom = 20.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction, 0.98f)
    val color by animateColorAsState(accent, tween(250), label = "cardAccent")
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawWithCache {
                val tl = 12.dp.toPx()
                val tr = 44.dp.toPx()
                val br = 30.dp.toPx()
                val bl = 30.dp.toPx()
                val w = size.width
                val h = size.height
                val path = chamferPath(size, tl, tr, br, bl)
                val fill = Brush.verticalGradient(listOf(Hud.BgRaised.copy(alpha = 0.92f), Hud.Bg.copy(alpha = 0.92f)))
                val tabTop = tl + 8.dp.toPx()
                val tabBottom = h * 0.55f
                val tab = Path().apply {
                    moveTo(-5.dp.toPx(), tabTop + 7.dp.toPx())
                    lineTo(1.dp.toPx(), tabTop)
                    lineTo(1.dp.toPx(), tabBottom)
                    lineTo(-5.dp.toPx(), tabBottom - 5.dp.toPx())
                    close()
                }
                val o = 4.dp.toPx()
                val tickStart = Offset(-o + tl * 0.2f, tl - o - tl * 0.2f)
                val tickEnd = Offset(-o + tl * 0.8f, tl - o - tl * 0.8f)
                val stripePath = Path().apply {
                    val baseX = w - br - 58.dp.toPx()
                    val yB = h - 3.dp.toPx()
                    val yT = h - 9.dp.toPx()
                    repeat(4) { i ->
                        val x = baseX + i * 10.dp.toPx()
                        moveTo(x, yB); lineTo(x + 5.dp.toPx(), yB)
                        lineTo(x + 10.dp.toPx(), yT); lineTo(x + 5.dp.toPx(), yT); close()
                    }
                }
                onDrawBehind {
                    drawPath(path, fill)
                    neonStroke(path, color, 1.5.dp.toPx(), glow = active)
                    drawPath(tab, color)
                    drawLine(color, tickStart, tickEnd, 2.dp.toPx())
                    if (stripes) drawPath(stripePath, color)
                }
            }
            .then(
                if (onClick != null) Modifier.clickable(interaction, indication = null, onClick = onClick) else Modifier,
            )
            .padding(contentPadding),
        content = content,
    )
}

/** Hexagon-pill toggle: filled glowing yellow when on, grey outline when off. */
@Composable
fun HexToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 64.dp,
    height: Dp = 32.dp,
) {
    val progress by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 700f), label = "toggle")
    Canvas(
        modifier
            .size(width, height)
            .toggleable(
                value = checked,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
    ) {
        val path = hexPillPath(size, 0.5f)
        val on = Hud.Yellow
        if (progress > 0.01f) {
            val c = on.copy(alpha = progress)
            drawPath(path, c.copy(alpha = 0.10f * progress), style = Stroke(14.dp.toPx()))
            drawPath(path, c.copy(alpha = 0.18f * progress), style = Stroke(7.dp.toPx()))
            drawPath(path, c)
        }
        if (progress < 0.99f) {
            drawPath(path, Hud.Grey.copy(alpha = 1f - progress), style = Stroke(1.5.dp.toPx()))
        }
        val r = size.height * 0.3f
        val start = size.height * 0.62f
        val end = size.width - size.height * 0.62f
        val cx = start + (end - start) * progress
        val knob = androidx.compose.ui.graphics.lerp(Hud.Grey, Hud.Bg, progress)
        drawCircle(knob, r, Offset(cx, size.height / 2))
    }
}

/** Small hexagon chip, e.g. a weekday. Outline by default; [filled] inverts it. */
@Composable
fun HexChip(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Hud.Yellow,
    filled: Boolean = false,
    onClick: (() -> Unit)? = null,
    style: TextStyle = HudType.chip,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction, 0.92f)
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .height(24.dp)
            .drawBehind {
                val path = hexPillPath(size, 0.42f)
                if (filled) drawPath(path, color) else drawPath(path, color, style = Stroke(1.3.dp.toPx()))
            }
            .then(if (onClick != null) Modifier.clickable(interaction, null, onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = style, color = if (filled) Hud.Bg else color)
    }
}

/** Clipped-corner button. */
@Composable
fun CyberButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Hud.Yellow,
    filled: Boolean = false,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction, 0.95f)
    val c = if (enabled) color else Hud.GreyDim
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .height(48.dp)
            .drawWithCache {
                val path = chamferPath(size, 10.dp.toPx(), 0f, 10.dp.toPx(), 0f)
                onDrawBehind {
                    if (filled) {
                        neonStroke(path, c, 1.dp.toPx(), glow = enabled)
                        drawPath(path, c)
                    } else {
                        drawPath(path, c.copy(alpha = 0.06f))
                        neonStroke(path, c, 1.5.dp.toPx(), glow = false)
                    }
                }
            }
            .clickable(interaction, null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = HudType.label.copy(fontSize = HudType.body.fontSize),
            color = if (filled) Hud.Bg else c,
            textAlign = TextAlign.Center,
        )
    }
}

/** `[-] VALUE [+]` with hold-to-repeat. */
@Composable
fun HudStepper(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Hud.Yellow,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = HudType.small, color = Hud.TextDim, modifier = Modifier.weight(1f))
        RepeatButton("-", onMinus, color)
        Text(
            value,
            style = HudType.label,
            color = color,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(96.dp),
        )
        RepeatButton("+", onPlus, color)
    }
}

@Composable
fun RepeatButton(symbol: String, onStep: () -> Unit, color: Color = Hud.Yellow, size: Dp = 36.dp) {
    val scope = rememberCoroutineScope()
    // Keyed on Unit + rememberUpdatedState so recompositions don't restart (cancel) a held press.
    val step by rememberUpdatedState(onStep)
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, label = "rep")
    Box(
        Modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBehind {
                val path = hexagonPath(this.size)
                drawPath(path, color.copy(alpha = if (pressed) 0.25f else 0.06f))
                drawPath(path, color, style = Stroke(1.3.dp.toPx()))
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    step()
                    var job: Job? = scope.launch {
                        delay(420)
                        while (true) { step(); delay(70) }
                    }
                    waitForUpOrCancellation()
                    job?.cancel()
                    job = null
                    pressed = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = HudType.title, color = color)
    }
}

/** `// SECTION ─────` divider label. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = Hud.Cyan) {
    Row(modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("// $text", style = HudType.small, color = color)
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .drawBehind { drawRect(Brush.horizontalGradient(listOf(color.copy(alpha = 0.6f), Color.Transparent))) },
        )
    }
}

/** Row used in forms: dim title, bright value, optional trailing control. */
@Composable
fun SettingRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Hud.Yellow,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(title, style = HudType.small, color = Hud.TextDim)
            Text(value, style = HudType.label.copy(letterSpacing = HudType.body.letterSpacing * 2), color = valueColor, maxLines = 2)
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Text(">>", style = HudType.label, color = valueColor)
        }
    }
}

/** Press and hold for [holdMillis] to confirm. Used when an alarm has no challenges. */
@Composable
fun HoldToConfirm(text: String, onConfirmed: () -> Unit, modifier: Modifier = Modifier, holdMillis: Int = 1800) {
    var holding by remember { mutableStateOf(false) }
    val confirm by rememberUpdatedState(onConfirmed)
    val progress by animateFloatAsState(
        targetValue = if (holding) 1f else 0f,
        animationSpec = tween(if (holding) holdMillis else 250),
        finishedListener = { if (it >= 1f) confirm() },
        label = "hold",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .drawWithCache {
                val path = chamferPath(size, 14.dp.toPx(), 0f, 14.dp.toPx(), 0f)
                onDrawBehind {
                    neonStroke(path, Hud.Yellow, 1.5.dp.toPx())
                    drawRect(Hud.Yellow.copy(alpha = 0.85f), size = size.copy(width = size.width * progress))
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    holding = true
                    waitForUpOrCancellation()
                    holding = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = HudType.label, color = if (progress > 0.5f) Hud.Bg else Hud.Yellow)
    }
}
