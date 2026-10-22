package com.cyberalarm.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.cyberalarm.R
import com.cyberalarm.cred.CrewTheme

/**
 * "Night City ambient" palette. Yellow is the primary signal and is reserved for enabled /
 * active things; cyan and red are ambient accents; greys mark disabled state.
 *
 * Yellow / SweepHighlight / Cyan follow the selected [CrewTheme] (unlocked with Street Cred). They
 * are snapshot state, so composables and draw blocks that read them update when the theme changes.
 */
object Hud {
    var theme by mutableStateOf(CrewTheme.SAMURAI)

    val Bg = Color(0xFF04080B)
    val BgRaised = Color(0xFF0A1215)
    val Field = Color(0xFF081014)
    val KeyFill = Color(0xFF071014)
    val ButtonFill = Color(0xFF061014)
    val RedFill = Color(0xFF0D080A)
    val ToggleOff = Color(0xFF060B0E)
    val Yellow: Color get() = Color(theme.primary)
    val SweepHighlight: Color get() = Color(theme.highlight)
    val Red = Color(0xFFFF2A3D)
    val Cyan: Color get() = Color(theme.secondary)
    val Grey = Color(0xFF7D8896)
    val GreyDim = Color(0xFF2D3740)
    val Text = Color(0xFFE6EDF2)
    val TextDim = Color(0xFF8C98A3)
    val Off = Color(0xFF5D6873)
    val OffDay = Color(0xFF55606A)
    val Faint = Color(0xFF3A444D)
}

/** Motion tokens from the design: one ease-out curve for movement, CSS "ease" for color fades. */
object HudMotion {
    val Ease = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    val CssEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
    val CssEaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

    /** Toggle knobs, expanding sections. */
    fun <T> glide(durationMs: Int = 550) = tween<T>(durationMs, easing = Ease)

    /** Color / border / glow transitions (0.5-0.6s in the design). */
    fun <T> fade(durationMs: Int = 500) = tween<T>(durationMs, easing = CssEase)
}

val DisplayFont = FontFamily(
    Font(R.font.chakra_petch_bold, FontWeight.Bold),
    Font(R.font.chakra_petch_semibold, FontWeight.SemiBold),
)
val MonoFont = FontFamily(Font(R.font.share_tech_mono))

object HudType {
    val clock = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 96.sp, letterSpacing = 3.sp)
    val cardTime = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 44.sp, letterSpacing = 2.sp)
    val title = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, letterSpacing = 3.sp)
    val label = TextStyle(fontFamily = MonoFont, fontSize = 16.sp, letterSpacing = 3.sp)
    val value = TextStyle(fontFamily = MonoFont, fontSize = 15.sp, letterSpacing = 2.sp)
    val body = TextStyle(fontFamily = MonoFont, fontSize = 13.sp, letterSpacing = 0.5.sp, lineHeight = 20.sp)
    val chip = TextStyle(fontFamily = MonoFont, fontSize = 11.sp, letterSpacing = 1.sp)
    val small = TextStyle(fontFamily = MonoFont, fontSize = 11.sp, letterSpacing = 1.sp, lineHeight = 16.sp)
    val micro = TextStyle(fontFamily = MonoFont, fontSize = 10.sp, letterSpacing = 2.sp)
    val date = TextStyle(fontFamily = MonoFont, fontSize = 14.sp, letterSpacing = 5.sp)
}

private fun colors() = darkColorScheme(
    primary = Hud.Yellow,
    onPrimary = Hud.Bg,
    secondary = Hud.Cyan,
    onSecondary = Hud.Bg,
    tertiary = Hud.Red,
    background = Hud.Bg,
    onBackground = Hud.Text,
    surface = Hud.BgRaised,
    onSurface = Hud.Text,
    surfaceVariant = Hud.GreyDim,
    onSurfaceVariant = Hud.TextDim,
    error = Hud.Red,
    outline = Hud.Grey,
)

private val typography = Typography().let { base ->
    Typography(
        bodyLarge = base.bodyLarge.copy(fontFamily = MonoFont),
        bodyMedium = base.bodyMedium.copy(fontFamily = MonoFont),
        bodySmall = base.bodySmall.copy(fontFamily = MonoFont),
        labelLarge = base.labelLarge.copy(fontFamily = MonoFont),
        labelMedium = base.labelMedium.copy(fontFamily = MonoFont),
        labelSmall = base.labelSmall.copy(fontFamily = MonoFont),
        titleLarge = base.titleLarge.copy(fontFamily = DisplayFont),
        titleMedium = base.titleMedium.copy(fontFamily = DisplayFont),
        headlineLarge = base.headlineLarge.copy(fontFamily = DisplayFont),
        displayLarge = base.displayLarge.copy(fontFamily = DisplayFont),
    )
}

@Composable
fun CyberTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors(), typography = typography, content = content)
}
