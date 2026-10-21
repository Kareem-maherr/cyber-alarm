package com.cyberalarm.enforcement

import android.content.Context
import android.content.Intent
import android.os.Build
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.cyberalarm.alarm.AlarmReceiver
import com.cyberalarm.alarm.RingingStateStore
import com.cyberalarm.ui.ringing.RingingScreen
import com.cyberalarm.ui.theme.CyberTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The ringing screen. Shown over the lock screen, turns the screen on, keeps it on, hides the
 * system bars, swallows Back and the volume keys. Leaving it any other way (Home, Recents, an
 * app switch gesture) is caught by AlarmService's watchdog + overlay and by the accessibility
 * service, which bring it straight back.
 *
 * OS LIMITS (best effort, not guarantees, on non-rooted stock Android):
 *  - The hardware power button cannot be intercepted. Long-press power-off can't be blocked; we
 *    only *close* the power menu (accessibility) and re-fire the alarm after reboot (BootReceiver).
 *  - Home/Recents can't be disabled for a normal app; we can only return immediately.
 *  - Force-stop from Settings cancels all alarms and kills the app until it is opened again.
 */
@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {

    @Inject lateinit var store: RingingStateStore
    @Inject lateinit var overlay: OverlayManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        showOverLockScreen()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        // Back does nothing while ringing.
        onBackPressedDispatcher.addCallback(this) { }

        val alarmId = intent.getLongExtra(AlarmReceiver.EXTRA_ALARM_ID, -1).takeIf { it >= 0 }
            ?: store.activeAlarmId()
        if (alarmId == null || !store.isRinging()) {
            finishAndRemoveTask()
            return
        }

        setContent {
            CyberTheme {
                RingingScreen(onFinished = { finishAndRemoveTask() })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        onScreen = true
        overlay.hide() // don't wait for the watchdog tick to uncover the alarm
    }

    override fun onStop() {
        onScreen = false
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        if (!store.isRinging()) finishAndRemoveTask()
    }

    /** Volume keys are consumed so the alarm can't be silenced from the hardware buttons. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE -> true
        else -> super.onKeyDown(keyCode, event)
    }

    /**
     * Losing focus while still in front usually means a system dialog (power menu, notification
     * shade) opened over us. ACTION_CLOSE_SYSTEM_DIALOGS works up to Android 11; from Android 12 it
     * is restricted for normal apps, so there we rely on CyberAccessibilityService instead.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        } else if (store.isRinging() && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            runCatching { sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)) }
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    companion object {
        /** Read by AlarmService's watchdog to detect escapes. */
        @Volatile
        var onScreen: Boolean = false
            private set

        fun intent(context: Context, alarmId: Long): Intent =
            Intent(context, AlarmActivity::class.java)
                .putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
    }
}
