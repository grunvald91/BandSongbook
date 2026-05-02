package com.fithealthzone.bandsongbook.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SongEntity::class, SongAudioEntity::class, SetlistEntity::class, SetlistItemEntity::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun songAudioDao(): SongAudioDao
    abstract fun setlistDao(): SetlistDao
    abstract fun setlistItemDao(): SetlistItemDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN autoScrollSpeed REAL NOT NULL DEFAULT 1.0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE song_audio ADD COLUMN remoteUrl TEXT")
                db.execSQL("ALTER TABLE song_audio ADD COLUMN objectKey TEXT")
                db.execSQL("ALTER TABLE song_audio ADD COLUMN contentHash TEXT")
                db.execSQL("ALTER TABLE song_audio ADD COLUMN sizeBytes INTEGER")
                db.execSQL("ALTER TABLE song_audio ADD COLUMN uploadedBy TEXT")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN createdBy TEXT")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE song_audio ADD COLUMN mimeType TEXT")
                db.execSQL("ALTER TABLE song_audio ADD COLUMN durationMs INTEGER")
                db.execSQL("ALTER TABLE song_audio ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE setlists ADD COLUMN deletedAt INTEGER")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `setlist_items_new` (
                        `id` TEXT NOT NULL,
                        `setlistId` TEXT NOT NULL,
                        `songId` TEXT NOT NULL,
                        `orderIndex` INTEGER NOT NULL,
                        `transposeOverride` INTEGER,
                        `updatedAt` INTEGER NOT NULL,
                        `deletedAt` INTEGER,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`setlistId`) REFERENCES `setlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`songId`) REFERENCES `songs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `setlist_items_new` (`id`, `setlistId`, `songId`, `orderIndex`, `transposeOverride`, `updatedAt`, `deletedAt`)
                    SELECT `id`, `setlistId`, `songId`, `orderIndex`, `transposeOverride`, CAST(strftime('%s','now') AS INTEGER) * 1000, NULL
                    FROM `setlist_items`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `setlist_items`")
                db.execSQL("ALTER TABLE `setlist_items_new` RENAME TO `setlist_items`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_setlist_items_setlistId` ON `setlist_items` (`setlistId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_setlist_items_songId` ON `setlist_items` (`songId`)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN preferFlats INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "band_songbook.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
