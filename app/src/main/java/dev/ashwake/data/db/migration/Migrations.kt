package dev.ashwake.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграции базы. По одному объекту на шаг — так каждая правка схемы
 * ревьюится по диффу, а не на слово.
 *
 * В debug старые базы до версии 2 пересоздаются (fallback в DatabaseModule),
 * потому что pre-release схема могла отличаться от версии 1 бесконтрольно.
 * В release этот путь запрещён: там только миграции.
 */
object Migrations {

    /**
     * 1 → 2: достижения, материалы улучшений и ежедневный сундук (этап 12).
     * Три новые таблицы, ни одна существующая не меняется.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `material_inventory` (
                    `materialId` TEXT NOT NULL,
                    `amount` INTEGER NOT NULL,
                    PRIMARY KEY(`materialId`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `achievements` (
                    `id` TEXT NOT NULL,
                    `unlockedAt` INTEGER,
                    `progress` REAL NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `daily_chests` (
                    `date` INTEGER NOT NULL,
                    `openedAt` INTEGER,
                    `rerollsUsed` INTEGER NOT NULL,
                    `rewardJson` TEXT,
                    PRIMARY KEY(`date`)
                )
                """.trimIndent()
            )
        }
    }
}
