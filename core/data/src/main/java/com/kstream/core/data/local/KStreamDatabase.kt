package com.kstream.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kstream.core.data.local.dao.DownloadDao
import com.kstream.core.data.local.dao.LikedMovieDao
import com.kstream.core.data.local.dao.MovieDao
import com.kstream.core.data.local.dao.RecommendationDao
import com.kstream.core.data.local.dao.WatchProgressDao
import com.kstream.core.data.local.entities.DownloadEntity
import com.kstream.core.data.local.entities.LikedMovieEntity
import com.kstream.core.data.local.entities.MovieEntity
import com.kstream.core.data.local.entities.RecommendationEntity
import com.kstream.core.data.local.entities.WatchProgressEntity

@Database(
    entities = [
        MovieEntity::class,
        WatchProgressEntity::class,
        DownloadEntity::class,
        LikedMovieEntity::class,
        RecommendationEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class KStreamDatabase : RoomDatabase() {
    abstract fun movieDao(): MovieDao
    abstract fun watchProgressDao(): WatchProgressDao
    abstract fun downloadDao(): DownloadDao
    abstract fun likedMovieDao(): LikedMovieDao
    abstract fun recommendationDao(): RecommendationDao
}
