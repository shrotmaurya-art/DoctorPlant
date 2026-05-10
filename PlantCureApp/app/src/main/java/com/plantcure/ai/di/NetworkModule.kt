package com.plantcure.ai.di

import com.plantcure.ai.BuildConfig
import com.plantcure.ai.data.remote.AgmarknetApiService
import com.plantcure.ai.data.remote.ClaudeApiService
import com.plantcure.ai.data.remote.WeatherApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * Hilt module providing Retrofit API service instances.
 * Each external API gets its own Retrofit instance with appropriate base URL.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingLevel = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }

        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply { level = loggingLevel })
            .build()
    }

    @Provides
    @Singleton
    fun provideClaudeApiService(client: OkHttpClient): ClaudeApiService {
        return Retrofit.Builder()
            .baseUrl("https://api.anthropic.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ClaudeApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideWeatherApiService(client: OkHttpClient): WeatherApiService {
        return Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeatherApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideAgmarknetApiService(client: OkHttpClient): AgmarknetApiService {
        return Retrofit.Builder()
            .baseUrl("https://api.data.gov.in/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AgmarknetApiService::class.java)
    }
}
