package com.cyberalarm.ui.permissions

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.cyberalarm.ui.components.CyberButton
import com.cyberalarm.ui.components.CyberCard
import com.cyberalarm.ui.components.NightCityScreen
import com.cyberalarm.ui.components.ScreenHeader
import com.cyberalarm.ui.components.SectionLabel
import com.cyberalarm.ui.components.StateTag
import com.cyberalarm.ui.theme.Hud
import com.cyberalarm.ui.theme.HudMotion
import com.cyberalarm.ui.theme.HudType

/** Dashboard of every permission the app uses, with a rationale and a grant button for each. */
@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val items = remember(refresh) { Permissions.all(context) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        // Denied twice -> Android stops showing the dialog; send the user to app settings instead.
        if (!ok) context.safeStart(Permissions.appDetails(context))
        refresh++
    }

    val insets = WindowInsets.safeDrawing.asPaddingValues()
    NightCityScreen {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp,
                top = insets.calculateTopPadding() + 12.dp,
                bottom = insets.calculateBottomPadding() + 48.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                ScreenHeader("SYS.PERMISSIONS", onBack) {
                    Text("${items.count { it.granted }}/${items.size} ONLINE", style = HudType.small, color = Hud.Yellow)
                }
            }
            item {
                Text(
                    "Every permission is opt-in. CORE makes alarms ring on time. ENFORCER makes them hard to escape and powers the app lock. OPTIONAL unlocks extras.",
                    style = HudType.body,
                    color = Hud.TextDim,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            PermissionTier.entries.forEach { tier ->
                val group = items.filter { it.tier == tier }
                if (group.isEmpty()) return@forEach
                item(key = "label_$tier") {
                    SectionLabel(tier.name, color = if (tier == PermissionTier.CORE) Hud.Yellow else Hud.Cyan, top = 14.dp, bottom = 0.dp)
                }
                items(group, key = { it.key }) { item ->
                    PermissionCard(item) {
                        when {
                            item.runtime != null -> launcher.launch(item.runtime)
                            item.settingsIntent != null -> context.safeStart(item.settingsIntent)
                        }
                    }
                }
            }
            item(key = "install") {
                SectionLabel("INSTALL-TIME", color = Hud.Grey, top = 14.dp, bottom = 6.dp)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Permissions.installTime.forEach {
                        Text("■ $it  // GRANTED", style = HudType.small, color = Hud.TextDim)
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(item: PermissionItem, onGrant: () -> Unit) {
    val title by animateColorAsState(if (item.granted) Hud.Yellow else Hud.Text, HudMotion.fade(600), label = "permTitle")
    // Granted cards are yellow-tinted; offline ones carry a red tint (always "active" so it shows).
    CyberCard(
        Modifier.fillMaxWidth(),
        accent = if (item.granted) Hud.Yellow else Hud.Red,
        cut = 18.dp,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(item.title, style = HudType.body.copy(letterSpacing = 1.5.sp), color = title, modifier = Modifier.weight(1f))
                StateTag(item.granted)
            }
            Text(item.rationale, style = HudType.small.copy(lineHeight = 17.sp), color = Hud.TextDim)
            AnimatedVisibility(
                visible = !item.granted,
                enter = expandVertically(HudMotion.glide(600)) + fadeIn(HudMotion.fade()),
                exit = shrinkVertically(HudMotion.glide(600)) + fadeOut(HudMotion.fade()),
            ) {
                Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.CenterEnd) {
                    CyberButton("GRANT", onGrant, filled = true, height = 34.dp, cut = 8.dp)
                }
            }
        }
    }
}

private fun Context.safeStart(intent: Intent) {
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .recoverCatching { startActivity(Permissions.appDetails(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
