package com.cyberalarm.ui.splash

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberalarm.BuildConfig
import com.cyberalarm.R
import com.cyberalarm.ui.components.rememberAmbientMotion
import com.cyberalarm.ui.theme.Hud
import com.cyberalarm.ui.theme.HudMotion
import com.cyberalarm.ui.theme.HudType
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

private val LogoBg = Color(0xFF020206)
private val Yellow = Color(0xFFF0FF00)
private val Cyan = Color(0xFF00E5FF)
private val Red = Color(0xFFFF2A3D)

/** Timeline, in ms. */
private const val POWER_ON = 380L
private const val GLITCH_END = 1_350L
private const val BOOT_END = 2_300L
private const val OUTRO = 560L
private const val TOTAL = BOOT_END + OUTRO

/** Only the red channel / only green+blue: drawn offset and added together = chromatic split. */
private val RedOnly = ColorFilter.colorMatrix(
    ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)),
)
private val CyanOnly = ColorFilter.colorMatrix(
    ColorMatrix(floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)),
)

private val BootLines = listOf(
    "> NIGHT_CITY_OS v${BuildConfig.VERSION_NAME}",
    "> MOUNTING WAKE PROTOCOLS ......... OK",
    "> NEURAL LINK ..................... OK",
    "> WELCOME BACK, CHOOM",
)

/**
 * Launch splash: a CRT power-on, the logo tearing in through chromatic split and sliced scanline
 * glitches, a terminal boot readout, then a glitch burst and CRT power-off that reveals the app.
 * Tap anywhere to skip to the outro. With "Remove animations" on it is a short fade instead.
 */
@Composable
fun BootSplash(onFinished: () -> Unit) {
    val image = ImageBitmap.imageResource(R.drawable.splash_logo)
    val motion = rememberAmbientMotion()
    val finished by rememberUpdatedState(onFinished)
    var t by remember { mutableLongStateOf(0L) }
    var skipAt by remember { mutableLongStateOf(-1L) }

    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it } - start
            t = now
            val end = when {
                !motion -> 700L
                skipAt >= 0 -> skipAt + OUTRO
                else -> TOTAL
            }
            if (now >= end) break
        }
        finished()
    }

    // Position on the scripted timeline (skipping jumps straight into the outro).
    val pt = when {
        !motion -> if (t < 450) BOOT_END - 1 else BOOT_END + OUTRO - 250 + (t - 450)
        skipAt >= 0 && t >= skipAt -> BOOT_END + (t - skipAt)
        else -> t
    }
    val outro = ((pt - BOOT_END).toFloat() / OUTRO).coerceIn(0f, 1f)
    // The black backdrop dissolves during the last part of the outro to reveal the app.
    val backdrop = 1f - ((outro - 0.55f) / 0.45f).coerceIn(0f, 1f)

    Box(
        Modifier
            .fillMaxSize()
            .clickable(remember { MutableInteractionSource() }, null) { if (skipAt < 0 && t < BOOT_END) skipAt = t },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(LogoBg.copy(alpha = backdrop))
            drawBoot(image, pt)
        }
        BootReadout(pt, Modifier.align(Alignment.BottomStart).padding(start = 28.dp, end = 28.dp, bottom = 72.dp))
    }
}

/** Glitch parameters change in steps (every ~55ms), like a stuttering signal, not every frame. */
private fun stepRandom(pt: Long, salt: Int = 0) = Random(pt / 55 * 31 + salt)

