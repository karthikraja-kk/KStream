package com.kstream.core.enrichment.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [MovieEnrichmentEntity::class],
    version = 1,
    exportSchema = false
)
abstract class EnrichmentDatabase : RoomDatabase() {
    abstract fun enrichmentDao(): MovieEnrichmentDao

    companion object {
        const val NAME = "enrichment.db"
    }
}
