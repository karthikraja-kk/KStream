package com.kstream.core.data.repositories

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kstream.core.data.db.daos.MovieDao
import com.kstream.core.data.db.entities.MovieEntity
import com.kstream.core.domain.models.MovieDetail
import com.kstream.core.domain.models.QualityOption
import com.kstream.core.network.responses.MovieDetailResponse

fun MovieDetailResponse.toEntity(path: String, gson: Gson): MovieEntity {
    return MovieEntity(
        path = path,
        title = title,
        year = year ?: "",
        synopsis = synopsis ?: "",
        genre = genre ?: "",
        castJson = gson.toJson(cast?.split(",")?.map { it.trim() } ?: emptyList<String>()),
        rating = rating ?: "",
        duration = duration ?: "",
        qualityFoldersJson = gson.toJson(qualityFolders ?: emptyList<Any>()),
        server1Url = server1Url,
        server2Url = server2Url,
        watchServer1Url = watchServer1Url,
        watchServer2Url = watchServer2Url
    )
}

fun MovieEntity.toDomain(gson: Gson): MovieDetail {
    val castType = object : TypeToken<List<String>>() {}.type
    val qualityType = object : TypeToken<List<QualityOption>>() {}.type
    
    return MovieDetail(
        path = path,
        title = title,
        year = year,
        synopsis = synopsis,
        genre = genre,
        cast = gson.fromJson(castJson, castType),
        rating = rating,
        duration = duration,
        qualities = gson.fromJson(qualityFoldersJson, qualityType),
        posterUrl = null // Poster handled separately
    )
}
