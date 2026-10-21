package com.cyberalarm.di

import android.content.Context
import androidx.room.Room
import com.cyberalarm.data.AlarmDao
import com.cyberalarm.data.AppDatabase
import com.cyberalarm.data.LockDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** Process-lifetime scope for work that must outlive a screen or a receiver. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "cyber_alarm.db").build()

    @Provides
    fun alarmDao(db: AppDatabase): AlarmDao = db.alarmDao()

    @Provides
    fun lockDao(db: AppDatabase): LockDao = db.lockDao()

    @Provides
    @Singleton
    @AppScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
