package com.kstream.core.domain

import com.kstream.core.domain.repository.MovieRepository
import com.kstream.core.model.ScanTriggerResult
import javax.inject.Inject

class TriggerScanUseCase @Inject constructor(
    private val movieRepository: MovieRepository
) {
    suspend operator fun invoke(): ScanTriggerResult {
        return movieRepository.triggerScan()
    }
}
