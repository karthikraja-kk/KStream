package com.kstream.core.model

sealed class RefreshMediaResult {
    data object Queued : RefreshMediaResult()
    data object Processing : RefreshMediaResult()
    data object Done : RefreshMediaResult()
    data class Failed(val error: String) : RefreshMediaResult()
}
