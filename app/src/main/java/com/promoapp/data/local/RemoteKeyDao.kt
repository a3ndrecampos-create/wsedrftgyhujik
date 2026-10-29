package com.promoapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RemoteKeyDao {
    @Query("SELECT * FROM remote_keys WHERE tab = :tab")
    suspend fun get(tab: String): RemoteKeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(key: RemoteKeyEntity)

    @Query("DELETE FROM remote_keys WHERE tab = :tab")
    suspend fun clear(tab: String)
}
