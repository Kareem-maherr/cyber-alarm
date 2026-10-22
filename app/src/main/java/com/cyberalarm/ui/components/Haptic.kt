package com.cyberalarm.ui.components

import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalContext
import com.cyberalarm.feedback.Haptics

/** A crisp haptic tick, for controls that handle their own gestures. */
@Composable
fun rememberTick(): () -> Unit {
    val context = LocalContext.current
    return remember { { Haptics.get(context).tick() } }
}

/** [clickable] (default ripple) that also ticks the vibrator on every tap. */
fun Modifier.hapticClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val tick = rememberTick()
    val latest by rememberUpdatedState(onClick)
    clickable(enabled = enabled) {
        tick()
        latest()
    }
}

/** [clickable] with an explicit interaction source / indication that also ticks the vibrator. */
fun Modifier.hapticClickable(
    interactionSource: MutableInteractionSource?,
    indication: Indication?,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val tick = rememberTick()
    val latest by rememberUpdatedState(onClick)
    clickable(interactionSource, indication, enabled = enabled) {
        tick()
        latest()
    }
}
