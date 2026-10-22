package com.cyberalarm

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cyberalarm.alarm.AlarmService
import com.cyberalarm.alarm.RingingStateStore
import com.cyberalarm.enforcement.AlarmActivity
import com.cyberalarm.ui.config.ConfigScreen
import com.cyberalarm.ui.cred.GigScreen
import com.cyberalarm.ui.cred.WakeLogScreen
import com.cyberalarm.ui.edit.EditAlarmScreen
import com.cyberalarm.ui.edit.EditAlarmViewModel
import com.cyberalarm.ui.list.AlarmListScreen
import com.cyberalarm.ui.permissions.PermissionsScreen
import com.cyberalarm.ui.splash.BootSplash
import com.cyberalarm.ui.theme.CyberTheme
import com.cyberalarm.ui.theme.HudMotion
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var store: RingingStateStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        val coldStart = savedInstanceState == null
        setContent {
            CyberTheme {
                // Boot splash on a cold launch only (not on rotation / process restore).
                var splash by rememberSaveable { mutableStateOf(coldStart) }
                Box(Modifier.fillMaxSize()) {
                    AppNav()
                    if (splash) BootSplash(onFinished = { splash = false })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Opening the app while an alarm rings just leads back to the alarm.
        // Also restarts the service in case it was killed (e.g. force-stop) while ringing.
        store.activeAlarmId()?.let {
            runCatching { AlarmService.start(this, it) }
            startActivity(AlarmActivity.intent(this, it))
        }
    }
}

private object Routes {
    const val LIST = "list"
    const val EDIT = "edit?${EditAlarmViewModel.ARG_ALARM_ID}={${EditAlarmViewModel.ARG_ALARM_ID}}"
    const val PERMISSIONS = "permissions"
    const val LOG = "log"
    const val GIG = "gig"
    const val CONFIG = "config"
    fun edit(id: Long?) = if (id == null) "edit" else "edit?${EditAlarmViewModel.ARG_ALARM_ID}=$id"
}

@Composable
private fun AppNav() {
    val nav = rememberNavController()
    val context = LocalContext.current

    // First launch: show the permissions dashboard once, on top of the list.
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("app", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("onboarded", false)) {
            prefs.edit { putBoolean("onboarded", true) }
            nav.navigate(Routes.PERMISSIONS)
        }
    }

    NavHost(
        navController = nav,
        startDestination = Routes.LIST,
        enterTransition = { fadeIn(tween(350, easing = HudMotion.Ease)) },
        exitTransition = { fadeOut(tween(200)) },
    ) {
        composable(Routes.LIST) {
            AlarmListScreen(
                onAdd = { nav.navigate(Routes.edit(null)) },
                onEdit = { nav.navigate(Routes.edit(it)) },
                onPermissions = { nav.navigate(Routes.PERMISSIONS) },
                onLog = { nav.navigate(Routes.LOG) },
                onConfig = { nav.navigate(Routes.CONFIG) },
            )
        }
        composable(Routes.LOG) {
            WakeLogScreen(onBack = { nav.popBackStack() }, onGig = { nav.navigate(Routes.GIG) })
        }
        composable(Routes.GIG) {
            GigScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.CONFIG) {
            ConfigScreen(onBack = { nav.popBackStack() }, onPermissions = { nav.navigate(Routes.PERMISSIONS) })
        }
        composable(
            Routes.EDIT,
            arguments = listOf(
                navArgument(EditAlarmViewModel.ARG_ALARM_ID) {
                    type = NavType.LongType
                    defaultValue = -1L
                },
            ),
        ) {
            EditAlarmScreen(
                onDone = { nav.popBackStack() },
                onPermissions = { nav.navigate(Routes.PERMISSIONS) },
            )
        }
        composable(Routes.PERMISSIONS) {
            PermissionsScreen(onBack = { nav.popBackStack() })
        }
    }
}
