package com.cyberalarm

import android.app.Application
import com.cyberalarm.alarm.Notifications
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CyberAlarmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }
}
