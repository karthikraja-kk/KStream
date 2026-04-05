package com.kstream.core.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.kstream.core.domain.models.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class DataStoreManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val USER_NAME = stringPreferencesKey("user_name")
    private val BASE_URL = stringPreferencesKey("base_url")
    private val WORKER_URL = stringPreferencesKey("worker_url")
    private val BACKUP_WORKER_URL = stringPreferencesKey("backup_worker_url")
    private val QUALITY_PREFERENCE = stringPreferencesKey("quality_preference")
    private val VOLUME = intPreferencesKey("volume")

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        AppSettings(
            userName = preferences[USER_NAME] ?: "",
            baseUrl = preferences[BASE_URL] ?: "",
            workerUrl = preferences[WORKER_URL] ?: "",
            backupWorkerUrl = preferences[BACKUP_WORKER_URL] ?: "",
            qualityPreference = preferences[QUALITY_PREFERENCE] ?: "Auto",
            volume = preferences[VOLUME] ?: 100
        )
    }

    suspend fun updateSettings(settings: AppSettings) {
        context.dataStore.edit { preferences ->
            preferences[USER_NAME] = settings.userName
            preferences[BASE_URL] = settings.baseUrl
            preferences[WORKER_URL] = settings.workerUrl
            preferences[BACKUP_WORKER_URL] = settings.backupWorkerUrl
            preferences[QUALITY_PREFERENCE] = settings.qualityPreference
            preferences[VOLUME] = settings.volume
        }
    }
}
