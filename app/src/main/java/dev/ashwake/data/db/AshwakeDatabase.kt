package dev.ashwake.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.ashwake.data.db.dao.abstinence.AbstinenceDao
import dev.ashwake.data.db.dao.character.CharacterDao
import dev.ashwake.data.db.dao.habits.HabitDao
import dev.ashwake.data.db.dao.routines.FocusDao
import dev.ashwake.data.db.dao.routines.RoutineDao
import dev.ashwake.data.db.dao.timebox.TimeboxDao
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
import dev.ashwake.data.db.entity.character.AppearancePresetEntity
import dev.ashwake.data.db.entity.character.AppearancePresetItemEntity
import dev.ashwake.data.db.entity.character.CharacterProfileEntity
import dev.ashwake.data.db.entity.character.CharacterStatEntity
import dev.ashwake.data.db.entity.character.EquippedItemEntity
import dev.ashwake.data.db.entity.character.LedgerTransactionEntity
import dev.ashwake.data.db.entity.character.OwnedItemEntity
import dev.ashwake.data.db.entity.character.StatEventEntity
import dev.ashwake.data.db.entity.character.UserRewardEntity
import dev.ashwake.data.db.entity.character.UserRewardRedemptionEntity
import dev.ashwake.data.db.entity.character.WalletEntity
import dev.ashwake.data.db.entity.habits.HabitAnchorEntity
import dev.ashwake.data.db.entity.routines.FocusSessionEntity
import dev.ashwake.data.db.entity.routines.RoutineEntity
import dev.ashwake.data.db.entity.routines.RoutineSessionEntity
import dev.ashwake.data.db.entity.routines.RoutineSessionStepEntity
import dev.ashwake.data.db.entity.routines.RoutineStepEntity
import dev.ashwake.data.db.entity.habits.HabitEntity
import dev.ashwake.data.db.entity.habits.HabitEntryEntity
import dev.ashwake.data.db.entity.habits.HabitFreezeEntity
import dev.ashwake.data.db.entity.habits.HabitPauseEntity
import dev.ashwake.data.db.entity.habits.HabitSkipReasonEntity
import dev.ashwake.data.db.entity.timebox.TimeboxBlockEntity
import dev.ashwake.data.db.entity.timebox.TimeboxDayEntity
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
        AbstinenceSubstituteEntity::class,
        // персонаж и экономика
        CharacterProfileEntity::class,
        OwnedItemEntity::class,
        EquippedItemEntity::class,
        AppearancePresetEntity::class,
        AppearancePresetItemEntity::class,
        CharacterStatEntity::class,
        StatEventEntity::class,
        WalletEntity::class,
        LedgerTransactionEntity::class,
        UserRewardEntity::class,
        UserRewardRedemptionEntity::class,
        // рутины и фокус
        RoutineEntity::class,
        RoutineStepEntity::class,
        RoutineSessionEntity::class,
        RoutineSessionStepEntity::class,
        FocusSessionEntity::class,
        // таймбоксинг
        TimeboxDayEntity::class,
        TimeboxBlockEntity::class
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
    abstract fun characterDao(): CharacterDao
    abstract fun routineDao(): RoutineDao
    abstract fun focusDao(): FocusDao
    abstract fun timeboxDao(): TimeboxDao

    companion object {
        const val NAME = "ashwake.db"
    }
}
