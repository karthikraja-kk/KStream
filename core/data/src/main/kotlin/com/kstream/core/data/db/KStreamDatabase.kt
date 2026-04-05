package com.kstream.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kstream.core.data.db.daos.*
import com.kstream.core.data.db.entities.*

@Database(
    entities = [
        YearEntity::class,
        MovieEntity::class,
        PosterEntity::class,
        ContinueWatchingEntity::class,
        RecentlyVisitedEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class KStreamDatabase : RoomDatabase() {
    abstract fun movieDao(): MovieDao
    abstract fun yearDao(): YearDao
    abstract fun posterDao(): PosterDao
    abstract fun watchDao(): WatchDao
}
