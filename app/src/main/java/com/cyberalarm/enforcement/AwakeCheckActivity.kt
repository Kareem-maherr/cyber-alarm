package com.cyberalarm.enforcement

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cyberalarm.alarm.AlarmController
import com.cyberalarm.alarm.AlarmReceiver
import com.cyberalarm.alarm.AwakeCheckManager
import com.cyberalarm.alarm.RingingStateStore
import com.cyberalarm.di.AppScope
import com.cyberalarm.ui.awake.AwakeCheckScreen
import com.cyberalarm.ui.theme.CyberTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The post-dismissal "still awake?" prompt. Shown over the lock screen. Leaving it is allowed; the
 * deadline alarm re-fires the alarm anyway if the challenge isn't solved in time.
 */
@AndroidEntryPoint
class AwakeCheckActivity : ComponentActivity() {

    @Inject lateinit var awake: AwakeCheckManager
    @Inject lateinit var controller: AlarmController
    @Inject lateinit var ringing: RingingStateStore
    @Inject @field:AppScope lateinit var appScope: CoroutineScope

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val alarmId = intent.getLongExtra(AlarmReceiver.EXTRA_ALARM_ID, -1)
        val state = awake.state()?.takeIf { it.alarmId == alarmId && it.running }
        if (state == null) {
            finishAndRemoveTask()
            return
        }
        setContent {
            CyberTheme {
                AwakeCheckScreen(
                    stage = state.stage,
                    stages = AwakeCheckManager.STAGES,
                    deadline = state.deadline,
                    onPassed = {
                        awake.passed(alarmId)
                        finishAndRemoveTask()
                    },
                    onExpired = {
                        // Backup for the deadline alarm; expire() only lets one of them re-fire.
                        appScope.launch(Dispatchers.IO) {
                            if (awake.expire(alarmId) && !ringing.isRinging()) controller.reRing(alarmId)
                        }
                        finishAndRemoveTask()
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Solved elsewhere / already expired: nothing to show.
        val id = intent.getLongExtra(AlarmReceiver.EXTRA_ALARM_ID, -1)
        if (awake.state()?.let { it.alarmId == id && it.running } != true) finishAndRemoveTask()
    }

    companion object {
        fun intent(context: Context, alarmId: Long): Intent =
            Intent(context, AwakeCheckActivity::class.java)
                .putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
    }
}
