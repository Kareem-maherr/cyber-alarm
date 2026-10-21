package com.cyberalarm.ui.edit

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cyberalarm.alarm.NextTrigger
import com.cyberalarm.challenge.ChallengeSpec
import com.cyberalarm.data.Alarm
import com.cyberalarm.ui.components.CyberButton
import com.cyberalarm.ui.components.HexChip
import com.cyberalarm.ui.components.HexToggle
import com.cyberalarm.ui.components.HudStepper
import com.cyberalarm.ui.components.RepeatButton
import com.cyberalarm.ui.components.ScreenHeader
import com.cyberalarm.ui.components.SectionLabel
import com.cyberalarm.ui.components.SettingRow
import com.cyberalarm.ui.components.chamferPath
import com.cyberalarm.ui.components.cornerBrackets
import com.cyberalarm.ui.components.hudBackground
import com.cyberalarm.ui.components.neonStroke
import com.cyberalarm.ui.components.rememberNow
import com.cyberalarm.ui.permissions.Permissions
import com.cyberalarm.ui.theme.Hud
import com.cyberalarm.ui.theme.HudType
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

enum class EditPage { MAIN, CHALLENGES, APPS, SOUND, TEST }

/** Add/edit alarm, plus its sub-pages (challenge picker, app-lock picker, sound picker, test run). */
@Composable
fun EditAlarmScreen(onDone: () -> Unit, onPermissions: () -> Unit, vm: EditAlarmViewModel = hiltViewModel()) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(EditPage.MAIN) }
    var testSpecs by remember { mutableStateOf<List<ChallengeSpec>?>(null) }
    var testReturn by remember { mutableStateOf(EditPage.MAIN) }
    BackHandler(enabled = page != EditPage.MAIN) { page = if (page == EditPage.TEST) testReturn else EditPage.MAIN }

    val insets = WindowInsets.safeDrawing.asPaddingValues()
    Box(
        Modifier
            .fillMaxSize()
            .hudBackground()
            .padding(top = insets.calculateTopPadding(), bottom = insets.calculateBottomPadding()),
    ) {
        val alarm = draft
        if (alarm == null) {
            Text("LOADING...", style = HudType.label, color = Hud.TextDim, modifier = Modifier.align(Alignment.Center))
            return@Box
        }
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                (fadeIn(tween(180)) + slideInHorizontally(tween(220)) { it / 12 }) togetherWith fadeOut(tween(120))
            },
            label = "editPage",
        ) { p ->
            when (p) {
                EditPage.MAIN -> EditMain(
                    alarm = alarm,
                    isNew = vm.isNew,
                    onChange = vm::update,
                    onOpen = { page = it },
                    onTest = {
                        testSpecs = alarm.challenges
                        testReturn = EditPage.MAIN
                        page = EditPage.TEST
                    },
                    onSave = { vm.save(onDone) },
                    onDelete = { vm.delete(onDone) },
                    onBack = onDone,
                )
                EditPage.CHALLENGES -> ChallengePickerPage(
                    selected = alarm.challenges,
                    onChange = { list -> vm.update { it.copy(challenges = list) } },
                    onPreview = { specs ->
                        testSpecs = specs
                        testReturn = EditPage.CHALLENGES
                        page = EditPage.TEST
                    },
                    onBack = { page = EditPage.MAIN },
                )
                EditPage.APPS -> AppLockPickerPage(
                    alarm = alarm,
                    vm = vm,
                    onPermissions = onPermissions,
                    onBack = { page = EditPage.MAIN },
                )
                EditPage.SOUND -> SoundPickerPage(
                    alarm = alarm,
                    vm = vm,
                    onBack = { page = EditPage.MAIN },
                )
                EditPage.TEST -> TestRunPage(
                    specs = testSpecs ?: alarm.challenges,
                    onExit = { page = testReturn },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditMain(
    alarm: Alarm,
    isNew: Boolean,
    onChange: ((Alarm) -> Alarm) -> Unit,
    onOpen: (EditPage) -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val now by rememberNow()
    var confirmDelete by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        ScreenHeader(if (isNew) "NEW_ALARM" else "EDIT_ALARM", onBack) {
            CyberButton("SAVE", onSave, filled = true)
        }

        Spacer(Modifier.height(12.dp))
        Column(
            Modifier.fillMaxWidth().cornerBrackets(Hud.Yellow, 14.dp).padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TimeDigit(alarm.hour, 24) { h -> onChange { it.copy(hour = h) } }
                Text(":", style = HudType.cardTime.copy(fontSize = 64.sp), color = Hud.Yellow, modifier = Modifier.padding(horizontal = 8.dp))
                TimeDigit(alarm.minute, 60) { m -> onChange { it.copy(minute = m) } }
            }
            Text(
                "ENGAGES IN " + NextTrigger.countdown(NextTrigger.compute(alarm, now), now),
                style = HudType.small,
                color = Hud.Cyan,
            )
        }

        SectionLabel("DESIGNATION")
        LabelField(alarm.label) { v -> onChange { it.copy(label = v) } }

        SectionLabel("REPEAT")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DayOfWeek.entries.forEach { day ->
                HexChip(
                    day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase(Locale.ENGLISH),
                    filled = alarm.repeatsOn(day),
                    onClick = { onChange { it.withDayToggled(day) } },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HexChip("WEEKDAYS", color = Hud.Cyan, onClick = { onChange { it.copy(repeatDays = Alarm.WEEKDAYS) } })
            HexChip("DAILY", color = Hud.Cyan, onClick = { onChange { it.copy(repeatDays = Alarm.EVERY_DAY) } })
            HexChip("ONCE", color = Hud.Cyan, onClick = { onChange { it.copy(repeatDays = 0) } })
        }

        SectionLabel("AUDIO // HAPTICS")
        SettingRow("SOUND", alarm.soundTitle, onClick = { onOpen(EditPage.SOUND) })
        SettingRow("VIBRATION", if (alarm.vibrate) "ENABLED" else "DISABLED", valueColor = if (alarm.vibrate) Hud.Yellow else Hud.Grey) {
            HexToggle(alarm.vibrate, { v -> onChange { it.copy(vibrate = v) } })
        }

        SectionLabel("SNOOZE")
        SettingRow("SNOOZE", if (alarm.snoozeEnabled) "ALLOWED" else "DISABLED // NO MERCY", valueColor = if (alarm.snoozeEnabled) Hud.Yellow else Hud.Red) {
            HexToggle(alarm.snoozeEnabled, { v -> onChange { it.copy(snoozeEnabled = v) } })
        }
        if (alarm.snoozeEnabled) {
            HudStepper(
                "INTERVAL", "${alarm.snoozeMinutes} MIN",
                onMinus = { onChange { it.copy(snoozeMinutes = (it.snoozeMinutes - 1).coerceAtLeast(1)) } },
                onPlus = { onChange { it.copy(snoozeMinutes = (it.snoozeMinutes + 1).coerceAtMost(30)) } },
            )
            Spacer(Modifier.height(8.dp))
            HudStepper(
                "MAX SNOOZES", "x${alarm.maxSnoozes}",
                onMinus = { onChange { it.copy(maxSnoozes = (it.maxSnoozes - 1).coerceAtLeast(1)) } },
                onPlus = { onChange { it.copy(maxSnoozes = (it.maxSnoozes + 1).coerceAtMost(10)) } },
            )
        }

        SectionLabel("DISMISS PROTOCOL", color = Hud.Yellow)
        SettingRow(
            "CHALLENGE CHAIN",
            if (alarm.challenges.isEmpty()) "NONE // HOLD TO DISMISS" else alarm.challenges.joinToString("  >  ") { it.summary() },
            onClick = { onOpen(EditPage.CHALLENGES) },
        )

        SectionLabel("STAY_AWAKE // APP LOCK", color = Hud.Red)
        val enforcerOk = remember { Permissions.isAccessibilityEnabled(context) || Permissions.hasUsageAccess(context) }
        SettingRow(
            "LOCKED AFTER DISMISS",
            if (alarm.lockedPackages.isEmpty()) "OFF" else "${alarm.lockedPackages.size} APPS // ${formatMinutes(alarm.lockMinutes)}",
            valueColor = if (alarm.lockedPackages.isEmpty()) Hud.Grey else Hud.Yellow,
            onClick = { onOpen(EditPage.APPS) },
        )
        if (alarm.lockedPackages.isNotEmpty() && !enforcerOk) {
            Text("! ENFORCER OFFLINE: grant Accessibility or Usage access", style = HudType.small, color = Hud.Red)
        }

        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CyberButton("TEST DISMISSAL", onTest, color = Hud.Cyan, modifier = Modifier.weight(1f))
            if (!isNew) {
                CyberButton(
                    if (confirmDelete) "CONFIRM?" else "DELETE",
                    { if (confirmDelete) onDelete() else confirmDelete = true },
                    color = Hud.Red,
                    filled = confirmDelete,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

/** Two-digit spinner: tap +/- (hold to repeat) or drag the digits vertically. Wraps around. */
@Composable
private fun TimeDigit(value: Int, modulo: Int, onValue: (Int) -> Unit) {
    val current by rememberUpdatedState(value)
    val emit by rememberUpdatedState(onValue)
    var dragAcc by remember { mutableFloatStateOf(0f) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RepeatButton("+", { onValue((value + 1).mod(modulo)) })
        Text(
            "%02d".format(value),
            style = HudType.cardTime.copy(fontSize = 64.sp, shadow = Shadow(Hud.Yellow.copy(alpha = 0.6f), blurRadius = 26f)),
            color = Hud.Yellow,
            modifier = Modifier
                .padding(vertical = 4.dp)
                .pointerInput(Unit) {
                    val stepPx = 22.dp.toPx()
                    detectVerticalDragGestures(onDragEnd = { dragAcc = 0f }) { _, dy ->
                        dragAcc += dy
                        while (dragAcc <= -stepPx) { emit((current + 1).mod(modulo)); dragAcc += stepPx }
                        while (dragAcc >= stepPx) { emit((current - 1).mod(modulo)); dragAcc -= stepPx }
                    }
                },
        )
        RepeatButton("-", { onValue((value - 1).mod(modulo)) })
    }
}

@Composable
private fun LabelField(value: String, onValue: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = { raw ->
            onValue(
                raw.uppercase(Locale.ENGLISH).replace(' ', '_')
                    .filter { it.isLetterOrDigit() || it == '_' || it == '-' || it == '.' }
                    .take(20),
            )
        },
        singleLine = true,
        textStyle = HudType.label.copy(color = Hud.Yellow),
        cursorBrush = SolidColor(Hud.Yellow),
        modifier = Modifier
            .fillMaxWidth()
            .drawWithCache {
                val path = chamferPath(size, 10.dp.toPx(), 0f, 10.dp.toPx(), 0f)
                onDrawBehind {
                    drawPath(path, Hud.Yellow.copy(alpha = 0.05f))
                    neonStroke(path, Hud.Yellow.copy(alpha = 0.8f), 1.2.dp.toPx(), glow = false)
                }
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        decorationBox = { inner ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("> ", style = HudType.label, color = Hud.Cyan)
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text("WAKE_UP", style = HudType.label, color = Hud.GreyDim)
                    inner()
                }
                Spacer(Modifier.width(8.dp))
                Text("${value.length}/20", style = HudType.small, color = Hud.TextDim)
            }
        },
    )
}

fun formatMinutes(minutes: Int): String = "%dH%02dM".format(minutes / 60, minutes % 60)
