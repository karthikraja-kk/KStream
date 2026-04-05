package com.kstream.core.data.di

import com.kstream.core.data.datastore.DataStoreManager
import com.kstream.core.network.UrlProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkProviderModule {

    @Provides
    @Singleton
    fun provideUrlProvider(dataStoreManager: DataStoreManager): UrlProvider {
        return object : UrlProvider {
            override fun getBaseUrl(): String {
                return runBlocking {
                    dataStoreManager.settingsFlow.first().workerUrl
                }
            }
        }
    }
}
