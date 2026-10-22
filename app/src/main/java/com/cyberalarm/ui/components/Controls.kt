package com.cyberalarm.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberalarm.ui.theme.DisplayFont
import com.cyberalarm.ui.theme.Hud
import com.cyberalarm.ui.theme.HudMotion
import com.cyberalarm.ui.theme.HudType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/** Press feedback shared by tappable HUD elements: scale with the design's ease-out curve. */
@Composable
fun pressScale(interaction: MutableInteractionSource, pressed: Float = 0.97f): Float {
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) pressed else 1f, HudMotion.glide(300), label = "press")
    return scale
}

/**
 * Night City panel: corners cut on one diagonal, 1px border, a soft diagonal tint of [accent]
 * when [active], a 2dp accent bar on the left edge and a faint glow. Everything cross-fades
 * (0.6s) when [active] or [accent] change.
 */
@Composable
fun CyberCard(
    modifier: Modifier = Modifier,
    active: Boolean = true,
    accent: Color = Hud.Yellow,
    onClick: (() -> Unit)? = null,
    cut: Dp = 26.dp,
    contentPadding: PaddingValues = PaddingValues(start = 22.dp, end = 18.dp, top = 16.dp, bottom = 16.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction, 0.985f)
    val border by animateColorAsState(if (active) accent.copy(alpha = 0.45f) else Hud.Grey.copy(alpha = 0.2f), HudMotion.fade(600), label = "cardBorder")
    val tint by animateColorAsState(if (active) accent.copy(alpha = 0.06f) else Color.Transparent, HudMotion.fade(600), label = "cardTint")
    val bar by animateColorAsState(if (active) accent else Hud.Grey.copy(alpha = 0.4f), HudMotion.fade(600), label = "cardBar")
    val glow by animateFloatAsState(if (active) 1f else 0f, HudMotion.fade(600), label = "cardGlow")
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawWithCache {
                val c = cut.toPx()
                val path = diagonalCutPath(size, c)
                // linear-gradient(135deg, tint, panel 45%)
                val fill = Brush.linearGradient(
                    0f to tint, 0.45f to Hud.BgRaised.copy(alpha = 0.85f), 1f to Hud.BgRaised.copy(alpha = 0.85f),
                    start = Offset.Zero, end = Offset(size.width, size.width),
                )
                val barTop = 16.dp.toPx()
                val barBottom = size.height - maxOf(c, 16.dp.toPx())
                val glowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    maskFilter = android.graphics.BlurMaskFilter(24.dp.toPx(), android.graphics.BlurMaskFilter.Blur.NORMAL)
                }
                val androidPath = path.asAndroidPath()
                onDrawBehind {
                    // drop-shadow(0 8px 24px accent/.08). Blur masks are hardware-accelerated from
                    // API 28; older devices simply skip the glow.
                    if (glow > 0f && android.os.Build.VERSION.SDK_INT >= 28) {
                        glowPaint.color = accent.copy(alpha = 0.10f * glow).toArgb()
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.save()
                            canvas.nativeCanvas.translate(0f, 8.dp.toPx())
                            canvas.nativeCanvas.drawPath(androidPath, glowPaint)
                            canvas.nativeCanvas.restore()
                        }
                    }
                    drawPath(path, fill)
                    drawPath(path, border, style = Stroke(1.dp.toPx()))
                    drawRect(bar, topLeft = Offset(0f, barTop), size = androidx.compose.ui.geometry.Size(2.dp.toPx(), barBottom - barTop))
                }
            }
            .then(if (onClick != null) Modifier.hapticClickable(interaction, indication = null, onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

/** Hexagon-pill toggle; the knob glides (550ms ease-out) and colors fade (500ms). */
@Composable
fun HexToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 60.dp,
    height: Dp = 28.dp,
) {
    val progress by animateFloatAsState(if (checked) 1f else 0f, HudMotion.glide(), label = "knob")
    val line by animateColorAsState(if (checked) Hud.Yellow else Hud.Grey.copy(alpha = 0.45f), HudMotion.fade(), label = "track")
    val fill by animateColorAsState(if (checked) Hud.Yellow else Hud.ToggleOff, HudMotion.fade(), label = "fill")
    val knob by animateColorAsState(if (checked) Hud.Bg else Hud.Grey, HudMotion.fade(), label = "knobColor")
    val tick = rememberTick()
    Canvas(
        modifier
            .size(width, height)
            .toggleable(
                value = checked,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
                onValueChange = {
                    tick()
                    onCheckedChange(it)
                },
            ),
    ) {
        drawPath(hexPillPath(size, 0.5f), line)
        val inset = 1.dp.toPx()
        val inner = hexPillPath(androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2), 0.5f)
        translate(inset, inset) { drawPath(inner, fill) }
        val d = size.height * 0.57f
        val pad = (size.height - d) / 2
        val x = pad + (size.width - d - 2 * pad) * progress
        drawCircle(knob, d / 2, Offset(x + d / 2, size.height / 2))
    }
}

