package com.cyberalarm.ui.edit

import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cyberalarm.alarm.AlarmPlayer
import com.cyberalarm.challenge.ChallengeRunner
import com.cyberalarm.challenge.ChallengeSpec
import com.cyberalarm.challenge.ChallengeType
import com.cyberalarm.challenge.Connect4Ai
import com.cyberalarm.challenge.Difficulty
import com.cyberalarm.challenge.MemoryRules
import com.cyberalarm.challenge.MixMatchGenerator
import com.cyberalarm.challenge.StepsChallenge
import com.cyberalarm.data.Alarm
import com.cyberalarm.data.BuiltinTone
import com.cyberalarm.ui.components.CyberButton
import com.cyberalarm.ui.components.CyberCard
import com.cyberalarm.ui.components.HexChip
import com.cyberalarm.ui.components.HexToggle
import com.cyberalarm.ui.components.HudStepper
import com.cyberalarm.ui.components.ScreenHeader
import com.cyberalarm.ui.components.SectionLabel
import com.cyberalarm.ui.components.hexagonPath
import com.cyberalarm.ui.permissions.Permissions
import com.cyberalarm.ui.theme.Hud
import com.cyberalarm.ui.theme.HudType

// ---------------------------------------------------------------- challenge picker

@Composable
fun ChallengePickerPage(
    selected: List<ChallengeSpec>,
    onChange: (List<ChallengeSpec>) -> Unit,
    onPreview: (List<ChallengeSpec>) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader("DISMISS_PROTOCOL", onBack)
        Text(
            "Enabled challenges run as a chain, in the order you switched them on (#1, #2...). The alarm stops only when every link is solved.",
            style = HudType.body,
            color = Hud.TextDim,
        )
        ChallengeType.entries.forEach { type ->
            val index = selected.indexOfFirst { it.type == type }
            val spec = selected.getOrNull(index)
            fun replace(new: ChallengeSpec) = onChange(selected.map { if (it.type == type) new else it })
            CyberCard(Modifier.fillMaxWidth(), active = spec != null) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(type.title, style = HudType.label.copy(fontSize = HudType.body.fontSize), color = if (spec != null) Hud.Yellow else Hud.Grey)
                                if (spec != null) {
                                    Spacer(Modifier.width(8.dp))
                                    HexChip("#${index + 1}", filled = true)
                                }
                            }
                            Text(type.blurb, style = HudType.small, color = Hud.TextDim)
                        }
                        HexToggle(spec != null, { on ->
                            onChange(if (on) selected + ChallengeSpec(type) else selected.filterNot { it.type == type })
                        })
                    }
                    if (spec != null) {
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Difficulty.entries.forEach { d ->
                                HexChip(d.short, filled = spec.difficulty == d, onClick = { replace(spec.copy(difficulty = d)) })
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        HudStepper(
                            countLabel(type), "x${spec.count}",
                            onMinus = { replace(spec.copy(count = (spec.count - 1).coerceAtLeast(1))) },
                            onPlus = { replace(spec.copy(count = (spec.count + 1).coerceAtMost(ChallengeSpec.MAX_COUNT))) },
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(detail(spec), style = HudType.small, color = Hud.Cyan)
                        Spacer(Modifier.height(10.dp))
                        CyberButton("TEST", { onPreview(listOf(spec)) }, color = Hud.Cyan)
                    }
                }
            }
        }
        if (selected.size > 1) CyberButton("TEST FULL CHAIN", { onPreview(selected) }, color = Hud.Yellow, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(32.dp))
    }
}

private fun countLabel(type: ChallengeType) = when (type) {
    ChallengeType.MATH -> "CORRECT IN A ROW"
    ChallengeType.CONNECT4 -> "WINS REQUIRED"
    ChallengeType.STEPS -> "STEP MULTIPLIER"
    else -> "ROUNDS"
}

