package com.cyberalarm.lock

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/**
 * Usage-stats based foreground-app detection: the fallback used when the accessibility service is
 * off. Needs the PACKAGE_USAGE_STATS special access. Polling-based, so it reacts within ~1s.
 */
class ForegroundAppDetector(context: Context) {

    private val usm = context.getSystemService(UsageStatsManager::class.java)
    private var lastKnown: String? = null

    fun currentPackage(): String? {
        val now = System.currentTimeMillis()
        val events = runCatching { usm.queryEvents(now - 15_000, now) }.getOrNull() ?: return lastKnown
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) lastKnown = event.packageName
        }
        return lastKnown
    }
}
