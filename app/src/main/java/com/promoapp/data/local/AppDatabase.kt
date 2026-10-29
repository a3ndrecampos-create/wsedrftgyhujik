package com.promoapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        FavoriteEntity::class,
        CachedDealEntity::class,
        RemoteKeyEntity::class,
        AlertEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao

    abstract fun cachedDealDao(): CachedDealDao

    abstract fun remoteKeyDao(): RemoteKeyDao

    abstract fun alertDao(): AlertDao
}