private fun detail(spec: ChallengeSpec): String = when (spec.type) {
    ChallengeType.MATH -> when (spec.difficulty) {
        Difficulty.EASY -> "2-DIGIT ADD / SUBTRACT"
        Difficulty.MEDIUM -> "A × B ± C"
        Difficulty.HARD -> "LINEAR EQUATIONS / A×B − C×D"
    }
    ChallengeType.MIX_MATCH -> "${MixMatchGenerator.pairsFor(spec.difficulty)} PAIRS" +
        if (spec.difficulty == Difficulty.HARD) " // STROOP INK" else ""
    ChallengeType.CONNECT4 -> "MINIMAX DEPTH ${Connect4Ai.settingsFor(spec.difficulty).depth}"
    ChallengeType.MEMORY -> "${MemoryRules.length(spec.difficulty)}-STEP SEQUENCE ON ${MemoryRules.gridSize(spec.difficulty)}x${MemoryRules.gridSize(spec.difficulty)}"
    ChallengeType.STEPS -> "${StepsChallenge.target(spec)} STEPS TOTAL"
}

// ---------------------------------------------------------------- test run

@Composable
fun TestRunPage(specs: List<ChallengeSpec>, onExit: () -> Unit) {
    var passed by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp)) {
        ScreenHeader("TEST_RUN", onExit)
        Text("DRY RUN // NO SOUND, NOTHING IS DISMISSED", style = HudType.small, color = Hud.TextDim)
        Spacer(Modifier.height(14.dp))
        if (passed) {
            Text("ALL CHALLENGES PASSED", style = HudType.title, color = Hud.Yellow)
            Spacer(Modifier.height(16.dp))
            CyberButton("RETURN", onExit, filled = true, modifier = Modifier.fillMaxWidth())
        } else {
            ChallengeRunner(specs, onAllPassed = { passed = true })
        }
        Spacer(Modifier.height(32.dp))
    }
}

// ---------------------------------------------------------------- app lock picker

