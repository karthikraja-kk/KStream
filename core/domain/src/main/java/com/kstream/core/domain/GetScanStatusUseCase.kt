package com.kstream.core.domain

import com.kstream.core.domain.repository.MovieRepository
import com.kstream.core.model.ScanStatusInfo
import javax.inject.Inject

class GetScanStatusUseCase @Inject constructor(
    private val movieRepository: MovieRepository
) {
    suspend operator fun invoke(): ScanStatusInfo {
        return movieRepository.getScanStatus()
    }
}
