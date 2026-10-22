package com.cyberalarm.wear

import android.content.Intent
import com.cyberalarm.alarm.AwakeCheckManager
import com.cyberalarm.enforcement.AwakeCheckActivity
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Watch -> phone: "OPEN ON PHONE" on the watch's awake-check prompt brings the check up here. */
@AndroidEntryPoint
class PhoneWearListener : WearableListenerService() {
    @Inject lateinit var awake: AwakeCheckManager

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WearPaths.OPEN_AWAKE) return
        val state = awake.state()?.takeIf { it.running } ?: return
        // Background activity start: allowed because we hold SYSTEM_ALERT_WINDOW.
        runCatching {
            startActivity(AwakeCheckActivity.intent(this, state.alarmId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
