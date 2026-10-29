package com.promoapp.di

import android.content.Context
import androidx.room.Room
import com.promoapp.data.local.AlertDao
import com.promoapp.data.local.AppDatabase
import com.promoapp.data.local.FavoriteDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase =
        Room
            .databaseBuilder(context, AppDatabase::class.java, "promoapp.db")
            // As versões 1 e 2 nunca foram publicadas. A partir do primeiro release, use Migrations
            // reais (os schemas exportados ficam em app/schemas) em vez de recriar o banco.
            .fallbackToDestructiveMigrationFrom(1, 2)
            .build()

    @Provides
    fun provideFavoriteDao(db: AppDatabase): FavoriteDao = db.favoriteDao()

    @Provides
    fun provideAlertDao(db: AppDatabase): AlertDao = db.alertDao()
}
