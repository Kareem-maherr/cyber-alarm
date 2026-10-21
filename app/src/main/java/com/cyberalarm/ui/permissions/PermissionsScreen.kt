package com.cyberalarm.ui.permissions

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.cyberalarm.ui.components.CyberButton
import com.cyberalarm.ui.components.CyberCard
import com.cyberalarm.ui.components.HexChip
import com.cyberalarm.ui.components.ScreenHeader
import com.cyberalarm.ui.components.SectionLabel
import com.cyberalarm.ui.components.hudBackground
import com.cyberalarm.ui.theme.Hud
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
    LazyColumn(
        Modifier.fillMaxSize().hudBackground(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = insets.calculateTopPadding() + 8.dp,
            bottom = insets.calculateBottomPadding() + 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("SYS.PERMISSIONS", onBack) }
        item {
            Text(
                "Every permission is opt-in. CORE makes alarms ring on time. ENFORCER makes them hard to escape and powers the app lock. OPTIONAL unlocks extras.",
                style = HudType.body,
                color = Hud.TextDim,
            )
        }
        PermissionTier.entries.forEach { tier ->
            val group = items.filter { it.tier == tier }
            if (group.isEmpty()) return@forEach
            item(key = "label_$tier") { SectionLabel(tier.name, color = if (tier == PermissionTier.CORE) Hud.Yellow else Hud.Cyan) }
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
            SectionLabel("INSTALL-TIME", color = Hud.Grey)
            Text(
                Permissions.installTime.joinToString("\n") { "■ $it  // GRANTED" },
                style = HudType.small,
                color = Hud.TextDim,
            )
        }
    }
}

@Composable
private fun PermissionCard(item: PermissionItem, onGrant: () -> Unit) {
    CyberCard(Modifier.fillMaxWidth(), active = item.granted, stripes = false, contentPadding = PaddingValues(start = 22.dp, end = 34.dp, top = 16.dp, bottom = 16.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.title, style = HudType.label.copy(fontSize = HudType.body.fontSize), color = if (item.granted) Hud.Yellow else Hud.Text, modifier = Modifier.weight(1f))
                HexChip(if (item.granted) "ONLINE" else "OFFLINE", color = if (item.granted) Hud.Yellow else Hud.Red, filled = item.granted)
            }
            Spacer(Modifier.height(6.dp))
            Text(item.rationale, style = HudType.small.copy(lineHeight = HudType.body.lineHeight), color = Hud.TextDim)
            if (!item.granted) {
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    CyberButton("GRANT", onGrant, color = Hud.Yellow, modifier = Modifier.padding(end = 8.dp))
                }
            }
        }
    }
}

private fun Context.safeStart(intent: Intent) {
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .recoverCatching { startActivity(Permissions.appDetails(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
