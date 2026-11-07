package com.gympad.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasurementDao {
    @Query("SELECT * FROM measurement ORDER BY epochDay")
    fun observeAll(): Flow<List<Measurement>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(measurement: Measurement): Long

    @Query("DELETE FROM measurement WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM measurement") suspend fun all(): List<Measurement>
    @Insert suspend fun insertAll(items: List<Measurement>)
    @Query("DELETE FROM measurement") suspend fun clear()
}
