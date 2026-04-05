package com.kstream.core.data.repositories

import com.kstream.core.data.datastore.DataStoreManager
import com.kstream.core.domain.models.AppSettings
import com.kstream.core.domain.models.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStoreManager: DataStoreManager
) : SettingsRepository {

    override fun getSettings(): Flow<AppSettings> = dataStoreManager.settingsFlow

    override suspend fun updateSettings(settings: AppSettings) {
        dataStoreManager.updateSettings(settings)
    }
}
