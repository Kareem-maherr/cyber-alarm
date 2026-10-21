package com.cyberalarm.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cyberalarm.alarm.NextTrigger
import com.cyberalarm.data.Alarm
import com.cyberalarm.ui.components.CyberCard
import com.cyberalarm.ui.components.GlitchClock
import com.cyberalarm.ui.components.HexChip
import com.cyberalarm.ui.components.HexFab
import com.cyberalarm.ui.components.HexToggle
import com.cyberalarm.ui.components.HudDateFormat
import com.cyberalarm.ui.components.LogLine
import com.cyberalarm.ui.components.ScreenEdgeAccents
import com.cyberalarm.ui.components.StatusStrip
import com.cyberalarm.ui.components.TerminalLog
import com.cyberalarm.ui.components.cornerBrackets
import com.cyberalarm.ui.components.hudBackground
import com.cyberalarm.ui.components.rememberNow
import com.cyberalarm.ui.permissions.Permissions
import com.cyberalarm.ui.theme.Hud
import com.cyberalarm.ui.theme.HudType
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun AlarmListScreen(
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onPermissions: () -> Unit,
    vm: AlarmListViewModel = hiltViewModel(),
) {
    val alarms by vm.alarms.collectAsStateWithLifecycle()
    val now by rememberNow()
    val context = LocalContext.current
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeTick++ }
    val coreMissing = remember(resumeTick) { Permissions.coreMissing(context) }
    val insets = WindowInsets.safeDrawing.asPaddingValues()

    Box(Modifier.fillMaxSize().hudBackground()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 18.dp, end = 18.dp,
                top = insets.calculateTopPadding() + 12.dp,
                bottom = insets.calculateBottomPadding() + 170.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(key = "status") { StatusStrip(now, onSystemClick = onPermissions) }
            item(key = "clock") { ClockBlock(now) }
            val list = alarms
            if (list != null && list.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "NO ALARMS LOADED\nTAP [+] TO CREATE ONE",
                        style = HudType.label,
                        color = Hud.TextDim,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                    )
                }
            }
            items(list.orEmpty(), key = { it.id }) { alarm ->
                AlarmCard(
                    alarm = alarm,
                    now = now,
                    onToggle = { vm.setEnabled(alarm, it) },
                    onClick = { onEdit(alarm.id) },
                    modifier = Modifier.animateItem(),
                )
            }
        }

        val log = buildList {
            add(LogLine("TIME.SET"))
            add(LogLine("ALARMS.LOAD" + (alarms?.let { " [${it.size}]" } ?: "")))
            if (coreMissing) add(LogLine("WARN: PERMS.MISSING  [FIX]", Hud.Yellow, onPermissions))
            add(LogLine("READY"))
        }
        TerminalLog(
            log,
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, bottom = insets.calculateBottomPadding() + 58.dp),
        )
        HexFab(
            onAdd,
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 22.dp, bottom = insets.calculateBottomPadding() + 50.dp),
        )
        ScreenEdgeAccents(Modifier.padding(bottom = insets.calculateBottomPadding()))
    }
}

@Composable
private fun ClockBlock(now: ZonedDateTime) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .cornerBrackets(Hud.Yellow, length = 16.dp, stroke = 2.dp)
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlitchClock("%02d:%02d".format(now.hour, now.minute))
        Spacer(Modifier.height(6.dp))
        Text(
            HudDateFormat.format(now).uppercase(Locale.ENGLISH),
            style = HudType.date.copy(shadow = Shadow(Hud.Yellow.copy(alpha = 0.6f), blurRadius = 16f)),
            color = Hud.Yellow,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlarmCard(
    alarm: Alarm,
    now: ZonedDateTime,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val on = alarm.enabled
    val color = if (on) Hud.Yellow else Hud.Grey
    CyberCard(modifier.fillMaxWidth(), active = on, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    alarm.timeText(),
                    style = HudType.cardTime.copy(
                        shadow = if (on) Shadow(Hud.Yellow.copy(alpha = 0.55f), blurRadius = 22f) else null,
                    ),
                    color = color,
                )
                Text(alarm.label.ifBlank { "ALARM" }, style = HudType.label, color = color)
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!alarm.isRepeating) {
                        HexChip("ONCE", color = color)
                    } else {
                        DayOfWeek.entries.filter(alarm::repeatsOn).forEach {
                            HexChip(it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase(Locale.ENGLISH), color = color)
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                HexToggle(on, onToggle, width = 72.dp, height = 36.dp)
                if (on) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        NextTrigger.countdown(NextTrigger.compute(alarm, now), now),
                        style = HudType.small,
                        color = Hud.Yellow.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}
