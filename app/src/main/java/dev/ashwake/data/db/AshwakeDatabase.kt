package dev.ashwake.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.ashwake.data.db.dao.abstinence.AbstinenceDao
import dev.ashwake.data.db.dao.backup.BackupDao
import dev.ashwake.data.db.dao.blocking.BlockingDao
import dev.ashwake.data.db.dao.character.CharacterDao
import dev.ashwake.data.db.dao.habits.HabitDao
import dev.ashwake.data.db.dao.routines.FocusDao
import dev.ashwake.data.db.dao.ritual.RitualDao
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
import dev.ashwake.data.db.entity.blocking.BlockedAppEntity
import dev.ashwake.data.db.entity.blocking.BlockingRuleEntity
import dev.ashwake.data.db.entity.blocking.BypassLogEntity
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
import dev.ashwake.data.db.entity.ritual.DailyReviewEntity
import dev.ashwake.data.db.entity.ritual.DailyReviewTopTaskEntity
import dev.ashwake.data.db.entity.ritual.WeeklyReportEntity
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
 * Схема спроектирована сразу под всё ТЗ (docs/02-database.md), поэтому
 * версия менялась ровно один раз и по делу. Миграции ведутся с самого
 * начала: пересоздание базы допустимо только в debug-сборке, где терять
 * нечего, а в релизе каждая версия обязана иметь путь вперёд.
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
        TimeboxBlockEntity::class,
        // ритуал и аналитика
        DailyReviewEntity::class,
        DailyReviewTopTaskEntity::class,
        WeeklyReportEntity::class,
        // блокировка приложений
        BlockingRuleEntity::class,
        BlockedAppEntity::class,
        BypassLogEntity::class
    ],
    version = AshwakeDatabase.VERSION,
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
    abstract fun ritualDao(): RitualDao
    abstract fun blockingDao(): BlockingDao
    abstract fun backupDao(): BackupDao

    companion object {
        const val NAME = "ashwake.db"

        /**
         * Версия схемы. Константой, а не числом в аннотации: её сверяет тест
         * миграций с последней выгруженной схемой, и без имени сверять было
         * бы нечего.
         */
        const val VERSION = 4

        /**
         * 1 → 2: у якоря появился день последнего срабатывания.
         *
         * Первая настоящая миграция. Она же образец для следующих: схема
         * меняется только вперёд, данные не теряются, и в релизной сборке
         * никакого fallbackToDestructiveMigration быть не может — там за
         * строчкой кода стоит стёртая история человека.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habit_anchors ADD COLUMN lastFiredDate INTEGER")
            }
        }

        /** 2 → 3: у задачи появился значок-эмодзи. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN emoji TEXT")
            }
        }

        /**
         * 3 → 4: значок-картинка у задачи, привычки и отказа.
         * Хранится имя файла в каталоге приложения, а не URI галереи:
         * разрешение на чужой URI не переживает перезагрузку.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN iconPath TEXT")
                db.execSQL("ALTER TABLE habits ADD COLUMN iconPath TEXT")
                db.execSQL("ALTER TABLE abstinences ADD COLUMN iconPath TEXT")
            }
        }

        val MIGRATIONS: Array<Migration> =
            arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
    }
}
