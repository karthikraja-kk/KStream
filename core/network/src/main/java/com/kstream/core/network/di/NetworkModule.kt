package com.kstream.core.network.di

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
            supabaseUrl = "https://your-project.supabase.co", // Replace with real URL
            supabaseKey = "your-supabase-key" // Replace with real key
        ) {
            install(Postgrest)
            install(Storage)
        }
    }

    @Provides
    @Singleton
    fun provideNetworkDataSource(
        client: SupabaseClient
    ): KStreamNetworkDataSource = SupabaseKStreamNetworkDataSource(client)
}
