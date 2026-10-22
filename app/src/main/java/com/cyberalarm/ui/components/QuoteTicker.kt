package com.cyberalarm.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberalarm.lingo.Lingo
import com.cyberalarm.ui.theme.Hud
import com.cyberalarm.ui.theme.HudMotion
import com.cyberalarm.ui.theme.HudType
import kotlinx.coroutines.delay
import kotlin.random.Random

/** A Night City quote that rotates every [periodMs]; tap to skip to the next one. */
@Composable
fun QuoteTicker(modifier: Modifier = Modifier, periodMs: Long = 14_000) {
    val quotes = Lingo.quotes
    var index by remember { mutableIntStateOf(Random.nextInt(quotes.size)) }
    LaunchedEffect(index) {
        delay(periodMs)
        index = (index + 1) % quotes.size
    }
    AnimatedContent(
        targetState = index,
        transitionSpec = {
            (fadeIn(HudMotion.fade(700)) + slideInVertically(HudMotion.glide(700)) { it / 4 }) togetherWith fadeOut(HudMotion.fade(300))
        },
        modifier = modifier
            .fillMaxWidth()
            .hapticClickable(remember { MutableInteractionSource() }, null) { index = (index + 1) % quotes.size },
        label = "quote",
    ) { i ->
        val q = quotes[i]
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "“${q.text}”",
                style = HudType.body.copy(fontSize = 13.sp),
                color = Hud.Text.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
            )
            Text("// ${q.by}", style = HudType.micro, color = Hud.Cyan.copy(alpha = 0.7f))
        }
    }
}
