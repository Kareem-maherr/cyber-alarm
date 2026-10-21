package com.cyberalarm.data

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmRepository @Inject constructor(private val dao: AlarmDao) {
    fun observeAll(): Flow<List<Alarm>> = dao.observeAll()

    suspend fun getAll(): List<Alarm> = dao.getAll()

    suspend fun get(id: Long): Alarm? = dao.get(id)

    /** Inserts or replaces; returns the alarm with its persisted id. */
    suspend fun save(alarm: Alarm): Alarm {
        val id = dao.insert(alarm)
        return alarm.copy(id = id)
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)

    suspend fun delete(id: Long) = dao.delete(id)
}