/**
 * 7-day strip: a 2dp bar over each day name. Interactive in the editor ([onToggle] set) with a
 * soft yellow wash on selected days; read-only on alarm cards, dimmed when the alarm is off.
 */
@Composable
fun DayStrip(
    mask: Int,
    modifier: Modifier = Modifier,
    alarmOn: Boolean = true,
    onToggle: ((DayOfWeek) -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        DayOfWeek.entries.forEach { day ->
            val active = mask and (1 shl (day.value - 1)) != 0
            val barColor = when {
                active && alarmOn -> Hud.Yellow
                active -> Hud.Grey.copy(alpha = 0.45f)
                onToggle != null -> Hud.Grey.copy(alpha = 0.18f)
                else -> Hud.Grey.copy(alpha = 0.12f)
            }
            val textColor = when {
                active && alarmOn -> Hud.Text
                active -> Hud.Grey
                onToggle != null -> Hud.OffDay
                else -> Hud.Faint
            }
            val bar by animateColorAsState(barColor, HudMotion.fade(600), label = "dayBar")
            val text by animateColorAsState(textColor, HudMotion.fade(600), label = "dayText")
            val wash by animateFloatAsState(if (active && onToggle != null) 1f else 0f, HudMotion.fade(), label = "dayWash")
            Column(
                Modifier
                    .weight(1f)
                    .drawBehind {
                        if (wash > 0f) drawRect(Brush.verticalGradient(listOf(Hud.Yellow.copy(alpha = 0.07f * wash), Color.Transparent)))
                    }
                    .then(if (onToggle != null) Modifier.hapticClickable { onToggle(day) }.padding(bottom = 10.dp) else Modifier),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (onToggle != null) 8.dp else 5.dp),
            ) {
                Box(Modifier.fillMaxWidth().height(2.dp).drawBehind { drawRect(bar) })
                Text(
                    day.getDisplayName(JavaTextStyle.SHORT, Locale.ENGLISH).uppercase(Locale.ENGLISH),
                    style = HudType.micro.copy(fontSize = if (onToggle != null) 11.sp else 9.5.sp, letterSpacing = 1.sp),
                    color = text,
                )
            }
        }
    }
}

/** Rectangular outline chip (repeat presets): bright when [selected]. */
@Composable
fun OutlineChip(text: String, selected: Boolean, onClick: () -> Unit, color: Color = Hud.Cyan) {
    val textColor by animateColorAsState(if (selected) Hud.Text else color, HudMotion.fade(400), label = "chipText")
    val line by animateColorAsState(if (selected) color else color.copy(alpha = 0.3f), HudMotion.fade(400), label = "chipLine")
    Box(
        Modifier
            .height(26.dp)
            .border(1.dp, line)
            .hapticClickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = HudType.micro, color = textColor)
    }
}

