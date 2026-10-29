package com.promoapp.di

import com.promoapp.data.repository.AlertRepositoryImpl
import com.promoapp.data.repository.CouponRepositoryImpl
import com.promoapp.data.repository.DealRepositoryImpl
import com.promoapp.data.repository.ProfileRepositoryImpl
import com.promoapp.data.repository.SettingsRepositoryImpl
import com.promoapp.domain.repository.AlertRepository
import com.promoapp.domain.repository.CouponRepository
import com.promoapp.domain.repository.DealRepository
import com.promoapp.domain.repository.ProfileRepository
import com.promoapp.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindDealRepository(impl: DealRepositoryImpl): DealRepository

    @Binds
    @Singleton
    abstract fun bindCouponRepository(impl: CouponRepositoryImpl): CouponRepository

    @Binds
    @Singleton
    abstract fun bindAlertRepository(impl: AlertRepositoryImpl): AlertRepository

    @Binds
    @Singleton
    abstract fun bindProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
