package com.zerostudio.cloudreve.core.database.di

import androidx.room.Room
import androidx.room.migration.Migration
import com.zerostudio.cloudreve.core.database.CloudreveDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val databaseModule = module {
    single {
        Room.databaseBuilder(
            androidContext(),
            CloudreveDatabase::class.java,
            "cloudreve.db",
        )
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
            )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }
    single { get<CloudreveDatabase>().fileDao() }
    single { get<CloudreveDatabase>().transferDao() }
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `transfers` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `direction` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `progress` REAL NOT NULL,
                `transferredBytes` INTEGER NOT NULL,
                `totalBytes` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `transfers` ADD COLUMN `sourceUri` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `transfers` ADD COLUMN `targetUri` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `transfers` ADD COLUMN `isDirectory` INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `files_new` (
                `instanceId` TEXT NOT NULL,
                `uri` TEXT NOT NULL,
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `parentUri` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `size` INTEGER NOT NULL,
                `mimeType` TEXT,
                `updatedAtEpochMillis` INTEGER NOT NULL,
                `isFavorite` INTEGER NOT NULL,
                `isOffline` INTEGER NOT NULL,
                PRIMARY KEY(`instanceId`, `uri`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `files_new` (
                `instanceId`,
                `uri`,
                `id`,
                `name`,
                `parentUri`,
                `type`,
                `size`,
                `mimeType`,
                `updatedAtEpochMillis`,
                `isFavorite`,
                `isOffline`
            )
            SELECT
                '',
                `uri`,
                `id`,
                `name`,
                `parentUri`,
                `type`,
                `size`,
                `mimeType`,
                `updatedAtEpochMillis`,
                `isFavorite`,
                `isOffline`
            FROM `files`
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE `files`")
        db.execSQL("ALTER TABLE `files_new` RENAME TO `files`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_files_instanceId_parentUri` ON `files` (`instanceId`, `parentUri`)")
    }
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_files_instanceId_parentUri_type_name`
            ON `files` (`instanceId`, `parentUri`, `type`, `name`)
            """.trimIndent(),
        )
    }
}

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `files` ADD COLUMN `thumbnailUrl` TEXT")
    }
}

private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_files_instanceId_type_updatedAtEpochMillis`
            ON `files` (`instanceId`, `type`, `updatedAtEpochMillis`)
            """.trimIndent(),
        )
    }
}
