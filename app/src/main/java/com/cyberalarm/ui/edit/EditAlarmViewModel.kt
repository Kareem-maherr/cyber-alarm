package com.cyberalarm.ui.edit

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cyberalarm.alarm.AlarmScheduler
import com.cyberalarm.cred.Rank
import com.cyberalarm.cred.WakeTracker
import com.cyberalarm.data.Alarm
import com.cyberalarm.data.AlarmRepository
import com.cyberalarm.lock.AppInfo
import com.cyberalarm.lock.InstalledApps
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class Track(val uri: String, val title: String, val artist: String)

@HiltViewModel
class EditAlarmViewModel @Inject constructor(
    savedState: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val repo: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val installedApps: InstalledApps,
    tracker: WakeTracker,
) : ViewModel() {

    val rank: StateFlow<Rank> = tracker.rank

    private val alarmId: Long = savedState.get<Long>(ARG_ALARM_ID) ?: -1L
    val isNew: Boolean = alarmId <= 0

    private val _draft = MutableStateFlow(if (isNew) Alarm() else null)
    val draft: StateFlow<Alarm?> = _draft.asStateFlow()

    private val _apps = MutableStateFlow<List<AppInfo>?>(null)
    val apps: StateFlow<List<AppInfo>?> = _apps.asStateFlow()

    private val _tracks = MutableStateFlow<List<Track>?>(null)
    val tracks: StateFlow<List<Track>?> = _tracks.asStateFlow()

    init {
        if (!isNew) viewModelScope.launch { _draft.value = repo.get(alarmId) ?: Alarm() }
    }

    fun update(transform: (Alarm) -> Alarm) = _draft.update { it?.let(transform) }

    fun save(onDone: () -> Unit) {
        val alarm = _draft.value ?: return
        viewModelScope.launch {
            // A changed alarm forgets any pending "skip next".
            val saved = repo.save(alarm.copy(enabled = true, skipAt = 0))
            scheduler.schedule(saved)
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            if (!isNew) {
                scheduler.cancel(alarmId)
                repo.delete(alarmId)
            }
            onDone()
        }
    }

    fun loadApps() {
        if (_apps.value != null) return
        viewModelScope.launch { _apps.value = installedApps.load() }
    }

    /** Reads the device music library. Caller must hold READ_MEDIA_AUDIO / READ_EXTERNAL_STORAGE. */
    fun loadTracks() {
        viewModelScope.launch {
            _tracks.value = withContext(Dispatchers.IO) {
                runCatching { queryTracks() }.getOrDefault(emptyList())
            }
        }
    }

    private fun queryTracks(): List<Track> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST)
        val out = mutableListOf<Track>()
        context.contentResolver.query(
            collection, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            while (c.moveToNext()) {
                out += Track(
                    uri = ContentUris.withAppendedId(collection, c.getLong(idCol)).toString(),
                    title = c.getString(titleCol) ?: "UNKNOWN",
                    artist = c.getString(artistCol)?.takeUnless { it == "<unknown>" } ?: "",
                )
            }
        }
        return out
    }

    companion object {
        const val ARG_ALARM_ID = "alarmId"
    }
}
