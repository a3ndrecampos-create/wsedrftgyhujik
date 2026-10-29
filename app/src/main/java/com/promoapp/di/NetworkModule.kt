package com.promoapp.di

import com.promoapp.BuildConfig
import com.promoapp.data.remote.CouponApi
import com.promoapp.data.remote.DealApi
import com.promoapp.data.remote.FakeCouponApi
import com.promoapp.data.remote.FakeDealApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides @Singleton
    fun provideOkHttp(): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level =
                        if (BuildConfig.DEBUG) {
                            HttpLoggingInterceptor.Level.BASIC
                        } else {
                            HttpLoggingInterceptor.Level.NONE
                        }
                },
            ).build()

    @Provides @Singleton
    fun provideRetrofit(
        client: OkHttpClient,
        json: Json,
    ): Retrofit =
        Retrofit
            .Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides @Singleton
    fun provideDealApi(retrofit: Retrofit): DealApi = if (BuildConfig.USE_FAKE_API) FakeDealApi() else retrofit.create(DealApi::class.java)

    @Provides
    @Singleton
    fun provideCouponApi(retrofit: Retrofit): CouponApi =
        if (BuildConfig.USE_FAKE_API) FakeCouponApi() else retrofit.create(CouponApi::class.java)
}
