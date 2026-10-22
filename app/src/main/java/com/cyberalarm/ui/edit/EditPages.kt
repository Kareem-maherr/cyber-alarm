package com.cyberalarm.ui.edit

import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import com.cyberalarm.ui.components.hapticClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cyberalarm.alarm.AlarmPlayer
import com.cyberalarm.challenge.BreachRules
import com.cyberalarm.challenge.ChallengeRunner
import com.cyberalarm.challenge.ChallengeSpec
import com.cyberalarm.challenge.ChallengeType
import com.cyberalarm.challenge.Connect4Ai
import com.cyberalarm.challenge.Difficulty
import com.cyberalarm.challenge.MemoryRules
import com.cyberalarm.challenge.MixMatchGenerator
import com.cyberalarm.challenge.StepsChallenge
import com.cyberalarm.cred.Rank
import com.cyberalarm.data.Alarm
import com.cyberalarm.data.BuiltinTone
import com.cyberalarm.ui.components.CyberButton
import com.cyberalarm.ui.components.CyberCard
import com.cyberalarm.ui.components.EqBars
import com.cyberalarm.ui.components.HexToggle
import com.cyberalarm.ui.components.HudStepper
import com.cyberalarm.ui.components.IndexBadge
import com.cyberalarm.ui.components.ScreenHeader
import com.cyberalarm.ui.components.SectionLabel
import com.cyberalarm.ui.components.SegmentedControl
import com.cyberalarm.ui.components.displayStyle
import com.cyberalarm.ui.components.hexagonPath
import com.cyberalarm.ui.permissions.Permissions
import com.cyberalarm.ui.theme.Hud
import com.cyberalarm.ui.theme.HudMotion
import com.cyberalarm.ui.theme.HudType

private val PagePadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 48.dp)

// ---------------------------------------------------------------- challenge picker (1b)

