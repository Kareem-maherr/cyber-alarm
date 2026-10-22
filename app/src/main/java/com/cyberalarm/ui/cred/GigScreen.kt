package com.cyberalarm.ui.cred

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cyberalarm.challenge.ChallengeRunner
import com.cyberalarm.cred.Gig
import com.cyberalarm.cred.GigBoard
import com.cyberalarm.cred.WakeTracker
import com.cyberalarm.ui.components.CyberButton
import com.cyberalarm.ui.components.NightCityScreen
import com.cyberalarm.ui.components.ScreenHeader
import com.cyberalarm.ui.components.displayStyle
import com.cyberalarm.ui.ringing.eddies
import com.cyberalarm.ui.theme.Hud
import com.cyberalarm.ui.theme.HudType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GigViewModel @Inject constructor(private val tracker: WakeTracker) : ViewModel() {
    val gig: Gig = GigBoard.today()

    /** null = playing, true = paid now, false = was already paid earlier today. */
    private val _result = MutableStateFlow<Boolean?>(null)
    val result: StateFlow<Boolean?> = _result.asStateFlow()

    fun complete() {
        viewModelScope.launch { _result.value = tracker.claimGig(gig) }
    }
}

@Composable
fun GigScreen(onBack: () -> Unit, vm: GigViewModel = hiltViewModel()) {
    val result by vm.result.collectAsStateWithLifecycle()
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    NightCityScreen {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = insets.calculateTopPadding() + 12.dp, bottom = insets.calculateBottomPadding() + 48.dp),
        ) {
            ScreenHeader("GIG // ${vm.gig.fixer}", onBack)
            Text("“${vm.gig.message}”", style = HudType.body, color = Hud.Text, modifier = Modifier.padding(vertical = 8.dp))
            Spacer(Modifier.height(10.dp))
            when (result) {
                null -> ChallengeRunner(vm.gig.specs, onAllPassed = vm::complete)
                else -> Column(
                    Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("GIG COMPLETE", style = displayStyle(26, spacing = 3f), color = Hud.Yellow)
                    Text(
                        if (result == true) "${eddies(vm.gig.reward, signed = true)} TRANSFERRED. PREEM WORK, CHOOM." else "ALREADY PAID TODAY. NO DOUBLE-DIPPING.",
                        style = HudType.small,
                        color = Hud.Cyan,
                    )
                    CyberButton("BACK TO THE STREET", onBack, color = Hud.Cyan, modifier = Modifier.padding(top = 10.dp))
                }
            }
        }
    }
}
