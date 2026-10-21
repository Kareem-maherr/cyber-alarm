package com.cyberalarm.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import com.cyberalarm.challenge.ChallengeSpec
import kotlinx.coroutines.flow.Flow

class Converters {
    @TypeConverter
    fun challengesToString(list: List<ChallengeSpec>): String = ChallengeSpec.encode(list)

    @TypeConverter
    fun stringToChallenges(raw: String?): List<ChallengeSpec> = ChallengeSpec.decode(raw)

    // Package names can't contain commas, so a plain CSV is safe.
    @TypeConverter
    fun packagesToString(list: List<String>): String = list.joinToString(",")

    @TypeConverter
    fun stringToPackages(raw: String?): List<String> = raw.orEmpty().split(",").filter { it.isNotBlank() }
}

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour, minute")
    fun observeAll(): Flow<List<Alarm>>

    @Query("SELECT * FROM alarms ORDER BY hour, minute")
    suspend fun getAll(): List<Alarm>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun get(id: Long): Alarm?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alarm: Alarm): Long

    @Query("UPDATE alarms SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM alarms WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface LockDao {
    @Query("SELECT * FROM lock_windows")
    fun observeAll(): Flow<List<LockWindow>>

    @Query("SELECT * FROM lock_windows")
    suspend fun getAll(): List<LockWindow>

    @Query("SELECT * FROM lock_windows WHERE packageName = :pkg")
    suspend fun get(pkg: String): LockWindow?

    @Upsert
    suspend fun upsert(window: LockWindow)

    @Query("DELETE FROM lock_windows WHERE lockedUntil <= :now")
    suspend fun deleteExpired(now: Long)
}

@Database(entities = [Alarm::class, LockWindow::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun lockDao(): LockDao
}