@Composable
fun ChallengePickerPage(
    selected: List<ChallengeSpec>,
    rank: Rank,
    onChange: (List<ChallengeSpec>) -> Unit,
    onPreview: (List<ChallengeSpec>) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PagePadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader("DISMISS_PROTOCOL", onBack)
        Text(
            "Enabled challenges run as a chain, in the order you switched them on (#1, #2...). The alarm stops only when every link is solved.",
            style = HudType.body,
            color = Hud.TextDim,
        )
        if (selected.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                selected.forEachIndexed { i, spec ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.fillMaxWidth().height(2.dp).drawBehind { drawRect(Hud.Yellow) })
                        Text("#${i + 1} ${spec.type.title}", style = HudType.micro.copy(fontSize = 9.sp, letterSpacing = 1.sp), color = Hud.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        ChallengeType.entries.forEach { type ->
            val index = selected.indexOfFirst { it.type == type }
            val spec = selected.getOrNull(index)
            val on = spec != null
            val shown = spec ?: ChallengeSpec(type)
            fun replace(new: ChallengeSpec) = onChange(selected.map { if (it.type == type) new else it })
            val titleColor by animateColorAsState(if (on) Hud.Yellow else Hud.TextDim, HudMotion.fade(600), label = "chTitle")
            CyberCard(
                Modifier.fillMaxWidth(),
                active = on,
                cut = 22.dp,
                contentPadding = PaddingValues(start = 20.dp, end = 18.dp, top = 14.dp, bottom = 14.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(type.title, style = displayStyle(16), color = titleColor)
                                AnimatedVisibility(on, enter = fadeIn(HudMotion.fade(400)), exit = fadeOut(HudMotion.fade(400))) {
                                    IndexBadge("#${index + 1}")
                                }
                            }
                            Text(type.blurb, style = HudType.small, color = Hud.TextDim)
                        }
                        HexToggle(on, { enable ->
                            onChange(if (enable) selected + ChallengeSpec(type) else selected.filterNot { it.type == type })
                        })
                    }
                    AnimatedVisibility(
                        visible = on,
                        enter = expandVertically(HudMotion.glide(600)) + fadeIn(HudMotion.fade(500)),
                        exit = shrinkVertically(HudMotion.glide(600)) + fadeOut(HudMotion.fade(500)),
                    ) {
                        Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (type != ChallengeType.JACK_IN) {
                                // LEGEND is Breach-only and needs MERC street cred.
                                val levels = Difficulty.entries.filter {
                                    it != Difficulty.LEGEND || (type == ChallengeType.BREACH && rank >= Rank.MERC)
                                }
                                SegmentedControl(
                                    levels.map { it.short },
                                    levels.indexOf(shown.difficulty).coerceAtLeast(0),
                                    { i -> replace(shown.copy(difficulty = levels[i])) },
                                )
                                HudStepper(
                                    countLabel(type), "x${shown.count}",
                                    onMinus = { replace(shown.copy(count = (shown.count - 1).coerceAtLeast(1))) },
                                    onPlus = { replace(shown.copy(count = (shown.count + 1).coerceAtMost(ChallengeSpec.MAX_COUNT))) },
                                    valueWidth = 64.dp,
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(detail(shown), style = HudType.small, color = Hud.Cyan, modifier = Modifier.weight(1f))
                                Text(
                                    "TEST",
                                    style = HudType.small.copy(letterSpacing = 2.sp),
                                    color = Hud.Cyan,
                                    modifier = Modifier
                                        .border(1.dp, Hud.Cyan.copy(alpha = 0.4f))
                                        .hapticClickable { onPreview(listOf(shown)) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(selected.size > 1, enter = fadeIn(HudMotion.fade()), exit = fadeOut(HudMotion.fade())) {
            CyberButton("TEST FULL CHAIN", { onPreview(selected) }, filled = true, height = 48.dp, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }
    }
}

private fun countLabel(type: ChallengeType) = when (type) {
    ChallengeType.MATH -> "PROBLEMS TO SOLVE"
    ChallengeType.BREACH -> "NODES TO BREACH"
    ChallengeType.CONNECT4 -> "WINS REQUIRED"
    ChallengeType.STEPS -> "STEP MULTIPLIER"
    else -> "ROUNDS"
}

private fun detail(spec: ChallengeSpec): String = when (spec.type) {
    ChallengeType.MATH -> when (spec.difficulty) {
        Difficulty.EASY -> "TWO 2-DIGIT TERMS"
        Difficulty.MEDIUM -> "THREE 2-DIGIT TERMS"
        Difficulty.HARD, Difficulty.LEGEND -> "THREE 3-DIGIT OR FOUR 2-DIGIT TERMS"
    }
    ChallengeType.BREACH -> BreachRules.config(spec.difficulty).let {
        "${it.size}x${it.size} // ${it.targetLength}-CODE // BUFFER ${it.bufferSize}"
    }
    ChallengeType.MIX_MATCH -> "${MixMatchGenerator.pairsFor(spec.difficulty)} PAIRS" +
        if (spec.difficulty == Difficulty.HARD) " // STROOP INK" else ""
    ChallengeType.CONNECT4 -> "MINIMAX DEPTH ${Connect4Ai.settingsFor(spec.difficulty).depth}"
    ChallengeType.MEMORY -> "${MemoryRules.length(spec.difficulty)}-STEP SEQUENCE ON ${MemoryRules.gridSize(spec.difficulty)}x${MemoryRules.gridSize(spec.difficulty)}"
    ChallengeType.STEPS -> "${StepsChallenge.target(spec)} STEPS TOTAL"
    ChallengeType.JACK_IN -> "5 MIN QUIET WINDOW // REGISTER TAGS IN CONFIG"
}

// ---------------------------------------------------------------- test run

@Composable
fun TestRunPage(specs: List<ChallengeSpec>, onExit: () -> Unit) {
    var passed by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PagePadding)) {
        ScreenHeader("TEST_RUN", onExit)
        Text("DRY RUN // NO ALARM, NOTHING IS DISMISSED", style = HudType.micro, color = Hud.TextDim)
        Spacer(Modifier.height(18.dp))
        if (passed) {
            Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("ALL CHALLENGES PASSED", style = displayStyle(22, spacing = 3f), color = Hud.Yellow)
                Text("SIGNAL CLEARED", style = HudType.micro, color = Hud.TextDim)
                CyberButton("RETURN", onExit, color = Hud.Cyan, modifier = Modifier.padding(top = 10.dp))
            }
        } else {
            ChallengeRunner(specs, onAllPassed = { passed = true })
        }
    }
}

// ---------------------------------------------------------------- app lock picker (1d)

@Composable
fun AppLockPickerPage(alarm: Alarm, vm: EditAlarmViewModel, onPermissions: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val apps by vm.apps.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.loadApps() }
    val enforcerOk = remember { Permissions.isAccessibilityEnabled(context) || Permissions.hasUsageAccess(context) }
    val filtered = apps.orEmpty().filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
        .sortedByDescending { it.packageName in alarm.lockedPackages }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PagePadding) {
        item { ScreenHeader("STAY_AWAKE", onBack) }
        item {
            Text(
                "After you dismiss this alarm, the apps below stay locked for the chosen time. Opening one shows a block screen; " +
                    "solving a challenge there grants a 10-minute pass for that app.",
                style = HudType.body,
                color = Hud.TextDim,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
        item {
            HudStepper(
                "LOCK DURATION", formatMinutes(alarm.lockMinutes),
                onMinus = { vm.update { it.copy(lockMinutes = (it.lockMinutes - 15).coerceAtLeast(15)) } },
                onPlus = { vm.update { it.copy(lockMinutes = (it.lockMinutes + 15).coerceAtMost(480)) } },
                valueWidth = 96.dp,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        if (!enforcerOk) {
            item {
                CyberCard(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    accent = Hud.Red,
                    cut = 18.dp,
                    onClick = onPermissions,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("ENFORCER OFFLINE", style = HudType.micro.copy(fontSize = 12.sp), color = Hud.Red)
                        Text("App lock needs the Accessibility enforcer or Usage access. Tap to open SYS.PERMISSIONS.", style = HudType.small, color = Hud.Text)
                    }
                }
            }
        }
        item {
            SectionLabel("TARGETS // ${alarm.lockedPackages.size} SELECTED", bottom = 8.dp)
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = HudType.body.copy(color = Hud.Yellow, letterSpacing = 1.sp),
                cursorBrush = SolidColor(Hud.Yellow),
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawLine(Hud.Yellow.copy(alpha = 0.45f), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                    }
                    .padding(vertical = 10.dp),
                decorationBox = { inner ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(">", style = HudType.body, color = Hud.Cyan)
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.weight(1f)) {
                            if (query.isEmpty()) Text("SEARCH APPS", style = HudType.body, color = Hud.GreyDim)
                            inner()
                        }
                    }
                },
            )
        }
        if (apps == null) {
            item { Text("SCANNING PACKAGES...", style = HudType.micro, color = Hud.Cyan, modifier = Modifier.padding(top = 12.dp)) }
        }
        items(filtered, key = { it.packageName }) { app ->
            val checked = app.packageName in alarm.lockedPackages
            val title by animateColorAsState(if (checked) Hud.Yellow else Hud.Text, HudMotion.fade(400), label = "appTitle")
            fun toggle(on: Boolean) = vm.update { a ->
                a.copy(lockedPackages = if (on) a.lockedPackages + app.packageName else a.lockedPackages - app.packageName)
            }
            Row(
                Modifier
                    .animateItem()
                    .fillMaxWidth()
                    .hapticClickable { toggle(!checked) }
                    .drawBehind { drawLine(Hud.Grey.copy(alpha = 0.12f), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                    if (app.icon != null) Image(app.icon, null, Modifier.fillMaxSize())
                    else Text(app.label.take(1), style = displayStyle(14), color = Hud.Text)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(app.label, style = HudType.body.copy(fontSize = 14.sp), color = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(app.packageName, style = HudType.micro.copy(letterSpacing = 0.sp), color = Hud.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                HexToggle(checked, ::toggle, width = 52.dp, height = 26.dp)
            }
        }
    }
}

// ---------------------------------------------------------------- sound picker (1c)

@Composable
fun SoundPickerPage(alarm: Alarm, vm: EditAlarmViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val rank by vm.rank.collectAsStateWithLifecycle()
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

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PagePadding) {
        item { ScreenHeader("AUDIO_SOURCE", onBack) }
        item {
            CyberCard(
                Modifier.fillMaxWidth().padding(top = 18.dp),
                cut = 22.dp,
                contentPadding = PaddingValues(start = 20.dp, end = 18.dp, top = 16.dp, bottom = 16.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("ACTIVE", style = HudType.micro, color = Hud.Cyan)
                            Text(alarm.soundTitle, style = displayStyle(22), color = Hud.Yellow, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        EqBars(playing = previewing != null)
                    }
                    Text(
                        "Plays on the alarm stream, looping, ramping up over 30s. Falls back to NEON_PULSE if the track is gone.",
                        style = HudType.small,
                        color = Hud.TextDim,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
        item { SectionLabel("BUILT-IN // SYNTHWAVE", bottom = 4.dp) }
        items(BuiltinTone.entries, key = { it.key }) { tone ->
            if (rank < tone.rank) {
                SoundRow(
                    title = tone.title, subtitle = "LOCKED // REACH ${tone.rank.title}",
                    selected = false, playing = false, onSelect = {}, onPreview = {}, locked = true,
                )
                return@items
            }
            SoundRow(
                title = tone.title, subtitle = if (tone.rank > Rank.STREET_KID) "UNLOCKED // ${tone.rank.title} REWARD" else "CYBER-ALARM ORIGINAL",
                selected = alarm.soundUri == tone.key, playing = previewing == tone.key,
                onSelect = { vm.update { it.copy(soundUri = tone.key, soundTitle = tone.title) } },
                onPreview = { togglePreview(tone.key) },
            )
        }
        item {
            SectionLabel("MUSIC LIBRARY", bottom = 12.dp)
            CyberButton("BROWSE FILES", { fileLauncher.launch(arrayOf("audio/*")) }, color = Hud.Cyan, height = 44.dp, modifier = Modifier.fillMaxWidth())
            when {
                !hasLibrary -> CyberButton(
                    "GRANT LIBRARY ACCESS", { permissionLauncher.launch(audioPermission) },
                    height = 44.dp, modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
                tracks == null -> Text("INDEXING TRACKS...", style = HudType.micro, color = Hud.Cyan, modifier = Modifier.padding(top = 12.dp))
                tracks!!.isEmpty() -> Text("NO TRACKS FOUND", style = HudType.micro, color = Hud.TextDim, modifier = Modifier.padding(top = 12.dp))
            }
        }
        items(tracks.orEmpty(), key = { it.uri }) { track ->
            SoundRow(
                title = track.title, subtitle = track.artist.ifBlank { "LOCAL FILE" },
                selected = alarm.soundUri == track.uri, playing = previewing == track.uri,
                onSelect = { vm.update { it.copy(soundUri = track.uri, soundTitle = track.title) } },
                onPreview = { togglePreview(track.uri) },
            )
        }
    }
}

@Composable
private fun SoundRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    playing: Boolean,
    onSelect: () -> Unit,
    onPreview: () -> Unit,
    locked: Boolean = false,
) {
    val hexLine by animateColorAsState(if (selected) Hud.Yellow else Hud.Grey, HudMotion.fade(400), label = "hexLine")
    val hexFill by animateColorAsState(if (selected) Hud.Yellow else Hud.ToggleOff, HudMotion.fade(400), label = "hexFill")
    val titleColor by animateColorAsState(if (selected) Hud.Yellow else Hud.Text, HudMotion.fade(400), label = "rowTitle")
    val pColor by animateColorAsState(if (playing) Hud.Red else Hud.Cyan, HudMotion.fade(400), label = "pColor")
    val pLine by animateColorAsState(if (playing) Hud.Red.copy(alpha = 0.5f) else Hud.Cyan.copy(alpha = 0.35f), HudMotion.fade(400), label = "pLine")
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind { drawLine(Hud.Grey.copy(alpha = 0.12f), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            Modifier.weight(1f).hapticClickable(onClick = onSelect),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(18.dp, 16.dp).drawBehind {
                    drawPath(hexagonPath(size), hexLine)
                    val inset = 1.5.dp.toPx()
                    translate(inset, inset) {
                        drawPath(hexagonPath(androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)), hexFill)
                    }
                },
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = HudType.body.copy(fontSize = 14.sp, letterSpacing = 1.sp), color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = HudType.micro.copy(letterSpacing = 1.sp), color = Hud.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(
            Modifier.size(36.dp).border(1.dp, if (locked) Hud.GreyDim else pLine).hapticClickable(enabled = !locked, onClick = onPreview),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (locked) "×" else if (playing) "■" else "▶", style = HudType.small.copy(fontSize = 12.sp), color = if (locked) Hud.Off else pColor)
        }
    }
}