private fun DrawScope.drawBoot(image: ImageBitmap, pt: Long) {
    val side = min(size.width, size.height) * 0.86f
    val dst = IntSize(side.toInt(), side.toInt())
    val topLeft = IntOffset(((size.width - side) / 2).toInt(), (size.height * 0.42f - side / 2).toInt())
    val cy = topLeft.y + side / 2

    when {
        // 1. CRT power-on: a bright line opens horizontally, then vertically into the picture.
        pt < POWER_ON -> {
            val p = pt.toFloat() / POWER_ON
            if (p < 0.45f) {
                val w = size.width * HudMotion.Ease.transform(p / 0.45f)
                val h = 2.dp.toPx()
                drawRect(Yellow.copy(alpha = 0.35f), Offset((size.width - w) / 2, cy - h * 3), Size(w, h * 7))
                drawRect(Color.White, Offset((size.width - w) / 2, cy - h / 2), Size(w, h))
            } else {
                val open = HudMotion.Ease.transform((p - 0.45f) / 0.55f)
                val h = max(2f, dst.height * open)
                val y = (cy - h / 2).toInt()
                drawImage(image, dstOffset = IntOffset(topLeft.x, y), dstSize = IntSize(dst.width, h.toInt()))
                drawRect(Color.White.copy(alpha = (1f - open) * 0.8f), Offset(0f, y.toFloat()), Size(size.width, h))
            }
        }
        // 2. Tear-in: heavy split + slicing that decays; 3. steady boot with rare micro-glitches.
        pt < BOOT_END -> {
            val intensity = if (pt < GLITCH_END) {
                1f - (pt - POWER_ON).toFloat() / (GLITCH_END - POWER_ON)
            } else {
                // Brief bursts so the steady logo still feels "live".
                if (stepRandom(pt, 7).nextFloat() < 0.12f) 0.35f else 0f
            }
            glitchLogo(image, topLeft, dst, intensity, stepRandom(pt))
        }
        // 4. Outro: full-strength glitch burst, then a CRT power-off collapse.
        else -> {
            val o = ((pt - BOOT_END).toFloat() / OUTRO).coerceIn(0f, 1f)
            if (o < 0.4f) {
                glitchLogo(image, topLeft, dst, 1f, stepRandom(pt, 3))
            } else {
                val c = HudMotion.Ease.transform(((o - 0.4f) / 0.35f).coerceIn(0f, 1f))
                val h = max(2f, dst.height * (1f - c))
                val w = if (c < 1f) side else side * (1f - ((o - 0.75f) / 0.25f).coerceIn(0f, 1f))
                if (c < 1f) {
                    drawImage(image, dstOffset = IntOffset(topLeft.x, (cy - h / 2).toInt()), dstSize = IntSize(dst.width, h.toInt()))
                    drawRect(Color.White.copy(alpha = c * 0.7f), Offset(topLeft.x.toFloat(), cy - h / 2), Size(side, h))
                } else if (w > 1f) {
                    drawRect(Color.White, Offset((size.width - w) / 2, cy - 1.dp.toPx()), Size(w, 2.dp.toPx()))
                }
            }
        }
    }
    scanlines(pt)
}

/**
 * The logo with [intensity] (0..1) of damage: RGB channels pulled apart, horizontal bands torn
 * sideways, a few hard-colored corruption blocks and the whole frame jittering.
 */
