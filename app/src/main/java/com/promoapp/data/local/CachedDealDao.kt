package com.promoapp.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CachedDealDao {
    @Query("SELECT * FROM cached_deals WHERE tab = :tab ORDER BY position ASC")
    fun pagingSource(tab: String): PagingSource<Int, CachedDealEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CachedDealEntity>)

    /** Força o próximo carregamento do feed a buscar na rede (usado depois de publicar uma oferta). */
    @Query("UPDATE cached_deals SET cachedAtMillis = 0")
    suspend fun markAllStale()

    @Query("DELETE FROM cached_deals WHERE tab = :tab")
    suspend fun clear(tab: String)

    @Query("SELECT COALESCE(MAX(position), -1) FROM cached_deals WHERE tab = :tab")
    suspend fun lastPosition(tab: String): Int

    @Query("SELECT MAX(cachedAtMillis) FROM cached_deals WHERE tab = :tab")
    suspend fun lastCachedAt(tab: String): Long?
}
