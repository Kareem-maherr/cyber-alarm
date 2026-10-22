package com.cyberalarm.ui.cred

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cyberalarm.challenge.Difficulty
import com.cyberalarm.cred.CrewTheme
import com.cyberalarm.cred.Gig
import com.cyberalarm.cred.GigBoard
import com.cyberalarm.cred.Rank
import com.cyberalarm.cred.Scoring
import com.cyberalarm.cred.WakeTracker
import com.cyberalarm.data.BuiltinTone
import com.cyberalarm.data.WakeSession
import com.cyberalarm.ui.components.CyberButton
import com.cyberalarm.ui.components.CyberCard
import com.cyberalarm.ui.components.NightCityScreen
import com.cyberalarm.ui.components.ScreenHeader
import com.cyberalarm.ui.components.SectionLabel
import com.cyberalarm.ui.components.displayStyle
import com.cyberalarm.ui.ringing.RankBar
import com.cyberalarm.ui.ringing.eddies
import com.cyberalarm.ui.ringing.formatDuration
import com.cyberalarm.ui.theme.Hud
import com.cyberalarm.ui.theme.HudType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class WakeLogViewModel @Inject constructor(tracker: WakeTracker) : ViewModel() {
    val total: StateFlow<Int> = tracker.total
    val sessions: StateFlow<List<WakeSession>> = tracker.sessions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val claimed: StateFlow<Set<String>> = tracker.claimedGigs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    val gig: Gig = GigBoard.today()
}

/** Street Cred, today's gig, unlocks and wake history. */
@Composable
fun WakeLogScreen(onBack: () -> Unit, onGig: () -> Unit, vm: WakeLogViewModel = hiltViewModel()) {
    val total by vm.total.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val claimed by vm.claimed.collectAsStateWithLifecycle()
    val rank = Rank.of(total)
    val streak = Scoring.streak(sessions)
    val insets = WindowInsets.safeDrawing.asPaddingValues()

    NightCityScreen {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp,
                top = insets.calculateTopPadding() + 12.dp, bottom = insets.calculateBottomPadding() + 48.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ScreenHeader("WAKE_LOG", onBack) }
            item {
                CyberCard(Modifier.fillMaxWidth(), active = true, contentPadding = PaddingValues(18.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("STREET CRED", style = HudType.micro, color = Hud.Cyan)
                        Text(
                            rank.title,
                            style = displayStyle(32, FontWeight.Bold, 3f).copy(shadow = Shadow(Hud.Yellow.copy(alpha = 0.25f), blurRadius = 40f)),
                            color = Hud.Yellow,
                        )
                        Row {
                            Text(eddies(total), style = HudType.value, color = Hud.Text, modifier = Modifier.weight(1f))
                            Text(
                                if (streak > 0) "STREAK // $streak CLEAN" else "NO STREAK",
                                style = HudType.small,
                                color = if (streak > 0) Hud.Yellow else Hud.TextDim,
                            )
                        }
                        RankBar(total)
                    }
                }
            }
            item {
                val gig = vm.gig
                val paid = gig.id in claimed
                CyberCard(Modifier.fillMaxWidth(), accent = Hud.Cyan, cut = 20.dp, contentPadding = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("DAILY GIG // ${gig.fixer}", style = HudType.micro, color = Hud.Cyan, modifier = Modifier.weight(1f))
                            Text(eddies(gig.reward, signed = true), style = HudType.small, color = Hud.Yellow)
                        }
                        Text("“${gig.message}”", style = HudType.body, color = Hud.Text)
                        Text(gig.specs.joinToString("  >  ") { it.summary() }, style = HudType.micro, color = Hud.TextDim)
                        if (paid) {
                            Text("GIG COMPLETE // PAID. NEW GIG TOMORROW.", style = HudType.small, color = Hud.Yellow)
                        } else {
                            CyberButton("TAKE THE GIG", onGig, filled = true, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            item { SectionLabel("UNLOCKS", bottom = 2.dp) }
            items(unlocks()) { u -> UnlockRow(u, total >= u.rank.minEddies) }
            item { SectionLabel("HISTORY", bottom = 2.dp) }
            if (sessions.isEmpty()) {
                item { Text("NO WAKES LOGGED YET, CHOOM. SET AN ALARM.", style = HudType.small, color = Hud.TextDim) }
            }
            items(sessions, key = { it.id }) { SessionRow(it) }
        }
    }
}

private data class Unlock(val title: String, val kind: String, val rank: Rank)

private fun unlocks(): List<Unlock> = buildList {
    CrewTheme.entries.filter { it.rank > Rank.STREET_KID }.forEach { add(Unlock(it.title, "THEME", it.rank)) }
    BuiltinTone.entries.filter { it.rank > Rank.STREET_KID }.forEach { add(Unlock(it.title, "TONE", it.rank)) }
    add(Unlock("BREACH ${Difficulty.LEGEND.short}", "DIFFICULTY", Rank.MERC))
}.sortedBy { it.rank }

@Composable
private fun UnlockRow(u: Unlock, unlocked: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (unlocked) "■" else "□", style = HudType.small, color = if (unlocked) Hud.Yellow else Hud.Off, modifier = Modifier.padding(end = 10.dp))
        Column(Modifier.weight(1f)) {
            Text(u.title, style = HudType.value, color = if (unlocked) Hud.Text else Hud.Off)
            Text(u.kind, style = HudType.micro, color = Hud.TextDim)
        }
        Text(if (unlocked) "UNLOCKED" else u.rank.title, style = HudType.micro, color = if (unlocked) Hud.Yellow else Hud.TextDim)
    }
}

private val sessionFormat = DateTimeFormatter.ofPattern("EEE dd MMM · HH:mm", Locale.ENGLISH)

@Composable
private fun SessionRow(s: WakeSession) {
    CyberCard(Modifier.fillMaxWidth(), active = s.clean, cut = 14.dp, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    sessionFormat.format(Instant.ofEpochMilli(s.firedAt).atZone(ZoneId.systemDefault())).uppercase(Locale.ENGLISH),
                    style = HudType.small,
                    color = Hud.Text,
                    modifier = Modifier.weight(1f),
                )
                Text(eddies(s.eddies, signed = true), style = HudType.small, color = if (s.eddies >= 0) Hud.Yellow else Hud.Red)
            }
            Text(s.label, style = HudType.micro, color = Hud.Cyan)
            val tags = buildList {
                if (s.dismissed) add("WAKE ${formatDuration(s.secondsToWake)}") else add("UNFINISHED")
                if (s.mistakes > 0) add("${s.mistakes} ERR")
                if (s.snoozes > 0) add("${s.snoozes} SNOOZE")
                if (s.checksPassed > 0) add("${s.checksPassed} CHECK OK")
                if (s.reRings > 0) add("${s.reRings} RELAPSE")
                if (s.usedJackIn) add("JACKED IN")
                if (s.clean) add("CLEAN")
            }
            Text(tags.joinToString(" · "), style = HudType.micro, color = if (s.reRings > 0) Hud.Red else Hud.TextDim)
        }
    }
}
