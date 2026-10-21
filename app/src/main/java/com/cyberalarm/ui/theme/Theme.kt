package com.cyberalarm.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.cyberalarm.R

/** HUD palette. Yellow is the primary signal; red/cyan are secondary accents; grey = disabled. */
object Hud {
    val Bg = Color(0xFF05090B)
    val BgRaised = Color(0xFF0A1215)
    val Glow = Color(0xFF0C2A2F)
    val Grid = Color(0xFF12343A)
    val Yellow = Color(0xFFF0FF00)
    val YellowDim = Color(0xFF7D8500)
    val Red = Color(0xFFFF2A3D)
    val Cyan = Color(0xFF00E5FF)
    val Grey = Color(0xFF7D8896)
    val GreyDim = Color(0xFF2D3740)
    val Text = Color(0xFFE6EDF2)
    val TextDim = Color(0xFF8C98A3)
}

val DisplayFont = FontFamily(
    Font(R.font.chakra_petch_bold, FontWeight.Bold),
    Font(R.font.chakra_petch_semibold, FontWeight.SemiBold),
)
val MonoFont = FontFamily(Font(R.font.share_tech_mono))

object HudType {
    val clock = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 92.sp, letterSpacing = 4.sp)
    val cardTime = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 50.sp, letterSpacing = 3.sp)
    val title = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = 3.sp)
    val label = TextStyle(fontFamily = MonoFont, fontSize = 18.sp, letterSpacing = 3.sp)
    val body = TextStyle(fontFamily = MonoFont, fontSize = 14.sp, letterSpacing = 1.sp, lineHeight = 20.sp)
    val chip = TextStyle(fontFamily = MonoFont, fontSize = 12.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.Bold)
    val small = TextStyle(fontFamily = MonoFont, fontSize = 11.sp, letterSpacing = 1.sp)
    val date = TextStyle(fontFamily = MonoFont, fontSize = 20.sp, letterSpacing = 7.sp)
}

private val colors = darkColorScheme(
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
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
