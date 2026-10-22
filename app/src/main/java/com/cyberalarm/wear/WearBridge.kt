package com.cyberalarm.wear

import android.content.Context
import android.util.Log
import com.cyberalarm.di.AppScope
import com.cyberalarm.settings.SettingsStore
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phone -> watch messages over the Wear Data Layer. The watch only buzzes and shows prompts; it can
 * never dismiss anything (that would make waking up far too easy). Fire-and-forget: no watch, no
 * Play services, or the link turned off in settings all just mean nothing is sent.
 */
@Singleton
class WearBridge @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
    @AppScope private val scope: CoroutineScope,
) {
    fun ringStarted(label: String) = send(WearPaths.RING, label)

    fun ringStopped() = send(WearPaths.STOP, "")

    fun awakeCheck(stage: Int, deadline: Long) = send(WearPaths.AWAKE, "$stage|$deadline")

    fun awakeDone() = send(WearPaths.STOP, "")

    /** Number of connected watches, for the settings screen. */
    suspend fun connectedWatches(): Int =
        runCatching { Wearable.getNodeClient(context).connectedNodes.await().size }.getOrDefault(0)

    private fun send(path: String, payload: String) {
        if (!settings.current.watchLink) return
        scope.launch {
            runCatching {
                val nodes = Wearable.getNodeClient(context).connectedNodes.await()
                nodes.forEach { node ->
                    Wearable.getMessageClient(context).sendMessage(node.id, path, payload.toByteArray()).await()
                }
            }.onFailure { Log.d(TAG, "No watch reachable for $path: ${it.message}") }
        }
    }

    private companion object {
        const val TAG = "WearBridge"
    }
}

/** Message paths shared with the :wear module (keep in sync with its copy). */
object WearPaths {
    const val RING = "/cyberalarm/ring"
    const val STOP = "/cyberalarm/stop"
    const val AWAKE = "/cyberalarm/awake"
    /** watch -> phone: "bring the awake check up on the phone". */
    const val OPEN_AWAKE = "/cyberalarm/open_awake"
}