/** Three-way segmented control (difficulty). */
@Composable
fun SegmentedControl(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().border(1.dp, Hud.Yellow.copy(alpha = 0.3f))) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val bg by animateColorAsState(if (on) Hud.Yellow else Color.Transparent, HudMotion.fade(400), label = "segBg")
            val fg by animateColorAsState(if (on) Hud.Bg else Hud.TextDim, HudMotion.fade(400), label = "segFg")
            Box(
                Modifier
                    .weight(1f)
                    .height(30.dp)
                    .drawBehind { drawRect(bg) }
                    .hapticClickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = HudType.chip.copy(letterSpacing = 2.sp), color = fg)
            }
        }
    }
}

/** Small solid tag, e.g. the `#1` chain index. */
@Composable
fun IndexBadge(text: String, modifier: Modifier = Modifier) {
    Box(modifier.drawBehind { drawRect(Hud.Yellow) }.padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(text, style = HudType.micro.copy(letterSpacing = 0.sp), color = Hud.Bg)
    }
}

/** `■ ONLINE` / `■ OFFLINE` state tag. */
@Composable
fun StateTag(online: Boolean, modifier: Modifier = Modifier) {
    val color by animateColorAsState(if (online) Hud.Yellow else Hud.Red, HudMotion.fade(600), label = "state")
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(6.dp).drawBehind { drawRoundRect(color, cornerRadius = CornerRadius(1.dp.toPx())) })
        Text(if (online) "ONLINE" else "OFFLINE", style = HudType.micro, color = color)
    }
}

/**
 * Clipped-corner button (top-left / bottom-right cuts). Filled = primary action in Chakra Petch;
 * outline = secondary action in mono with a dark inner fill.
 */
@Composable
fun CyberButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Hud.Yellow,
    filled: Boolean = false,
    enabled: Boolean = true,
    height: Dp = 46.dp,
    cut: Dp = 10.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction, 0.96f)
    val c = if (enabled) color else Hud.GreyDim
    val fill by animateColorAsState(if (filled) c else if (c == Hud.Red) Hud.RedFill else Hud.ButtonFill, HudMotion.fade(400), label = "btnFill")
    val label by animateColorAsState(if (filled) Hud.Bg else c, HudMotion.fade(400), label = "btnText")
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .height(height)
            .drawWithCache {
                val outer = buttonCutPath(size, cut.toPx())
                val inset = 1.dp.toPx()
                val inner = buttonCutPath(androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2), cut.toPx() - inset / 2)
                onDrawBehind {
                    drawPath(outer, if (filled) fill else c.copy(alpha = 0.5f))
                    if (!filled) translate(inset, inset) { drawPath(inner, fill) }
                }
            }
            .hapticClickable(interaction, null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = if (filled) HudType.title.copy(fontSize = 14.sp, letterSpacing = 2.sp) else HudType.micro.copy(fontSize = 13.sp),
            color = label,
            textAlign = TextAlign.Center,
        )
    }
}

/** `LABEL      [-]  VALUE  [+]` with hold-to-repeat hexagon buttons. */
@Composable
fun HudStepper(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    valueWidth: Dp = 88.dp,
) {
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = HudType.micro, color = Hud.TextDim, modifier = Modifier.weight(1f))
        RepeatButton("-", onMinus)
        Text(value, style = HudType.label.copy(letterSpacing = 2.sp), color = Hud.Yellow, textAlign = TextAlign.Center, modifier = Modifier.width(valueWidth))
        RepeatButton("+", onPlus)
    }
}

/**
 * Hold-to-repeat button. [hex] = outlined hexagon (steppers); otherwise a bare glyph that lights
 * up yellow while pressed (the ▲ / ▼ time arrows).
 */
