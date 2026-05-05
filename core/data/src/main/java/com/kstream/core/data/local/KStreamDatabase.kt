package com.kstream.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kstream.core.data.local.dao.DownloadDao
import com.kstream.core.data.local.dao.MovieDao
import com.kstream.core.data.local.dao.WatchProgressDao
import com.kstream.core.data.local.entities.DownloadEntity
import com.kstream.core.data.local.entities.MovieEntity
import com.kstream.core.data.local.entities.WatchProgressEntity

@Database(
    entities = [
        MovieEntity::class,
        WatchProgressEntity::class,
        DownloadEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class KStreamDatabase : RoomDatabase() {
    abstract fun movieDao(): MovieDao
    abstract fun watchProgressDao(): WatchProgressDao
    abstract fun downloadDao(): DownloadDao
}
