package com.kstream.core.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class KStreamDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val USERNAME = stringPreferencesKey("username")
    private val FIRST_LAUNCH_COMPLETED = booleanPreferencesKey("first_launch_completed")
    private val WIFI_ONLY_DOWNLOAD = booleanPreferencesKey("wifi_only_download")

    val username: Flow<String> = context.dataStore.data.map { it[USERNAME] ?: "Guest" }
    val isFirstLaunchCompleted: Flow<Boolean> = context.dataStore.data.map { it[FIRST_LAUNCH_COMPLETED] ?: false }
    val isWifiOnlyDownload: Flow<Boolean> = context.dataStore.data.map { it[WIFI_ONLY_DOWNLOAD] ?: true }

    suspend fun setUsername(name: String) {
        context.dataStore.edit { it[USERNAME] = name }
    }

    suspend fun setFirstLaunchCompleted(completed: Boolean) {
        context.dataStore.edit { it[FIRST_LAUNCH_COMPLETED] = completed }
    }

    suspend fun setWifiOnlyDownload(wifiOnly: Boolean) {
        context.dataStore.edit { it[WIFI_ONLY_DOWNLOAD] = wifiOnly }
    }
}