@Composable
fun AppLockPickerPage(alarm: Alarm, vm: EditAlarmViewModel, onPermissions: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val apps by vm.apps.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.loadApps() }
    val enforcerOk = remember { Permissions.isAccessibilityEnabled(context) || Permissions.hasUsageAccess(context) }
    val filtered = apps.orEmpty().filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
        .sortedByDescending { it.packageName in alarm.lockedPackages }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { ScreenHeader("STAY_AWAKE", onBack) }
        item {
            Text(
                "After you dismiss this alarm, the apps below stay locked for the chosen time. Opening one shows a block screen; " +
                    "solving a challenge there grants a 10-minute pass for that app.",
                style = HudType.body,
                color = Hud.TextDim,
            )
        }
        item {
            Spacer(Modifier.height(8.dp))
            HudStepper(
                "LOCK DURATION", formatMinutes(alarm.lockMinutes),
                onMinus = { vm.update { it.copy(lockMinutes = (it.lockMinutes - 15).coerceAtLeast(15)) } },
                onPlus = { vm.update { it.copy(lockMinutes = (it.lockMinutes + 15).coerceAtMost(480)) } },
            )
        }
        if (!enforcerOk) {
            item {
                CyberCard(Modifier.fillMaxWidth(), accent = Hud.Red, stripes = false, onClick = onPermissions) {
                    Text(
                        "ENFORCER OFFLINE\nApp lock needs the Accessibility enforcer or Usage access. Tap to open SYS.PERMISSIONS.",
                        style = HudType.small,
                        color = Hud.Red,
                    )
                }
            }
        }
        item {
            SectionLabel("TARGETS // ${alarm.lockedPackages.size} SELECTED")
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = HudType.body.copy(color = Hud.Yellow),
                cursorBrush = SolidColor(Hud.Yellow),
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawLine(Hud.Yellow.copy(alpha = 0.6f), androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height), 1.dp.toPx())
                    }
                    .padding(vertical = 10.dp),
                decorationBox = { inner ->
                    Box { if (query.isEmpty()) Text("> SEARCH APPS", style = HudType.body, color = Hud.GreyDim); inner() }
                },
            )
        }
        if (apps == null) {
            item { Text("SCANNING PACKAGES...", style = HudType.small, color = Hud.Cyan) }
        }
        items(filtered, key = { it.packageName }) { app ->
            val checked = app.packageName in alarm.lockedPackages
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { vm.update { a -> a.copy(lockedPackages = if (checked) a.lockedPackages - app.packageName else a.lockedPackages + app.packageName) } }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (app.icon != null) Image(app.icon, null, Modifier.size(36.dp)) else Spacer(Modifier.size(36.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.label, style = HudType.body, color = if (checked) Hud.Yellow else Hud.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(app.packageName, style = HudType.small, color = Hud.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                HexToggle(checked, { on ->
                    vm.update { a -> a.copy(lockedPackages = if (on) a.lockedPackages + app.packageName else a.lockedPackages - app.packageName) }
                }, width = 52.dp, height = 26.dp)
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

// ---------------------------------------------------------------- sound picker

@Composable
fun SoundPickerPage(alarm: Alarm, vm: EditAlarmViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val audioPermission = if (Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_AUDIO else android.Manifest.permission.READ_EXTERNAL_STORAGE
    var hasLibrary by remember { mutableStateOf(Permissions.granted(context, audioPermission)) }
    LaunchedEffect(hasLibrary) { if (hasLibrary) vm.loadTracks() }

    // Preview uses the MEDIA stream at normal volume, not the alarm path.
    var previewing by remember { mutableStateOf<String?>(null) }
    val player = remember { mutableStateOf<MediaPlayer?>(null) }
    fun stopPreview() {
        player.value?.let { runCatching { it.stop() }; it.release() }
        player.value = null
        previewing = null
    }
    fun togglePreview(key: String) {
        val same = previewing == key
        stopPreview()
        if (same) return
        val uri = AlarmPlayer.resolve(context, key) ?: return
        player.value = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
                setDataSource(context, uri)
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()
        if (player.value != null) previewing = key
    }
    DisposableEffect(Unit) { onDispose { stopPreview() } }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasLibrary = it }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            // Persist read access so the alarm can still open the file after a reboot.
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "CUSTOM_TRACK"
            vm.update { it.copy(soundUri = uri.toString(), soundTitle = name.substringBeforeLast('.')) }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { ScreenHeader("AUDIO_SOURCE", onBack) }
        item {
            Text("ACTIVE: ${alarm.soundTitle}", style = HudType.small, color = Hud.Cyan)
            Text("Plays on the alarm stream, looping, ramping up over 30s. Falls back to NEON_PULSE if the track is gone.", style = HudType.small, color = Hud.TextDim)
        }
        item { SectionLabel("BUILT-IN // SYNTHWAVE") }
        items(BuiltinTone.entries, key = { it.key }) { tone ->
            SoundRow(
                title = tone.title, subtitle = "CYBER-ALARM ORIGINAL",
                selected = alarm.soundUri == tone.key, playing = previewing == tone.key,
                onSelect = { vm.update { it.copy(soundUri = tone.key, soundTitle = tone.title) } },
                onPreview = { togglePreview(tone.key) },
            )
        }
        item {
            SectionLabel("MUSIC LIBRARY")
            CyberButton("BROWSE FILES", { fileLauncher.launch(arrayOf("audio/*")) }, color = Hud.Cyan, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            if (!hasLibrary) {
                CyberButton("GRANT LIBRARY ACCESS", { permissionLauncher.launch(audioPermission) }, modifier = Modifier.fillMaxWidth())
            } else if (tracks == null) {
                Text("INDEXING TRACKS...", style = HudType.small, color = Hud.Cyan)
            } else if (tracks!!.isEmpty()) {
                Text("NO TRACKS FOUND", style = HudType.small, color = Hud.TextDim)
            }
        }
        items(tracks.orEmpty(), key = { it.uri }) { track ->
            SoundRow(
                title = track.title, subtitle = track.artist,
                selected = alarm.soundUri == track.uri, playing = previewing == track.uri,
                onSelect = { vm.update { it.copy(soundUri = track.uri, soundTitle = track.title) } },
                onPreview = { togglePreview(track.uri) },
            )
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun SoundRow(title: String, subtitle: String, selected: Boolean, playing: Boolean, onSelect: () -> Unit, onPreview: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(18.dp).drawBehind {
                val path = hexagonPath(size)
                if (selected) drawPath(path, Hud.Yellow) else drawPath(path, Hud.Grey, style = Stroke(1.5.dp.toPx()))
            },
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = HudType.body, color = if (selected) Hud.Yellow else Hud.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, style = HudType.small, color = Hud.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            if (playing) "[■]" else "[▶]",
            style = HudType.label,
            color = if (playing) Hud.Red else Hud.Cyan,
            modifier = Modifier.clickable(onClick = onPreview).padding(8.dp),
        )
    }
}
