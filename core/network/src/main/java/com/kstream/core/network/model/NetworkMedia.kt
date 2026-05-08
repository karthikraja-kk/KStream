package com.kstream.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NetworkMedia(
    @SerialName("id") val id: String, // Added from SQL schema (uuid)
    @SerialName("movie_id") val movieId: String, // From SQL schema (uuid)
    @SerialName("quality") val quality: String?, // Made nullable
    @SerialName("file_size") val fileSize: String?, // Made nullable
    @SerialName("download_url_1") val downloadUrl1: String? = null, // From SQL schema (text)
    @SerialName("download_url_2") val downloadUrl2: String? = null, // From SQL schema (text)
    @SerialName("watch_url_1") val watchUrl1: String? = null, // From SQL schema (text)
    @SerialName("watch_url_2") val watchUrl2: String? = null, // From SQL schema (text)
    // Apply the custom serializer for created_at and updated_at
    @Serializable(with = DateTimeAsLongSerializer::class)
    @SerialName("created_at") val createdAt: Long? = null, // Changed from Instant to Long
    @Serializable(with = DateTimeAsLongSerializer::class)
    @SerialName("updated_at") val updatedAt: Long? = null  // Changed from Instant to Long
)


