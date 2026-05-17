package com.kstream.core.domain

import com.kstream.core.domain.repository.MovieRepository
import com.kstream.core.model.ScanStatus
import javax.inject.Inject

class GetScanStatusUseCase @Inject constructor(
    private val movieRepository: MovieRepository
) {
    suspend operator fun invoke(): ScanStatus {
        return movieRepository.getScanStatus()
    }
}
