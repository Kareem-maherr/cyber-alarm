package com.cyberalarm.settings

import android.content.Context
import android.net.Uri
import com.cyberalarm.alarm.AlarmScheduler
import com.cyberalarm.challenge.ChallengeSpec
import com.cyberalarm.data.Alarm
import com.cyberalarm.data.AlarmDao
import com.cyberalarm.data.CredDao
import com.cyberalarm.data.CredEntry
import com.cyberalarm.data.WakeDao
import com.cyberalarm.data.WakeSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exports / imports everything worth keeping as one JSON file (user picks the location via the
 * system file picker): alarms, settings, jack-in tags, wake history and the Street Cred ledger.
 *
 * Custom sound URIs are kept as-is; on a new phone they usually point nowhere, and the player then
 * falls back to the built-in tone.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmDao: AlarmDao,
    private val wakeDao: WakeDao,
    private val credDao: CredDao,
    private val settings: SettingsStore,
    private val jackIn: JackInStore,
    private val scheduler: AlarmScheduler,
) {
    suspend fun export(uri: Uri): Int = withContext(Dispatchers.IO) {
        val alarms = alarmDao.getAll()
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("exportedAt", System.currentTimeMillis())
            .put("alarms", JSONArray(alarms.map { it.toJson() }))
            .put("settings", settings.current.toJson())
            .put("jackIn", JSONArray(jackIn.tags.value.map { it.encode() }))
            .put("sessions", JSONArray(wakeDao.getAll().map { it.toJson() }))
            .put("cred", JSONArray(credDao.getAll().map { it.toJson() }))
        context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(root.toString(2).toByteArray()) }
        alarms.size
    }

    /** Replaces the current data with the backup. Returns the number of alarms restored. */
    suspend fun import(uri: Uri): Int = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        val root = JSONObject(text)
        require(root.optString("format") == FORMAT) { "Not a Cyber-Alarm backup" }

        alarmDao.getAll().forEach { scheduler.cancel(it.id) }
        alarmDao.deleteAll()
        val alarms = root.getJSONArray("alarms").objects().map { alarmFrom(it) }
        alarms.forEach { alarmDao.insert(it) }

        root.optJSONObject("settings")?.let { s -> settings.update { settingsFrom(s, it) } }
        root.optJSONArray("jackIn")?.let { arr -> jackIn.replaceAll((0 until arr.length()).mapNotNull { JackInTag.decode(arr.getString(it)) }) }

        wakeDao.deleteAll()
        wakeDao.insertAll(root.optJSONArray("sessions")?.objects()?.map { sessionFrom(it) }.orEmpty())
        credDao.deleteAll()
        credDao.insertAll(root.optJSONArray("cred")?.objects()?.map { credFrom(it) }.orEmpty())

        scheduler.rescheduleAll(alarmDao.getAll())
        alarms.size
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun Alarm.toJson() = JSONObject()
        .put("id", id).put("hour", hour).put("minute", minute).put("label", label)
        .put("repeatDays", repeatDays).put("enabled", enabled).put("vibrate", vibrate)
        .put("snoozeEnabled", snoozeEnabled).put("snoozeMinutes", snoozeMinutes).put("maxSnoozes", maxSnoozes)
        .put("soundUri", soundUri).put("soundTitle", soundTitle)
        .put("challenges", ChallengeSpec.encode(challenges))
        .put("lockedPackages", lockedPackages.joinToString(","))
        .put("lockMinutes", lockMinutes).put("awakeCheck", awakeCheck)

    private fun alarmFrom(o: JSONObject): Alarm {
        val d = Alarm()
        return Alarm(
            id = o.optLong("id", 0),
            hour = o.optInt("hour", d.hour),
            minute = o.optInt("minute", d.minute),
            label = o.optString("label", d.label),
            repeatDays = o.optInt("repeatDays", 0),
            enabled = o.optBoolean("enabled", true),
            vibrate = o.optBoolean("vibrate", true),
            snoozeEnabled = o.optBoolean("snoozeEnabled", true),
            snoozeMinutes = o.optInt("snoozeMinutes", d.snoozeMinutes),
            maxSnoozes = o.optInt("maxSnoozes", d.maxSnoozes),
            soundUri = o.optString("soundUri", d.soundUri),
            soundTitle = o.optString("soundTitle", d.soundTitle),
            challenges = ChallengeSpec.decode(o.optString("challenges")),
            lockedPackages = o.optString("lockedPackages").split(",").filter { it.isNotBlank() },
            lockMinutes = o.optInt("lockMinutes", d.lockMinutes),
            awakeCheck = o.optBoolean("awakeCheck", true),
        )
    }

    private fun AppSettings.toJson() = JSONObject()
        .put("bedtimeEnabled", bedtimeEnabled).put("sleepMinutes", sleepMinutes).put("nightLock", nightLock)
        .put("voiceLines", voiceLines).put("holidayUntil", holidayUntil).put("theme", theme).put("watchLink", watchLink)
        .put("soundPack", soundPack)

    private fun settingsFrom(o: JSONObject, d: AppSettings) = AppSettings(
        bedtimeEnabled = o.optBoolean("bedtimeEnabled", d.bedtimeEnabled),
        sleepMinutes = o.optInt("sleepMinutes", d.sleepMinutes),
        nightLock = o.optBoolean("nightLock", d.nightLock),
        voiceLines = o.optBoolean("voiceLines", d.voiceLines),
        holidayUntil = o.optLong("holidayUntil", d.holidayUntil),
        theme = o.optString("theme", d.theme),
        watchLink = o.optBoolean("watchLink", d.watchLink),
        soundPack = o.optString("soundPack", d.soundPack),
        // Custom sound files live in this phone's app storage and are not in the backup.
        soundKey = d.soundKey,
        soundError = d.soundError,
        soundOk = d.soundOk,
    )

    private fun WakeSession.toJson() = JSONObject()
        .put("id", id).put("alarmId", alarmId).put("label", label).put("firedAt", firedAt).put("dismissedAt", dismissedAt)
        .put("mistakes", mistakes).put("snoozes", snoozes).put("reRings", reRings).put("checksPassed", checksPassed)
        .put("maxEscalation", maxEscalation).put("usedJackIn", usedJackIn).put("eddies", eddies)

    private fun sessionFrom(o: JSONObject) = WakeSession(
        id = o.optLong("id"), alarmId = o.optLong("alarmId"), label = o.optString("label"), firedAt = o.optLong("firedAt"),
        dismissedAt = o.optLong("dismissedAt"), mistakes = o.optInt("mistakes"), snoozes = o.optInt("snoozes"),
        reRings = o.optInt("reRings"), checksPassed = o.optInt("checksPassed"), maxEscalation = o.optInt("maxEscalation"),
        usedJackIn = o.optBoolean("usedJackIn"), eddies = o.optInt("eddies"),
    )

    private fun CredEntry.toJson() = JSONObject()
        .put("id", id).put("at", at).put("amount", amount).put("reason", reason).put("sessionId", sessionId)

    private fun credFrom(o: JSONObject) = CredEntry(
        id = o.optLong("id"), at = o.optLong("at"), amount = o.optInt("amount"), reason = o.optString("reason"), sessionId = o.optLong("sessionId"),
    )

    private companion object {
        const val FORMAT = "cyber-alarm-backup"
        const val VERSION = 1
    }
}
