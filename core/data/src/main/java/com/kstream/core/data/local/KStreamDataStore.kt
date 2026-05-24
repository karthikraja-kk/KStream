package com.kstream.core.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class KStreamDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val RECENT_SEARCH_DELIMITER = "||"
        private const val MAX_RECENT_SEARCHES = 10
    }

    private val USERNAME = stringPreferencesKey("username")
    private val FIRST_LAUNCH_COMPLETED = booleanPreferencesKey("first_launch_completed")
    private val WIFI_ONLY_DOWNLOAD = booleanPreferencesKey("wifi_only_download")
    private val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
    private val DOWNLOAD_LOCATION = stringPreferencesKey("download_location")
    private val DOWNLOAD_LOCATION_URI = stringPreferencesKey("download_location_uri")
    private val POSTER_BASE_URL = stringPreferencesKey("poster_base_url")
    private val HD_ONLY_FILTER = booleanPreferencesKey("hd_only_filter")

    val username: Flow<String> = context.dataStore.data.map { it[USERNAME] ?: "Guest" }
    val isFirstLaunchCompleted: Flow<Boolean> = context.dataStore.data.map { it[FIRST_LAUNCH_COMPLETED] ?: false }
    val isWifiOnlyDownload: Flow<Boolean> = context.dataStore.data.map { it[WIFI_ONLY_DOWNLOAD] ?: true }
    val downloadLocation: Flow<String> = context.dataStore.data.map { it[DOWNLOAD_LOCATION] ?: "Internal Storage" }
    val downloadLocationUri: Flow<String> = context.dataStore.data.map { it[DOWNLOAD_LOCATION_URI] ?: "" }
    val posterBaseUrl: Flow<String> = context.dataStore.data.map { it[POSTER_BASE_URL] ?: "" }
    val isHdOnlyFilter: Flow<Boolean> = context.dataStore.data.map { it[HD_ONLY_FILTER] ?: false }
    val recentSearches: Flow<List<String>> = context.dataStore.data.map {
        decodeRecentSearches(it[RECENT_SEARCHES])
    }

    suspend fun setUsername(name: String) {
        context.dataStore.edit { it[USERNAME] = name }
    }

    suspend fun setDownloadLocation(location: String) {
        context.dataStore.edit { it[DOWNLOAD_LOCATION] = location }
    }

    suspend fun setDownloadLocationUri(uri: String) {
        context.dataStore.edit { it[DOWNLOAD_LOCATION_URI] = uri }
    }

    suspend fun setPosterBaseUrl(url: String) {
        context.dataStore.edit { it[POSTER_BASE_URL] = url }
    }

    suspend fun getPosterBaseUrlSync(): String {
        return context.dataStore.data.map { it[POSTER_BASE_URL] ?: "" }.first()
    }

    suspend fun setFirstLaunchCompleted(completed: Boolean) {
        context.dataStore.edit { it[FIRST_LAUNCH_COMPLETED] = completed }
    }

    suspend fun setWifiOnlyDownload(wifiOnly: Boolean) {
        context.dataStore.edit { it[WIFI_ONLY_DOWNLOAD] = wifiOnly }
    }

    suspend fun setHdOnlyFilter(hdOnly: Boolean) {
        context.dataStore.edit { it[HD_ONLY_FILTER] = hdOnly }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }

    suspend fun clearRecentSearches() {
        context.dataStore.edit { it.remove(RECENT_SEARCHES) }
    }

    suspend fun addRecentSearch(query: String) {
        val normalized = query.trim()
        if (normalized.isEmpty()) return

        context.dataStore.edit { prefs ->
            val updated = decodeRecentSearches(prefs[RECENT_SEARCHES])
                .filterNot { it.equals(normalized, ignoreCase = true) }
                .toMutableList()
                .apply { add(0, normalized) }
                .take(MAX_RECENT_SEARCHES)
            prefs[RECENT_SEARCHES] = encodeRecentSearches(updated)
        }
    }

    private fun decodeRecentSearches(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return try {
            val jsonArray = org.json.JSONArray(value)
            (0 until jsonArray.length()).map { jsonArray.getString(it) }
        } catch (_: Exception) {
            // Backwards-compatible: migrate from old || delimiter format
            value.split(RECENT_SEARCH_DELIMITER).filter { it.isNotBlank() }
        }
    }

    private fun encodeRecentSearches(values: List<String>): String {
        return org.json.JSONArray(values).toString()
    }
}
