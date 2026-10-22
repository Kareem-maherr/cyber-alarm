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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

    @Query("DELETE FROM alarms")
    suspend fun deleteAll()
}

@Dao
interface WakeDao {
    @Insert
    suspend fun insert(session: WakeSession): Long

    @androidx.room.Update
    suspend fun update(session: WakeSession)

    @Query("SELECT * FROM wake_sessions WHERE id = :id")
    suspend fun get(id: Long): WakeSession?

    @Query("SELECT * FROM wake_sessions ORDER BY firedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 120): Flow<List<WakeSession>>

    @Query("SELECT * FROM wake_sessions ORDER BY firedAt DESC")
    suspend fun getAll(): List<WakeSession>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<WakeSession>)

    @Query("DELETE FROM wake_sessions")
    suspend fun deleteAll()
}

@Dao
interface CredDao {
    @Insert
    suspend fun insert(entry: CredEntry): Long

    @Query("SELECT COALESCE(SUM(amount), 0) FROM cred_log")
    fun observeTotal(): Flow<Int>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM cred_log")
    suspend fun total(): Int

    @Query("SELECT COALESCE(SUM(amount), 0) FROM cred_log WHERE sessionId = :sessionId")
    suspend fun sessionTotal(sessionId: Long): Int

    @Query("SELECT COUNT(*) FROM cred_log WHERE reason = :reason")
    suspend fun countReason(reason: String): Int

    @Query("SELECT reason FROM cred_log WHERE reason LIKE 'GIG:%'")
    fun observeGigs(): Flow<List<String>>

    @Query("SELECT * FROM cred_log ORDER BY at DESC")
    suspend fun getAll(): List<CredEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<CredEntry>)

    @Query("DELETE FROM cred_log")
    suspend fun deleteAll()
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

    @Query("DELETE FROM lock_windows WHERE packageName = :pkg")
    suspend fun delete(pkg: String)
}

@Database(entities = [Alarm::class, LockWindow::class, WakeSession::class, CredEntry::class], version = 4, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun lockDao(): LockDao
    abstract fun wakeDao(): WakeDao
    abstract fun credDao(): CredDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE lock_windows ADD COLUMN lockedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN awakeCheck INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN skipAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `wake_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`alarmId` INTEGER NOT NULL, `label` TEXT NOT NULL, `firedAt` INTEGER NOT NULL, " +
                        "`dismissedAt` INTEGER NOT NULL, `mistakes` INTEGER NOT NULL, `snoozes` INTEGER NOT NULL, " +
                        "`reRings` INTEGER NOT NULL, `checksPassed` INTEGER NOT NULL, `maxEscalation` INTEGER NOT NULL, " +
                        "`usedJackIn` INTEGER NOT NULL, `eddies` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cred_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`at` INTEGER NOT NULL, `amount` INTEGER NOT NULL, `reason` TEXT NOT NULL, `sessionId` INTEGER NOT NULL)",
                )
            }
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
    }
}
