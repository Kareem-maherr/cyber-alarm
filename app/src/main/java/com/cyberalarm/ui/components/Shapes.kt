package com.cyberalarm.ui.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Rectangle with independently clipped (45°) corners: the HUD's basic panel silhouette. */
fun chamferPath(size: Size, tl: Float, tr: Float, br: Float, bl: Float): Path = Path().apply {
    val w = size.width
    val h = size.height
    moveTo(tl, 0f)
    lineTo(w - tr, 0f)
    lineTo(w, tr)
    lineTo(w, h - br)
    lineTo(w - br, h)
    lineTo(bl, h)
    lineTo(0f, h - bl)
    lineTo(0f, tl)
    close()
}

class ChamferShape(
    private val tl: Dp = 0.dp,
    private val tr: Dp = 0.dp,
    private val br: Dp = 0.dp,
    private val bl: Dp = 0.dp,
) : Shape {
    constructor(all: Dp) : this(all, all, all, all)

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        with(density) { Outline.Generic(chamferPath(size, tl.toPx(), tr.toPx(), br.toPx(), bl.toPx())) }
}

/**
 * Elongated hexagon (pointed left/right ends). [pointRatio] is how far the points reach in, as a
 * fraction of the height: 0.5 = sharp 45° points, smaller = blunter.
 */
fun hexPillPath(size: Size, pointRatio: Float = 0.5f): Path = Path().apply {
    val w = size.width
    val h = size.height
    val p = h * pointRatio
    moveTo(p, 0f)
    lineTo(w - p, 0f)
    lineTo(w, h / 2)
    lineTo(w - p, h)
    lineTo(p, h)
    lineTo(0f, h / 2)
    close()
}

class HexPillShape(private val pointRatio: Float = 0.5f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(hexPillPath(size, pointRatio))
}

/** Regular flat-top hexagon inscribed in [size]. */
fun hexagonPath(size: Size): Path = Path().apply {
    val w = size.width
    val h = size.height
    moveTo(w * 0.25f, 0f)
    lineTo(w * 0.75f, 0f)
    lineTo(w, h / 2)
    lineTo(w * 0.75f, h)
    lineTo(w * 0.25f, h)
    lineTo(0f, h / 2)
    close()
}

/**
 * Cheap neon glow: the same stroke drawn a few times, wider and fainter. Avoids BlurMaskFilter,
 * which isn't hardware-accelerated below API 28 and is costly during animation.
 */
fun DrawScope.neonStroke(path: Path, color: Color, width: Float, glow: Boolean = true) {
    if (glow) {
        drawPath(path, color.copy(alpha = 0.06f), style = Stroke(width * 7))
        drawPath(path, color.copy(alpha = 0.12f), style = Stroke(width * 4))
        drawPath(path, color.copy(alpha = 0.25f), style = Stroke(width * 2))
    }
    drawPath(path, color, style = Stroke(width))
}
