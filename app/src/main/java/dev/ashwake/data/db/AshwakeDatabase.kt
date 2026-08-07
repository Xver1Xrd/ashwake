package dev.ashwake.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.ashwake.data.db.dao.tasks.ProjectDao
import dev.ashwake.data.db.dao.tasks.TagDao
import dev.ashwake.data.db.dao.tasks.TaskDao
import dev.ashwake.data.db.entity.tasks.ProjectEntity
import dev.ashwake.data.db.entity.tasks.RecurrenceRuleEntity
import dev.ashwake.data.db.entity.tasks.TagEntity
import dev.ashwake.data.db.entity.tasks.TaskEntity
import dev.ashwake.data.db.entity.tasks.TaskPostponementEntity
import dev.ashwake.data.db.entity.tasks.TaskTagCrossRef

/**
 * Единая база приложения.
 *
 * Схема спроектирована сразу под всё ТЗ (docs/02-database.md), но сущности
 * подключаются по мере реализации фич. До версии 1.0 версия базы остаётся 1,
 * а изменения схемы разработчику достаются пересозданием базы —
 * миграции пишутся начиная с первого релиза.
 */
@Database(
    entities = [
        // задачи
        ProjectEntity::class,
        TagEntity::class,
        TaskEntity::class,
        TaskTagCrossRef::class,
        RecurrenceRuleEntity::class,
        TaskPostponementEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AshwakeDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun projectDao(): ProjectDao
    abstract fun tagDao(): TagDao

    companion object {
        const val NAME = "ashwake.db"
    }
}
