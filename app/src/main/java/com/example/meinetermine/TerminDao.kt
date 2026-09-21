package com.example.meinetermine

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import androidx.room.Transaction

@Dao
interface TerminDao {

    @Query("SELECT * FROM termine ORDER BY scheduledAtMillis, id")
    suspend fun getAll(): List<TerminEntity>

    @Query("SELECT * FROM termine ORDER BY scheduledAtMillis, id")
    fun observeAll(): Flow<List<TerminEntity>>

    @Query("SELECT * FROM termine WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TerminEntity?

    @Insert
    suspend fun insert(termin: TerminEntity): Long

    @Delete
    suspend fun delete(termin: TerminEntity)

    @Update
    suspend fun update(termin: TerminEntity)

    @Query("UPDATE termine SET scheduledAtMillis = :scheduledAtMillis WHERE id = :id")
    suspend fun updateScheduledAt(id: Long, scheduledAtMillis: Long)

    @Query("SELECT * FROM termine WHERE scheduledAtMillis = 0")
    suspend fun getLegacyTermine(): List<TerminEntity>

    @Query("DELETE FROM termine")
    suspend fun deleteAll()

    @Insert
    suspend fun insertAll(termine: List<TerminEntity>)

    @Transaction
    suspend fun replaceAll(termine: List<TerminEntity>) {
        deleteAll()
        insertAll(termine)
    }

    @Query("""
    SELECT * FROM termine
    WHERE date = :date
    AND id != :excludeId
""")
    suspend fun getTermineForDate(
        date: String,
        excludeId: Long
    ): List<TerminEntity>
}
