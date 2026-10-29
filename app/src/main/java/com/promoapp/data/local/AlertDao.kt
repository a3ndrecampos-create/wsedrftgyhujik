package com.promoapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {
    @Query("SELECT * FROM price_alerts ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<AlertEntity>>

    @Query("SELECT * FROM price_alerts WHERE dealId = :dealId")
    fun observe(dealId: Long): Flow<AlertEntity?>

    @Query("SELECT * FROM price_alerts WHERE triggeredAtMillis IS NULL")
    suspend fun getActive(): List<AlertEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AlertEntity)

    @Query("DELETE FROM price_alerts WHERE dealId = :dealId")
    suspend fun delete(dealId: Long)
}
