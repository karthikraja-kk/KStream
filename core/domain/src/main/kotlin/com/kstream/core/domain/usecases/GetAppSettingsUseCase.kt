package com.kstream.core.domain.usecases

import com.kstream.core.domain.models.AppSettings
import kotlinx.coroutines.flow.Flow

import com.kstream.core.domain.models.SettingsRepository

class GetAppSettingsUseCase(private val repository: SettingsRepository) {
    operator fun invoke(): Flow<AppSettings> = repository.getSettings()
}