private fun DrawScope.glitchLogo(image: ImageBitmap, topLeft: IntOffset, dst: IntSize, intensity: Float, rnd: Random) {
    val scale = dst.width.toFloat() / image.width
    val jitter = if (rnd.nextFloat() < intensity * 0.6f) IntOffset(((rnd.nextFloat() - 0.5f) * 40 * intensity).toInt(), 0) else IntOffset.Zero
    val origin = topLeft + jitter
    val split = (intensity * (6 + rnd.nextFloat() * 22)).dp.toPx().toInt()
    val alpha = if (rnd.nextFloat() < intensity * 0.35f) 0.55f + rnd.nextFloat() * 0.3f else 1f

    if (split > 0) {
        drawImage(image, dstOffset = origin + IntOffset(-split, 0), dstSize = dst, alpha = alpha, colorFilter = RedOnly, blendMode = BlendMode.Plus)
        drawImage(image, dstOffset = origin + IntOffset(split, 0), dstSize = dst, alpha = alpha, colorFilter = CyanOnly, blendMode = BlendMode.Plus)
    } else {
        drawImage(image, dstOffset = origin, dstSize = dst, alpha = alpha)
    }

    // Torn bands: blank the band, redraw it shifted (sometimes with one channel only).
    repeat((intensity * 7 * rnd.nextFloat()).toInt() + if (intensity > 0f) 1 else 0) {
        val sy = rnd.nextInt(image.height)
        val sh = (4 + rnd.nextInt(60)).coerceAtMost(image.height - sy)
        if (sh <= 0) return@repeat
        val dy = origin.y + (sy * scale).toInt()
        val dh = (sh * scale).toInt().coerceAtLeast(1)
        val dx = ((rnd.nextFloat() - 0.5f) * 2 * 90 * intensity).dp.toPx().toInt()
        drawRect(LogoBg, Offset(origin.x.toFloat() - split, dy.toFloat()), Size(dst.width + 2f * split, dh.toFloat()))
        drawImage(
            image,
            srcOffset = IntOffset(0, sy), srcSize = IntSize(image.width, sh),
            dstOffset = IntOffset(origin.x + dx, dy), dstSize = IntSize(dst.width, dh),
            colorFilter = when (rnd.nextInt(5)) {
                0 -> RedOnly
                1 -> CyanOnly
                else -> null
            },
        )
    }

    // Corruption blocks and full-width signal bars.
    repeat((intensity * 5 * rnd.nextFloat()).toInt()) {
        val c = listOf(Yellow, Cyan, Red)[rnd.nextInt(3)]
        val w = dst.width * (0.08f + rnd.nextFloat() * 0.3f)
        val h = (2 + rnd.nextInt(14)).dp.toPx()
        val x = origin.x + rnd.nextFloat() * (dst.width - w)
        val y = origin.y + rnd.nextFloat() * dst.height
        drawRect(c.copy(alpha = 0.55f + rnd.nextFloat() * 0.4f), Offset(x, y), Size(w, h))
    }
    if (rnd.nextFloat() < intensity * 0.5f) {
        val y = rnd.nextFloat() * size.height
        drawRect(Cyan.copy(alpha = 0.18f), Offset(0f, y), Size(size.width, (1 + rnd.nextInt(3)).dp.toPx()))
    }
}

/** CRT texture: fine scanlines plus a soft bright band sweeping down. */
private fun DrawScope.scanlines(pt: Long) {
    val step = 3.dp.toPx()
    var y = 0f
    while (y < size.height) {
        drawRect(Color.Black.copy(alpha = 0.22f), Offset(0f, y), Size(size.width, 1f))
        y += step
    }
    val band = ((pt % 1_800L).toFloat() / 1_800L) * (size.height + 200f) - 100f
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.05f), Color.Transparent), startY = band - 80f, endY = band + 80f),
        Offset(0f, band - 80f),
        Size(size.width, 160f),
    )
}

/** Terminal lines typed out under the logo during the steady phase, with a load bar. */
@Composable
private fun BootReadout(pt: Long, modifier: Modifier) {
    val start = GLITCH_END - 150
    if (pt < start) return
    val fade = if (pt > BOOT_END) 1f - ((pt - BOOT_END).toFloat() / (OUTRO * 0.5f)).coerceIn(0f, 1f) else 1f
    val elapsed = (pt - start).coerceAtMost(BOOT_END - start)
    val perLine = (BOOT_END - start - 150) / BootLines.size
    Column(modifier.fillMaxWidth().graphicsLayer { alpha = fade }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BootLines.forEachIndexed { i, line ->
            val lineT = elapsed - i * perLine
            if (lineT <= 0) return@forEachIndexed
            val chars = (line.length * (lineT.toFloat() / (perLine * 0.7f))).toInt().coerceIn(0, line.length)
            val cursor = if (chars < line.length && (pt / 120) % 2 == 0L) "█" else ""
            Text(
                line.take(chars) + cursor,
                style = HudType.small.copy(fontSize = 11.sp),
                color = if (i == BootLines.lastIndex) Hud.Yellow else Cyan.copy(alpha = 0.85f),
            )
        }
        val progress = (elapsed.toFloat() / (BOOT_END - start)).coerceIn(0f, 1f)
        Box(
            Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(2.dp)
                .background(Color.White.copy(alpha = 0.08f))
                .drawBehind { drawRect(Yellow, size = size.copy(width = size.width * progress)) },
        )
    }
}
