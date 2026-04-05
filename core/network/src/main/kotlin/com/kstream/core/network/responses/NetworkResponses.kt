package com.kstream.core.network.responses

import com.google.gson.annotations.SerializedName

data class PingResponse(
    val ok: Boolean,
    val status: Int?
)

data class FolderResponse(
    val folders: List<FolderItem>
)

data class FolderItem(
    val name: String,
    val href: String
)

data class PaginatedResponse(
    val folders: List<FolderItem>,
    @SerializedName("totalPages") val totalPages: Int,
    @SerializedName("hasNextPage") val hasNextPage: Boolean
)

data class MovieDetailResponse(
    val title: String,
    val poster: String?,
    val synopsis: String?,
    val genre: String?,
    val cast: String?, // Sometimes cast comes as string "a, b, c"
    val rating: String?,
    val year: String?,
    val duration: String?,
    val qualityFolders: List<QualityFolder>?,
    val server1Url: String?,
    val server2Url: String?,
    val watchServer1Url: String?,
    val watchServer2Url: String?
)

data class QualityFolder(
    val name: String,
    val label: String,
    val href: String
)

data class PosterResponse(
    val posterUrl: String?
)

data class ErrorResponse(
    val error: String,
    val ok: Boolean
)
