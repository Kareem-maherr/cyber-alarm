package com.cyberalarm.ui.config

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cyberalarm.cred.CrewTheme
import com.cyberalarm.feedback.SoundPack
import com.cyberalarm.feedback.SoundSlot
import com.cyberalarm.feedback.rememberCyberFeedback
import com.cyberalarm.settings.TagKind
import com.cyberalarm.ui.components.CodeScanner
import com.cyberalarm.ui.components.CyberButton
import com.cyberalarm.ui.components.CyberCard
import com.cyberalarm.ui.components.HexToggle
import com.cyberalarm.ui.components.HudStepper
import com.cyberalarm.ui.components.NfcReaderEffect
import com.cyberalarm.ui.components.NightCityScreen
import com.cyberalarm.ui.components.OutlineChip
import com.cyberalarm.ui.components.ScreenHeader
import com.cyberalarm.ui.components.SectionLabel
import com.cyberalarm.ui.components.SettingRow
import com.cyberalarm.ui.components.hasCamera
import com.cyberalarm.ui.components.hasNfc
import com.cyberalarm.ui.components.nfcEnabled
import com.cyberalarm.ui.theme.Hud
import com.cyberalarm.ui.theme.HudMotion
import com.cyberalarm.ui.theme.HudType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ConfigScreen(onBack: () -> Unit, onPermissions: () -> Unit, vm: ConfigViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val rank by vm.rank.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val watches by vm.watches.collectAsStateWithLifecycle()
    val feedback = rememberCyberFeedback()
    var registering by remember { mutableStateOf<TagKind?>(null) }
    var cameraOk by remember { mutableStateOf(hasCamera(context)) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        cameraOk = ok
        if (ok) registering = TagKind.CODE
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::export)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::import)
    }
    var pickingSlot by remember { mutableStateOf<SoundSlot?>(null) }
    val soundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val slot = pickingSlot
        if (uri != null && slot != null) vm.setCustomSound(slot, uri)
        pickingSlot = null
    }
    LaunchedEffect(Unit) { vm.refreshWatches() }
    val insets = WindowInsets.safeDrawing.asPaddingValues()

    NightCityScreen {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = insets.calculateTopPadding() + 12.dp, bottom = insets.calculateBottomPadding() + 48.dp),
        ) {
            ScreenHeader("CONFIG", onBack)
            message?.let {
                Text(it, style = HudType.small, color = Hud.Yellow, modifier = Modifier.padding(top = 8.dp))
                LaunchedEffect(it) {
                    kotlinx.coroutines.delay(4_000)
                    vm.clearMessage()
                }
            }

            // ---------------------------------------------------------------- theme
            SectionLabel("CREW THEME // UNLOCKED WITH STREET CRED", bottom = 8.dp)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CrewTheme.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { t ->
                            val unlocked = rank >= t.rank
                            ThemeChip(t, selected = settings.theme == t.name, unlocked = unlocked, modifier = Modifier.weight(1f)) {
                                if (unlocked) vm.update { it.copy(theme = t.name) }
                            }
                        }
                    }
                }
            }

            // ---------------------------------------------------------------- bedtime
            SectionLabel("BEDTIME", bottom = 4.dp)
            SettingRow(
                "BEDTIME REMINDER",
                if (settings.bedtimeEnabled) "ON // ${formatGoal(settings.sleepMinutes)} BEFORE NEXT ALARM" else "OFF",
                valueColor = if (settings.bedtimeEnabled) Hud.Yellow else Hud.Grey,
            ) {
                HexToggle(settings.bedtimeEnabled, { v -> vm.update { it.copy(bedtimeEnabled = v) } })
            }
            AnimatedVisibility(
                settings.bedtimeEnabled,
                enter = expandVertically(HudMotion.glide(600)) + fadeIn(HudMotion.fade(500)),
                exit = shrinkVertically(HudMotion.glide(600)) + fadeOut(HudMotion.fade(500)),
            ) {
                Column {
                    HudStepper(
                        "SLEEP GOAL", formatGoal(settings.sleepMinutes),
                        onMinus = { vm.update { it.copy(sleepMinutes = (it.sleepMinutes - 30).coerceAtLeast(5 * 60)) } },
                        onPlus = { vm.update { it.copy(sleepMinutes = (it.sleepMinutes + 30).coerceAtMost(11 * 60)) } },
                    )
                    SettingRow(
                        "NIGHT LOCK",
                        if (settings.nightLock) "LOCKS THE NEXT ALARM'S APPS FROM BEDTIME" else "OFF",
                        valueColor = if (settings.nightLock) Hud.Yellow else Hud.Grey,
                        divider = false,
                    ) {
                        HexToggle(settings.nightLock, { v -> vm.update { it.copy(nightLock = v) } })
                    }
                }
            }

            // ---------------------------------------------------------------- holiday
            SectionLabel("HOLIDAY MODE", bottom = 4.dp)
            val holiday = settings.holidayActive()
            SettingRow(
                "PAUSE REPEATING ALARMS",
                if (holiday) "UNTIL ${endLabel(vm.holidayDays(settings))}" else "OFF // ONE-TIME ALARMS STILL RING",
                valueColor = if (holiday) Hud.Cyan else Hud.Grey,
                divider = holiday,
            ) {
                HexToggle(holiday, { v -> vm.setHoliday(if (v) 7 else null) })
            }
            if (holiday) {
                val days = vm.holidayDays(settings)
                HudStepper(
                    "DAYS OFF", "$days D",
                    onMinus = { vm.setHoliday((days - 1).coerceAtLeast(1)) },
                    onPlus = { vm.setHoliday((days + 1).coerceAtMost(60)) },
                )
            }

            // ---------------------------------------------------------------- voice
            SectionLabel("VOICE", bottom = 4.dp)
            SettingRow(
                "FIXER VOICE LINES",
                if (settings.voiceLines) "SPOKEN AFTER YOU DISMISS" else "OFF",
                valueColor = if (settings.voiceLines) Hud.Yellow else Hud.Grey,
            ) {
                HexToggle(settings.voiceLines, { v -> vm.update { it.copy(voiceLines = v) } })
            }
            OutlineChip("TEST VOICE", false, vm::testVoice)

            // ---------------------------------------------------------------- ui sounds
            SectionLabel("UI SOUNDS // KEYPAD & ANSWERS", bottom = 8.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoundPack.entries.forEach { pack ->
                    OutlineChip(pack.title, settings.soundPack == pack.name, { vm.setSoundPack(pack) })
                }
            }
            SoundSlot.entries.forEach { slot ->
                val custom = settings.customSound(slot).isNotEmpty()
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(slot.title, style = HudType.micro, color = Hud.TextDim)
                        Text(
                            if (custom) "YOUR FILE" else "${SoundPack.of(settings.soundPack).title} PACK",
                            style = HudType.value,
                            color = if (custom) Hud.Yellow else Hud.Text,
                        )
                    }
                    OutlineChip("▶", false, { feedback.preview(slot) })
                    if (custom) {
                        OutlineChip("RESET", false, { vm.clearCustomSound(slot) }, color = Hud.Red)
                    } else {
                        OutlineChip("FILE", false, {
                            pickingSlot = slot
                            soundLauncher.launch(arrayOf("audio/*"))
                        })
                    }
                }
            }
            Text(
                "Use any short clip from your phone (under 1 MB). It's copied into the app and stays on this phone. " +
                    "GLITCH and TERMINAL packs: Kenney.nl, CC0.",
                style = HudType.small,
                color = Hud.TextDim,
                modifier = Modifier.padding(top = 8.dp),
            )

            // ---------------------------------------------------------------- jack-in
            SectionLabel("JACK-IN POINTS", bottom = 4.dp)
            Text(
                "Stick a QR code somewhere away from the bed (kitchen, bathroom), or use any barcode or NFC tag. " +
                    "The JACK_IN challenge needs one of these scanned to dismiss.",
                style = HudType.small,
                color = Hud.TextDim,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            tags.forEach { tag ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tag.name, style = HudType.value, color = Hud.Yellow)
                        Text(if (tag.kind == TagKind.NFC) "NFC TAG" else "QR / BARCODE", style = HudType.micro, color = Hud.TextDim)
                    }
                    OutlineChip("DROP", false, { vm.remove(tag) }, color = Hud.Red)
                }
            }
            if (registering == null) {
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CyberButton(
                        "+ QR / BARCODE",
                        { if (cameraOk) registering = TagKind.CODE else cameraLauncher.launch(Manifest.permission.CAMERA) },
                        color = Hud.Cyan,
                        modifier = Modifier.weight(1f),
                    )
                    if (hasNfc(context)) {
                        CyberButton("+ NFC TAG", { registering = TagKind.NFC }, color = Hud.Cyan, modifier = Modifier.weight(1f))
                    }
                }
            } else {
                CyberCard(Modifier.fillMaxWidth().padding(top = 6.dp), accent = Hud.Cyan, cut = 18.dp, contentPadding = PaddingValues(14.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val done = { kind: TagKind, raw: String ->
                            if (vm.register(kind, raw)) feedback.success() else feedback.error()
                            registering = null
                        }
                        if (registering == TagKind.CODE) {
                            Text("AIM AT THE CODE YOU WANT TO REGISTER", style = HudType.micro, color = Hud.Cyan)
                            Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clipToBounds().border(1.dp, Hud.Cyan.copy(alpha = 0.5f))) {
                                CodeScanner(Modifier.fillMaxSize()) { done(TagKind.CODE, it) }
                            }
                        } else {
                            NfcReaderEffect { done(TagKind.NFC, it) }
                            Text(
                                if (nfcEnabled(context)) "HOLD THE NFC TAG TO THE BACK OF THE PHONE" else "NFC IS OFF // TURN IT ON IN QUICK SETTINGS",
                                style = HudType.small,
                                color = if (nfcEnabled(context)) Hud.Cyan else Hud.Red,
                            )
                        }
                        CyberButton("CANCEL", { registering = null }, color = Hud.Red, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            // ---------------------------------------------------------------- watch
            SectionLabel("WEAR OS", bottom = 4.dp)
            SettingRow(
                "WATCH LINK",
                when {
                    !settings.watchLink -> "OFF"
                    watches == 0 -> "ON // NO WATCH CONNECTED"
                    else -> "ON // $watches WATCH${if (watches > 1) "ES" else ""} CONNECTED"
                },
                valueColor = if (settings.watchLink && watches > 0) Hud.Yellow else Hud.Grey,
            ) {
                HexToggle(settings.watchLink, { v -> vm.update { it.copy(watchLink = v) } })
            }
            Text(
                "The watch buzzes and shows the awake-check prompt. It can't dismiss anything, by design.",
                style = HudType.small,
                color = Hud.TextDim,
                modifier = Modifier.padding(top = 6.dp),
            )

            // ---------------------------------------------------------------- backup
            SectionLabel("BACKUP", bottom = 8.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CyberButton(
                    "EXPORT",
                    { exportLauncher.launch("cyber-alarm-${LocalDate.now()}.json") },
                    color = Hud.Cyan,
                    modifier = Modifier.weight(1f),
                )
                CyberButton("IMPORT", { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }, color = Hud.Cyan, modifier = Modifier.weight(1f))
            }
            Text(
                "Alarms, settings, jack-in points, wake history and street cred. Import replaces what's here.",
                style = HudType.small,
                color = Hud.TextDim,
                modifier = Modifier.padding(top = 6.dp),
            )

            SectionLabel("SYSTEM", bottom = 4.dp)
            SettingRow("PERMISSIONS", "DASHBOARD", onClick = onPermissions, divider = false)
        }
    }
}

@Composable
private fun ThemeChip(theme: CrewTheme, selected: Boolean, unlocked: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color = androidx.compose.ui.graphics.Color(theme.primary)
    CyberCard(modifier, active = selected, accent = if (unlocked) color else Hud.GreyDim, cut = 12.dp, onClick = onClick, contentPadding = PaddingValues(12.dp)) {
        Column {
            Text(theme.title, style = HudType.value, color = if (unlocked) color else Hud.Off)
            Text(if (unlocked) (if (selected) "ACTIVE" else "UNLOCKED") else "REACH ${theme.rank.title}", style = HudType.micro, color = Hud.TextDim)
        }
    }
}

private fun formatGoal(minutes: Int) = if (minutes % 60 == 0) "${minutes / 60}H" else "${minutes / 60}H${minutes % 60}M"

private fun endLabel(days: Int): String =
    DateTimeFormatter.ofPattern("EEE dd MMM", Locale.ENGLISH).format(LocalDate.now().plusDays(days.toLong())).uppercase(Locale.ENGLISH)
