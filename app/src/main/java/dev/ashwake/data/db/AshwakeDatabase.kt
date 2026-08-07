package dev.ashwake.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.ashwake.data.db.dao.abstinence.AbstinenceDao
import dev.ashwake.data.db.dao.habits.HabitDao
import dev.ashwake.data.db.dao.tasks.ProjectDao
import dev.ashwake.data.db.dao.tasks.TagDao
import dev.ashwake.data.db.dao.tasks.TaskDao
import dev.ashwake.data.db.entity.abstinence.AbstinenceAttemptEntity
import dev.ashwake.data.db.entity.abstinence.AbstinenceEntity
import dev.ashwake.data.db.entity.abstinence.AbstinenceMilestoneEntity
import dev.ashwake.data.db.entity.abstinence.AbstinenceSubstituteEntity
import dev.ashwake.data.db.entity.abstinence.CravingEventEntity
import dev.ashwake.data.db.entity.abstinence.CravingTriggerEntity
import dev.ashwake.data.db.entity.abstinence.RelapseReasonEntity
import dev.ashwake.data.db.entity.habits.HabitAnchorEntity
import dev.ashwake.data.db.entity.habits.HabitEntity
import dev.ashwake.data.db.entity.habits.HabitEntryEntity
import dev.ashwake.data.db.entity.habits.HabitFreezeEntity
import dev.ashwake.data.db.entity.habits.HabitPauseEntity
import dev.ashwake.data.db.entity.habits.HabitSkipReasonEntity
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
        TaskPostponementEntity::class,
        // привычки
        HabitEntity::class,
        HabitEntryEntity::class,
        HabitSkipReasonEntity::class,
        HabitFreezeEntity::class,
        HabitPauseEntity::class,
        HabitAnchorEntity::class,
        // отказы
        AbstinenceEntity::class,
        AbstinenceAttemptEntity::class,
        AbstinenceMilestoneEntity::class,
        RelapseReasonEntity::class,
        CravingTriggerEntity::class,
        CravingEventEntity::class,
        AbstinenceSubstituteEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AshwakeDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun projectDao(): ProjectDao
    abstract fun tagDao(): TagDao
    abstract fun habitDao(): HabitDao
    abstract fun abstinenceDao(): AbstinenceDao

    companion object {
        const val NAME = "ashwake.db"
    }
}
