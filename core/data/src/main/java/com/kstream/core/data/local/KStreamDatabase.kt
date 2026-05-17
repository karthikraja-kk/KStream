package com.kstream.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 6,
    exportSchema = false
)
abstract class KStreamDatabase : RoomDatabase() {
    abstract fun movieDao(): MovieDao
    abstract fun watchProgressDao(): WatchProgressDao
    abstract fun downloadDao(): DownloadDao
    abstract fun likedMovieDao(): LikedMovieDao
    abstract fun recommendationDao(): RecommendationDao

    companion object {
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE movies_cache ADD COLUMN movieUrl TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_movies_cache_movieName ON movies_cache(movieName)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_movies_cache_genres ON movies_cache(genres)")
            }
        }
    }
}
