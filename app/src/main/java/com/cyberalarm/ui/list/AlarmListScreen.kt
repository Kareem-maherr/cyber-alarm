package com.cyberalarm.ui.list

import androidx.compose.animation.animateColorAsState
import com.cyberalarm.ui.components.hapticClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cyberalarm.alarm.NextTrigger
import com.cyberalarm.data.Alarm
import com.cyberalarm.ui.components.CyberCard
import com.cyberalarm.ui.components.DayStrip
import com.cyberalarm.ui.components.FlankedCaption
import com.cyberalarm.ui.components.HexFab
import com.cyberalarm.ui.components.HexToggle
import com.cyberalarm.ui.components.LogLine
import com.cyberalarm.ui.components.NightCityScreen
import com.cyberalarm.ui.components.OutlineChip
import com.cyberalarm.ui.ringing.eddies
import com.cyberalarm.ui.components.QuoteTicker
import com.cyberalarm.ui.components.StatusStrip
import com.cyberalarm.ui.components.SweepText
import com.cyberalarm.ui.components.TerminalLog
import com.cyberalarm.ui.components.hudDate
import com.cyberalarm.ui.components.rememberNow
import com.cyberalarm.ui.permissions.Permissions
import com.cyberalarm.ui.theme.Hud
import com.cyberalarm.ui.theme.HudMotion
import com.cyberalarm.ui.theme.HudType
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFormat = DateTimeFormatter.ofPattern("EEE dd MMM", Locale.ENGLISH)

private fun shortDay(at: ZonedDateTime) = dayFormat.format(at).uppercase(Locale.ENGLISH)

private fun holidayEnd(until: Long) = shortDay(Instant.ofEpochMilli(until).atZone(ZoneId.systemDefault()))

@Composable
fun AlarmListScreen(
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onPermissions: () -> Unit,
    onLog: () -> Unit,
    onConfig: () -> Unit,
    vm: AlarmListViewModel = hiltViewModel(),
) {
    val alarms by vm.alarms.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val balance by vm.eddies.collectAsStateWithLifecycle()
    val rank by vm.rank.collectAsStateWithLifecycle()
    val now by rememberNow()
    val context = LocalContext.current
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeTick++ }
    val coreMissing = remember(resumeTick) { Permissions.coreMissing(context) }
    val insets = WindowInsets.safeDrawing.asPaddingValues()

    NightCityScreen {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp,
                top = insets.calculateTopPadding() + 18.dp,
                bottom = insets.calculateBottomPadding() + 190.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "status") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatusStrip(onSystemClick = onPermissions)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f).hapticClickable(onClick = onLog)) {
                            Text(rank.title, style = HudType.micro, color = Hud.Cyan)
                            Text(eddies(balance), style = HudType.small, color = Hud.Yellow)
                        }
                        OutlineChip("WAKE_LOG", false, onLog)
                        OutlineChip("CONFIG", false, onConfig)
                    }
                }
            }
            item(key = "clock") {
                Column(
                    Modifier.fillMaxWidth().padding(top = 34.dp, bottom = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SweepText("%02d:%02d".format(now.hour, now.minute), periodMs = 8_000)
                    FlankedCaption(hudDate(now), HudType.date, Hud.TextDim)
                }
            }
            if (settings.holidayActive()) {
                item(key = "holiday") {
                    CyberCard(Modifier.fillMaxWidth(), accent = Hud.Cyan, cut = 16.dp, contentPadding = PaddingValues(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("HOLIDAY MODE // REPEATING ALARMS OFF", style = HudType.micro, color = Hud.Cyan)
                                Text("Back on ${holidayEnd(settings.holidayUntil)}. Enjoy it, choom.", style = HudType.small, color = Hud.Text)
                            }
                            OutlineChip("END", false, vm::endHoliday)
                        }
                    }
                }
            }
            item(key = "quote") { QuoteTicker(Modifier.padding(horizontal = 12.dp).padding(bottom = 18.dp)) }
            val list = alarms
            if (list != null && list.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "NO ALARMS LOADED, CHOOM\nTAP + TO CREATE ONE",
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
                    holidayUntil = settings.holidayUntil,
                    onToggle = { vm.setEnabled(alarm, it) },
                    onSkip = { vm.toggleSkip(alarm) },
                    onClick = { onEdit(alarm.id) },
                    modifier = Modifier.animateItem(),
                )
            }
        }

        val log = buildList {
            add(LogLine("TIME.SET"))
            add(LogLine("ALARMS.LOAD" + (alarms?.let { " [${it.size}]" } ?: "")))
            if (coreMissing) add(LogLine("WARN: PERMS.MISSING  [FIX]", Hud.Yellow, onPermissions))
            add(LogLine("READY, CHOOM", Hud.Cyan))
        }
        TerminalLog(
            log,
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, bottom = insets.calculateBottomPadding() + 62.dp),
        )
        HexFab(
            onAdd,
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = insets.calculateBottomPadding() + 52.dp),
        )
    }
}

@Composable
private fun AlarmCard(
    alarm: Alarm,
    now: ZonedDateTime,
    holidayUntil: Long,
    onToggle: (Boolean) -> Unit,
    onSkip: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val on = alarm.enabled
    val timeColor by animateColorAsState(if (on) Hud.Yellow else Hud.Off, HudMotion.fade(600), label = "time")
    val subColor by animateColorAsState(if (on) Hud.Text else Hud.Off, HudMotion.fade(600), label = "sub")
    CyberCard(modifier.fillMaxWidth(), active = on, onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(alarm.label.ifBlank { "ALARM" }, style = HudType.chip.copy(letterSpacing = 3.sp), color = subColor)
                    Text(alarm.timeText(), style = HudType.cardTime, color = timeColor)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HexToggle(on, onToggle)
                    Text(
                        if (on) NextTrigger.countdown(NextTrigger.compute(alarm, now, holidayUntil), now) else "",
                        style = HudType.micro.copy(letterSpacing = 1.sp),
                        color = Hud.Yellow.copy(alpha = 0.7f),
                    )
                }
            }
            if (alarm.isRepeating) {
                DayStrip(alarm.repeatDays, alarmOn = on)
                if (on) {
                    val skipping = NextTrigger.isSkipping(alarm, now, holidayUntil)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (skipping) "SKIPPING ${shortDay(NextTrigger.nextUnskipped(alarm, now, holidayUntil))} // SLEEP IN" else "",
                            style = HudType.micro.copy(letterSpacing = 1.sp),
                            color = Hud.Cyan,
                            modifier = Modifier.weight(1f),
                        )
                        OutlineChip(if (skipping) "UNDO SKIP" else "SKIP NEXT", skipping, onSkip)
                    }
                }
            } else {
                Text("ONCE // NO REPEAT", style = HudType.micro.copy(fontSize = 9.5.sp, letterSpacing = 1.sp), color = if (on) Hud.Text else Hud.Faint)
            }
        }
    }
}
