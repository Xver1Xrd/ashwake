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
import dev.ashwake.data.db.dao.tasks.TaskAttachmentDao
import dev.ashwake.data.db.dao.online.SyncDao
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
import dev.ashwake.data.db.entity.character.MaterialInventoryEntity
import dev.ashwake.data.db.entity.character.AchievementEntity
import dev.ashwake.data.db.entity.character.DailyChestEntity
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
import dev.ashwake.data.db.entity.tasks.TaskAttachmentEntity
import dev.ashwake.data.db.entity.tasks.TaskPostponementEntity
import dev.ashwake.data.db.entity.tasks.TaskTagCrossRef
import dev.ashwake.data.db.entity.online.SyncConflictEntity
import dev.ashwake.data.db.entity.online.SyncStateEntity
import dev.ashwake.data.db.entity.online.SyncTombstoneEntity

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
        TaskAttachmentEntity::class,
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
        MaterialInventoryEntity::class,
        AchievementEntity::class,
        DailyChestEntity::class,
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
        BypassLogEntity::class,
        SyncTombstoneEntity::class,
        SyncStateEntity::class,
        SyncConflictEntity::class
    ],
    version = AshwakeDatabase.VERSION,
    exportSchema = true
)
abstract class AshwakeDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun taskAttachmentDao(): TaskAttachmentDao
    abstract fun syncDao(): SyncDao
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
        const val VERSION = 9

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

        /**
         * 4 → 5: экономика персонажа — материалы улучшений, достижения
         * и ежедневный сундук (п. 4).
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS material_inventory (" +
                        "materialId TEXT NOT NULL PRIMARY KEY, amount INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS achievements (" +
                        "id TEXT NOT NULL PRIMARY KEY, unlockedAt INTEGER, progress REAL NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS daily_chests (" +
                        "date INTEGER NOT NULL PRIMARY KEY, openedAt INTEGER, " +
                        "rerollsUsed INTEGER NOT NULL, rewardJson TEXT)"
                )
            }
        }

        /** 5 → 6: сложность задачи. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN difficulty INTEGER NOT NULL DEFAULT 1")
            }
        }

        /** 6 → 7: локальные фото и файлы в закрытом хранилище приложения. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS task_attachments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        taskId INTEGER NOT NULL,
                        fileName TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        mimeType TEXT NOT NULL,
                        sizeBytes INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(taskId) REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_task_attachments_taskId ON task_attachments(taskId)")
            }
        }

        /** 7 → 8: приватная синхронизация, пространства друзей и пар. */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // До 1.2.0 существовали две опубликованные схемы с номером 7:
                // одна уже содержала онлайн-поля, другая — только локальные
                // вложения. Проверяем реальные столбцы, а не предполагаем
                // историю версии — иначе обновление падало с duplicate column.
                fun hasColumn(table: String, column: String): Boolean =
                    db.query("PRAGMA table_info($table)").use { cursor ->
                        val nameIndex = cursor.getColumnIndex("name")
                        generateSequence { if (cursor.moveToNext()) cursor else null }
                            .any { it.getString(nameIndex) == column }
                    }
                fun addTaskColumn(column: String, type: String) {
                    if (!hasColumn("tasks", column)) db.execSQL("ALTER TABLE tasks ADD COLUMN $column $type")
                }

                addTaskColumn("globalId", "TEXT")
                addTaskColumn("spaceId", "TEXT")
                addTaskColumn("syncRevision", "INTEGER NOT NULL DEFAULT 0")
                addTaskColumn("syncState", "TEXT NOT NULL DEFAULT 'LOCAL'")
                // В одной из выпущенных схем с номером 7 отсутствовало поле
                // сложности, хотя последовательная ветка 5→6 его уже имела.
                addTaskColumn("difficulty", "INTEGER NOT NULL DEFAULT 1")
                // Прежняя версия вложений называла файл fileName, а ранняя
                // онлайн-схема уже содержала storedName и лишний unique-index.
                // ALTER TABLE оставляет default и индексы от старой схемы, из-за
                // чего Room отклоняет базу при запуске. Пересобираем только эту
                // таблицу и переносим каждую запись, не теряя файлы.
                val hasAttachments = db.query(
                    "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'task_attachments'"
                ).use { it.moveToFirst() }
                if (hasAttachments) {
                    val sourceName = if (hasColumn("task_attachments", "storedName")) "storedName" else "fileName"
                    val sourceKind = if (hasColumn("task_attachments", "kind")) "kind" else "'FILE'"
                    db.execSQL(
                        "CREATE TABLE task_attachments_new (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "taskId INTEGER NOT NULL, storedName TEXT NOT NULL, " +
                            "displayName TEXT NOT NULL, mimeType TEXT NOT NULL, " +
                            "sizeBytes INTEGER NOT NULL, kind TEXT NOT NULL, createdAt INTEGER NOT NULL, " +
                            "FOREIGN KEY(taskId) REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
                    )
                    db.execSQL(
                        "INSERT INTO task_attachments_new " +
                            "(id, taskId, storedName, displayName, mimeType, sizeBytes, kind, createdAt) " +
                            "SELECT id, taskId, $sourceName, displayName, mimeType, sizeBytes, $sourceKind, createdAt " +
                            "FROM task_attachments"
                    )
                    db.execSQL("DROP TABLE task_attachments")
                    db.execSQL("ALTER TABLE task_attachments_new RENAME TO task_attachments")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_task_attachments_taskId ON task_attachments(taskId)")
                }

                // У добавленных выше NOT NULL-полей были временные DEFAULT,
                // чтобы старая база могла заполнить существующие строки. В
                // финальной Room-схеме этих DEFAULT нет. Пересборка tasks
                // убирает их и возвращает ровно контракт текущей сущности.
                db.execSQL(
                    "CREATE TABLE tasks_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, globalId TEXT, spaceId TEXT, " +
                        "syncRevision INTEGER NOT NULL, syncState TEXT NOT NULL, title TEXT NOT NULL, " +
                        "emoji TEXT, iconPath TEXT, note TEXT, projectId INTEGER, parentTaskId INTEGER, " +
                        "priority TEXT NOT NULL, difficulty INTEGER NOT NULL, dueDate INTEGER, dueTime INTEGER, " +
                        "estimateMinutes INTEGER, status TEXT NOT NULL, completedAt INTEGER, position INTEGER NOT NULL, " +
                        "quadrantOverride TEXT, recurrenceId INTEGER, seriesId TEXT, isTemplate INTEGER NOT NULL, " +
                        "persistentReminderMinutes INTEGER, postponeCount INTEGER NOT NULL, lastPostponedAt INTEGER, " +
                        "sourceLink TEXT, delegatedTo TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, " +
                        "FOREIGN KEY(projectId) REFERENCES projects(id) ON UPDATE NO ACTION ON DELETE SET NULL, " +
                        "FOREIGN KEY(parentTaskId) REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                        "FOREIGN KEY(recurrenceId) REFERENCES recurrence_rules(id) ON UPDATE NO ACTION ON DELETE SET NULL)"
                )
                db.execSQL(
                    "INSERT INTO tasks_new (id, globalId, spaceId, syncRevision, syncState, title, emoji, iconPath, note, " +
                        "projectId, parentTaskId, priority, difficulty, dueDate, dueTime, estimateMinutes, status, completedAt, " +
                        "position, quadrantOverride, recurrenceId, seriesId, isTemplate, persistentReminderMinutes, postponeCount, " +
                        "lastPostponedAt, sourceLink, delegatedTo, createdAt, updatedAt) " +
                        "SELECT id, globalId, spaceId, syncRevision, syncState, title, emoji, iconPath, note, projectId, parentTaskId, " +
                        "priority, difficulty, dueDate, dueTime, estimateMinutes, status, completedAt, position, quadrantOverride, " +
                        "recurrenceId, seriesId, isTemplate, persistentReminderMinutes, postponeCount, lastPostponedAt, sourceLink, " +
                        "delegatedTo, createdAt, updatedAt FROM tasks"
                )
                db.execSQL("DROP TABLE tasks")
                db.execSQL("ALTER TABLE tasks_new RENAME TO tasks")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_projectId ON tasks(projectId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_parentTaskId ON tasks(parentTaskId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_recurrenceId ON tasks(recurrenceId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_dueDate ON tasks(dueDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_status ON tasks(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_seriesId ON tasks(seriesId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_spaceId ON tasks(spaceId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_tasks_globalId ON tasks(globalId)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS sync_tombstones (" +
                        "globalId TEXT NOT NULL PRIMARY KEY, spaceId TEXT NOT NULL, " +
                        "recordType TEXT NOT NULL, baseRevision INTEGER NOT NULL, createdAt INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_tombstones_spaceId ON sync_tombstones(spaceId)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS sync_state (" +
                        "accountId TEXT NOT NULL PRIMARY KEY, cursor INTEGER NOT NULL, lastSuccessAt INTEGER)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS sync_conflicts (" +
                        "globalId TEXT NOT NULL PRIMARY KEY, spaceId TEXT NOT NULL, " +
                        "localRevision INTEGER NOT NULL, remoteRevision INTEGER NOT NULL, " +
                        "remoteCiphertext TEXT NOT NULL, createdAt INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_conflicts_spaceId ON sync_conflicts(spaceId)")
            }
        }

        /** 8 → 9: составные индексы горячих запросов экранов «Сегодня» и «Задачи». */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_tasks_isTemplate_parentTaskId_status_dueDate " +
                        "ON tasks(isTemplate, parentTaskId, status, dueDate)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_tasks_parentTaskId_status " +
                        "ON tasks(parentTaskId, status)"
                )
            }
        }

        val MIGRATIONS: Array<Migration> =
            arrayOf(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9
            )
    }
}
