package com.kstream.core.domain.usecases

import com.kstream.core.domain.models.AppSettings

import com.kstream.core.domain.models.SettingsRepository

class UpdateAppSettingsUseCase(private val repository: SettingsRepository) {
    suspend operator fun invoke(settings: AppSettings) = repository.updateSettings(settings)
}