@Composable
fun RepeatButton(
    symbol: String,
    onStep: () -> Unit,
    color: Color = Hud.Yellow,
    width: Dp = 34.dp,
    height: Dp = 30.dp,
    hex: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    // Keyed on Unit + rememberUpdatedState so recompositions don't restart (cancel) a held press.
    val step by rememberUpdatedState(onStep)
    val tick = rememberTick()
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, HudMotion.glide(250), label = "rep")
    val glyph by animateColorAsState(if (hex || pressed) color else Hud.TextDim, HudMotion.fade(300), label = "repGlyph")
    Box(
        Modifier
            .size(width, height)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(
                if (hex) {
                    Modifier.drawBehind {
                        val path = hexagonPath(size)
                        drawPath(path, color.copy(alpha = 0.4f))
                        val inset = 1.dp.toPx()
                        translate(inset, inset) {
                            drawPath(hexagonPath(androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)), if (pressed) color.copy(alpha = 0.18f) else Hud.Field)
                        }
                    }
                } else {
                    Modifier
                },
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    tick()
                    step()
                    var job: Job? = scope.launch {
                        delay(420)
                        while (true) { tick(); step(); delay(70) }
                    }
                    waitForUpOrCancellation()
                    job?.cancel()
                    job = null
                    pressed = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = if (hex) HudType.title.copy(letterSpacing = 0.sp) else HudType.label.copy(letterSpacing = 0.sp), color = glyph)
    }
}

/** `// SECTION ─────` divider label. */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Hud.Cyan,
    top: Dp = 24.dp,
    bottom: Dp = 10.dp,
) {
    Row(modifier.fillMaxWidth().padding(top = top, bottom = bottom), verticalAlignment = Alignment.CenterVertically) {
        Text("// $text", style = HudType.micro, color = color)
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .drawBehind { drawRect(Brush.horizontalGradient(listOf(color.copy(alpha = 0.4f), Color.Transparent))) },
        )
    }
}

/** Form row: small caption, bright value, `>>` or a trailing control, hairline underneath. */
@Composable
fun SettingRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Hud.Yellow,
    onClick: (() -> Unit)? = null,
    divider: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val color by animateColorAsState(valueColor, HudMotion.fade(), label = "rowValue")
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.hapticClickable(onClick = onClick) else Modifier)
            .drawBehind {
                if (divider) drawLine(Hud.Grey.copy(alpha = 0.15f), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = HudType.micro, color = Hud.TextDim)
            Text(value, style = HudType.value, color = color, maxLines = 3)
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Text(">>", style = HudType.value.copy(fontSize = 14.sp, letterSpacing = 0.sp), color = Hud.TextDim)
        }
    }
}

/** Press and hold for [holdMillis] to confirm. Used when an alarm has no challenges. */
@Composable
fun HoldToConfirm(text: String, onConfirmed: () -> Unit, modifier: Modifier = Modifier, holdMillis: Int = 1800) {
    var holding by remember { mutableStateOf(false) }
    val confirm by rememberUpdatedState(onConfirmed)
    val context = androidx.compose.ui.platform.LocalContext.current
    val tick = rememberTick()
    val progress by animateFloatAsState(
        targetValue = if (holding) 1f else 0f,
        animationSpec = tween(if (holding) holdMillis else 300, easing = HudMotion.Ease),
        finishedListener = {
            if (it >= 1f) {
                com.cyberalarm.feedback.Haptics.get(context).success()
                confirm()
            }
        },
        label = "hold",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .drawWithCache {
                val path = buttonCutPath(size, 10.dp.toPx())
                onDrawBehind {
                    drawPath(path, Hud.ButtonFill)
                    drawPath(path, Hud.Yellow.copy(alpha = 0.5f), style = Stroke(1.dp.toPx()))
                    clipPath(path) { drawRect(Hud.Yellow, size = size.copy(width = size.width * progress)) }
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    tick()
                    holding = true
                    waitForUpOrCancellation()
                    holding = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = HudType.title.copy(fontSize = 15.sp), color = if (progress > 0.5f) Hud.Bg else Hud.Yellow)
    }
}

/** Display-font text helper for big numerals and headings. */
fun displayStyle(size: Int, weight: androidx.compose.ui.text.font.FontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, spacing: Float = 2f): TextStyle =
    TextStyle(fontFamily = DisplayFont, fontWeight = weight, fontSize = size.sp, letterSpacing = spacing.sp)
