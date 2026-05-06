package com.kstream.core.network.di

import com.kstream.core.network.BuildConfig
import com.kstream.core.network.KStreamNetworkDataSource
import com.kstream.core.network.SupabaseKStreamNetworkDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient {
        return createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_KEY
        ) {
            install(Postgrest)
            install(Storage)
            // Explicitly set the engine if needed, though Ktor should find it.
            // The issue might be related to how Ktor initializes on Android.
        }
    }

    @Provides
    @Singleton
    fun provideNetworkDataSource(
        client: SupabaseClient
    ): KStreamNetworkDataSource = SupabaseKStreamNetworkDataSource(client)
}
