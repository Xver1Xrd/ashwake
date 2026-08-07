# Этап 1b. Схема базы данных

Room, `AshwakeDatabase`, версия 1. Схема спроектирована сразу под всё ТЗ,
чтобы не мигрировать при добавлении фич.

> **Что уже в коде.** Задачи: `projects`, `tags`, `tasks`, `task_tag_cross_ref`,
> `recurrence_rules`, `task_postponements`. Привычки: `habits`, `habit_entries`,
> `habit_skip_reasons`, `habit_freezes`, `habit_pauses`, `habit_anchors`.
> Экспортированная схема лежит в `app/schemas/` и коммитится — миграции
> ревьюятся по диффу. Остальные таблицы подключаются на своих этапах;
> до версии 1.0 база в debug пересоздаётся, миграции пишутся с первого релиза.
>
> **Отменено:** `habit_score_snapshots` не заведена. Это был кэш, а год истории —
> 365 контрольных точек, которые движок считает за микросекунды. Таблицу
> пришлось бы держать в согласии с историей отметок ради выигрыша,
> которого нет. Вернёмся к ней, если профилирование покажет нужду.

## Соглашения

| Тип в домене | Хранение | Причина |
|---|---|---|
| `Instant` | `Long` epochMillis | точные моменты: старт отказа, время срыва, транзакции |
| `LocalDate` | `Int` epochDay | сутки как единица: отметки привычек, дни задач |
| `LocalTime` | `Int` минут от полуночи | слоты дня, время напоминаний |
| `Duration` | `Int` минут (или секунд в рутинах/фокусе) | явно в имени поля |
| `enum` | `String` (имя константы) | читаемость при экспорте в JSON/CSV |
| списки | отдельная таблица | JSON-колонок нет нигде, кроме кэшей отчётов |

- Все `id` локальных сущностей — `Long`, `autoGenerate = true`.
- `id` предметов, палитр, аффиксов, достижений — `String` из assets-каталога.
- Каскады: `onDelete = CASCADE` на дочерних (подзадачи, шаги, отметки, попытки),
  `SET_NULL` там, где потеря родителя не должна убивать историю (задача у фокус-сессии).
- «Мягкое удаление» через `archived: Boolean` у сущностей, у которых есть история:
  привычки, отказы, проекты, рутины. Физическое удаление — только по явному запросу.
- Границы суток берутся из настройки «начало дня» (по умолчанию 04:00), а не из полуночи,
  иначе отметка в 01:00 улетает в следующий день.

---

## 1. Задачи

```kotlin
@Entity(tableName = "projects")
data class ProjectEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val color: Int,
  val icon: String?,
  val position: Int,
  val archived: Boolean = false,
  val createdAt: Long
)

@Entity(tableName = "tags", indices = [Index("name", unique = true)])
data class TagEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val color: Int
)

@Entity(
  tableName = "tasks",
  foreignKeys = [
    ForeignKey(ProjectEntity::class, ["id"], ["projectId"], onDelete = SET_NULL),
    ForeignKey(TaskEntity::class, ["id"], ["parentTaskId"], onDelete = CASCADE),
    ForeignKey(RecurrenceRuleEntity::class, ["id"], ["recurrenceId"], onDelete = SET_NULL)
  ],
  indices = [Index("projectId"), Index("parentTaskId"), Index("recurrenceId"),
             Index("dueDate"), Index("status"), Index("seriesId")]
)
data class TaskEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val title: String,
  val note: String? = null,
  val projectId: Long? = null,
  val parentTaskId: Long? = null,          // подзадача
  val priority: String,                    // P1..P4
  val dueDate: Int? = null,                // epochDay
  val dueTime: Int? = null,                // минуты от полуночи
  val estimateMinutes: Int? = null,        // нужна таймбоксингу (п. 2)
  val status: String,                      // ACTIVE | DONE | DROPPED
  val completedAt: Long? = null,
  val position: Int = 0,                   // ручной порядок и drag-and-drop в матрице
  val eisenhowerQuadrant: String? = null,  // Q1..Q4, null = вычислять по приоритету/дедлайну
  // повторы: правило + связь экземпляров одной серии
  val recurrenceId: Long? = null,
  val seriesId: String? = null,            // UUID серии; экземпляры делят его
  val isTemplate: Boolean = false,         // шаблон серии сам не показывается в списках
  // настойчивое напоминание (п. 1)
  val persistentReminderMinutes: Int? = null,
  // счётчик переносов (п. 1): денормализован ради фильтра «залежавшиеся»
  val postponeCount: Int = 0,
  val lastPostponedAt: Long? = null,
  // «поделиться» из другого приложения (п. 11)
  val sourceLink: String? = null,
  val delegatedTo: String? = null,
  val createdAt: Long,
  val updatedAt: Long
)

@Entity(tableName = "recurrence_rules")
data class RecurrenceRuleEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val type: String,          // DAILY | EVERY_N_DAYS | WEEKDAYS | DAY_OF_MONTH
  val intervalDays: Int? = null,
  val weekdaysMask: Int? = null,   // биты 0..6, понедельник = бит 0
  val dayOfMonth: Int? = null,
  val startDate: Int,
  val endDate: Int? = null,
  val fromCompletion: Boolean = false  // считать следующий срок от факта, а не от плана
)

@Entity(tableName = "task_tag_cross_ref", primaryKeys = ["taskId", "tagId"],
  foreignKeys = [/* CASCADE на обе стороны */], indices = [Index("tagId")])
data class TaskTagCrossRef(val taskId: Long, val tagId: Long)

// Полная история переносов. tasks.postponeCount — её кэш.
@Entity(tableName = "task_postponements", indices = [Index("taskId")])
data class TaskPostponementEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val taskId: Long,
  val fromDate: Int?,
  val toDate: Int?,
  val at: Long,
  val source: String        // SWIPE | RITUAL | DIALOG | AUTO
)
```

## 2. Таймбоксинг

```kotlin
// Итог одной раскладки дня. Хранит дефицит времени, чтобы честно показать нехватку (п. 2).
@Entity(tableName = "timebox_days")
data class TimeboxDayEntity(
  @PrimaryKey val date: Int,
  val plannedAt: Long,
  val workStartMinute: Int,
  val workEndMinute: Int,
  val bufferMinutes: Int,
  val lunchStartMinute: Int?,
  val lunchEndMinute: Int?,
  val deficitMinutes: Int          // сколько не влезло
)

@Entity(tableName = "timebox_blocks",
  foreignKeys = [/* date → CASCADE, taskId/routineId → SET_NULL */],
  indices = [Index("date"), Index("taskId"), Index("routineId")])
data class TimeboxBlockEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val date: Int,
  val startMinute: Int,
  val endMinute: Int,
  val kind: String,                // TASK | ROUTINE | CALENDAR | LUNCH | BUFFER | MANUAL
  val title: String,
  val taskId: Long? = null,
  val routineId: Long? = null,
  val calendarEventId: Long? = null,   // системный календарь, только чтение
  val pinned: Boolean = false,         // «не двигать»
  val createdBy: String,               // AUTO | MANUAL
  val actualStartMinute: Int? = null,  // факт, для пересчёта на лету
  val actualEndMinute: Int? = null
)
```

## 3. Привычки

```kotlin
@Entity(tableName = "habits")
data class HabitEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val icon: String?,
  val color: Int,
  val type: String,                 // CHECK | COUNTER | NEGATIVE
  val sphere: String,               // HEALTH | SPORT | STUDY | CHORES | MENTAL
  // цели
  val targetValue: Float = 1f,      // 2 (литра), 30 (страниц)
  val unitName: String? = null,
  val minimumValue: Float? = null,  // «минимальная планка» на плохой день (п. 5)
  // расписание
  val scheduleType: String,         // DAILY | TIMES_PER_WEEK | EVERY_OTHER_DAY |
                                    // WEEKDAYS | BIWEEKLY
  val timesPerWeek: Int? = null,
  val weekdaysMask: Int? = null,
  val biweeklyAnchorDate: Int? = null,
  // напоминание и якорь (п. 5)
  val reminderTime: Int? = null,
  val freezeQuotaPerMonth: Int = 3,
  val position: Int = 0,
  val archived: Boolean = false,
  val createdAt: Long
)

// Одна отметка за сутки. Уникальность (habitId, date) — источник правды для score.
@Entity(tableName = "habit_entries",
  indices = [Index(value = ["habitId", "date"], unique = true), Index("date")])
data class HabitEntryEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val habitId: Long,
  val date: Int,
  val status: String,        // DONE | MINIMUM | SKIPPED | FROZEN | PAUSED
  val value: Float,          // факт для счётчиков
  val contribution: Float,   // вклад в score: DONE=1.0, MINIMUM=0.5, SKIPPED=0.0
  val note: String? = null,
  val completedAt: Long?,
  val skipReasonId: Long? = null,
  val source: String         // MANUAL | NOTIFICATION | WIDGET | RITUAL
)

@Entity(tableName = "habit_skip_reasons")
data class HabitSkipReasonEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val code: String,          // FORGOT | NO_TIME | NO_ENERGY | NO_WILL | CIRCUMSTANCES | CUSTOM
  val label: String,
  val builtin: Boolean
)

// Кэш score и стрика на дату. Пересчитывается воркером и при правке истории.
// Нужен, чтобы график за год не пересчитывал 365 дней на каждой рекомпозиции.
@Entity(tableName = "habit_score_snapshots", primaryKeys = ["habitId", "date"])
data class HabitScoreSnapshotEntity(
  val habitId: Long,
  val date: Int,
  val score: Float,          // 0..1
  val streak: Int,
  val recordStreak: Int
)

// Потраченные заморозки. monthKey = год*100+месяц, для контроля квоты.
@Entity(tableName = "habit_freezes",
  indices = [Index(value = ["habitId", "date"], unique = true), Index("monthKey")])
data class HabitFreezeEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val habitId: Long,
  val date: Int,
  val monthKey: Int,
  val spentAt: Long,
  val auto: Boolean          // списана автоматически или руками
)

// Пауза / отпуск. habitId = null → пауза всех привычек сразу.
@Entity(tableName = "habit_pauses", indices = [Index("habitId")])
data class HabitPauseEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val habitId: Long? = null,
  val startDate: Int,
  val endDate: Int? = null,  // null = бессрочно, до ручного снятия
  val reason: String? = null
)

// Якоря и цепочки A → B → C (habit stacking, п. 5)
@Entity(tableName = "habit_anchors", indices = [Index("habitId")])
data class HabitAnchorEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val habitId: Long,
  val type: String,          // HABIT_DONE | ROUTINE_DONE | FIRST_UNLOCK | TASK_TAG_DONE | TIME
  val refHabitId: Long? = null,
  val refRoutineId: Long? = null,
  val refTagId: Long? = null,
  val delayMinutes: Int = 0
)
```

Каталог 60+ готовых привычек — в `assets/presets/habits.json`, не в БД.

## 4. Отказы

```kotlin
@Entity(tableName = "abstinences")
data class AbstinenceEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val icon: String?,
  val paletteId: String,
  val mode: String,                    // GENTLE | STRICT
  val gentlePenaltyDays: Int = 7,      // сколько дней вычитать в GENTLE вместо обнуления
  val milestonesEnabled: Boolean = true,
  val currentAttemptId: Long? = null,  // денормализация: активная попытка
  val motivationText: String? = null,  // «зачем я это бросил» — показывается при тяге
  // baseline: null → блок «сэкономлено» скрыт целиком
  val baselineUnitName: String? = null,
  val baselineUnitsPerDay: Float? = null,
  val baselineCostPerUnit: Float? = null,
  val baselineCurrency: String? = null,
  val stickyNotification: Boolean = false,
  val substanceWarningAck: Boolean = false,  // однократный нейтральный экран (п. 4)
  val position: Int = 0,
  val archived: Boolean = false,
  val createdAt: Long
)

// История попыток. Счётчик обнуляется — история не удаляется никогда.
@Entity(tableName = "abstinence_attempts", indices = [Index("abstinenceId")])
data class AbstinenceAttemptEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val abstinenceId: Long,
  val ordinal: Int,                    // номер попытки, показывается как «Попытка №4»
  val startedAt: Long,
  val endedAt: Long? = null,           // null = текущая
  val relapseReasonId: Long? = null,
  val note: String? = null,
  val penaltyDaysApplied: Int = 0,     // применённый штраф в GENTLE
  val undoDeadline: Long? = null       // «отмена срыва» в течение 24 часов
)

@Entity(tableName = "abstinence_relapse_reasons")
data class RelapseReasonEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val code: String,   // STRESS | COMPANY | INERTIA | CELEBRATION | BOREDOM | COULD_NOT_REFUSE | CUSTOM
  val label: String,
  val builtin: Boolean
)

// Вехи: и стандартные (1,3,7,14,30,60,90,180,270,365, дальше по году), и кастомные.
// userText пишет сам пользователь — приложение не выдаёт медицинских утверждений.
@Entity(tableName = "abstinence_milestones",
  indices = [Index(value = ["abstinenceId", "days"], unique = true)])
data class AbstinenceMilestoneEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val abstinenceId: Long,
  val days: Int,
  val title: String,
  val userText: String? = null,
  val custom: Boolean = false,
  val reachedAt: Long? = null,
  val notified: Boolean = false
)

// Тяга: фиксация после «Тяжело». Основа аналитики (heatmap по часам/дням, топ триггеров).
@Entity(tableName = "craving_events",
  indices = [Index("abstinenceId"), Index("at")])
data class CravingEventEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val abstinenceId: Long,
  val at: Long,
  val hourOfDay: Int,          // денормализовано ради heatmap без пересчёта
  val dayOfWeek: Int,
  val intensity: Int,          // 1..5
  val triggerId: Long? = null,
  val note: String? = null,
  val resisted: Boolean? = null,   // null = не заполнил
  val durationSeconds: Int? = null,
  val usedBreathing: Boolean = false,
  val usedSubstituteId: Long? = null
)

@Entity(tableName = "craving_triggers")
data class CravingTriggerEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val code: String, val label: String, val builtin: Boolean
)

// Заранее заготовленные действия-заместители
@Entity(tableName = "abstinence_substitutes", indices = [Index("abstinenceId")])
data class AbstinenceSubstituteEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val abstinenceId: Long, val text: String, val position: Int
)
```

Производные величины (рекорд, всего чистых дней, сэкономлено, прогресс до вехи)
не хранятся — считает `AbstinenceCalculator` из попыток и baseline.

## 5. Рутины

```kotlin
@Entity(tableName = "routines")
data class RoutineEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String, val icon: String?,
  val startTime: Int? = null,        // привязка ко времени старта
  val alarmEnabled: Boolean = false,
  val ttsEnabled: Boolean = true,
  val vibrateOnStep: Boolean = true,
  val position: Int = 0,
  val archived: Boolean = false
)

@Entity(tableName = "routine_steps", indices = [Index("routineId")])
data class RoutineStepEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val routineId: Long,
  val title: String,
  val durationSeconds: Int,
  val position: Int,
  val note: String? = null,
  val ttsText: String? = null        // если объявлять нужно не заголовком
)

@Entity(tableName = "routine_sessions", indices = [Index("routineId"), Index("startedAt")])
data class RoutineSessionEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val routineId: Long,
  val startedAt: Long, val endedAt: Long? = null,
  val completed: Boolean = false,
  val plannedSeconds: Int, val actualSeconds: Int = 0,
  val skippedSteps: Int = 0          // для routineBonus «без пропуска шагов» (п. 16.5.2)
)

// Снимок шага на момент сессии: план vs факт, п. 6.
// Заголовок копируется, чтобы правка рутины не переписала историю.
@Entity(tableName = "routine_session_steps", indices = [Index("sessionId")])
data class RoutineSessionStepEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val sessionId: Long,
  val stepId: Long? = null,
  val title: String,
  val position: Int,
  val plannedSeconds: Int, val actualSeconds: Int,
  val skipped: Boolean = false,
  val addedOnTheFly: Boolean = false
)
```

## 6. Фокус

```kotlin
@Entity(tableName = "focus_sessions",
  indices = [Index("taskId"), Index("startedAt")])
data class FocusSessionEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val taskId: Long? = null,          // SET_NULL: удалённая задача не стирает часы фокуса
  val projectId: Long? = null,
  val mode: String,                  // POMODORO | STOPWATCH
  val phase: String,                 // WORK | BREAK
  val startedAt: Long, val endedAt: Long? = null,
  val plannedSeconds: Int, val actualSeconds: Int = 0,
  val completed: Boolean = false,
  val interruptions: Int = 0,
  val whiteNoiseId: String? = null
)
```

## 7. Вечерний ритуал

```kotlin
@Entity(tableName = "daily_reviews")
data class DailyReviewEntity(
  @PrimaryKey val date: Int,
  val dayRating: Int?,       // 1..5
  val mood: Int?,            // отдельная шкала — нужна корреляциям (п. 10)
  val energy: Int?,          // отдельная шкала
  val note: String? = null,
  val completedAt: Long,
  val completedAs: String    // EVENING | NEXT_MORNING (ритуал за вчера)
)

@Entity(tableName = "daily_review_top_tasks",
  primaryKeys = ["date", "taskId"], indices = [Index("taskId")])
data class DailyReviewTopTaskEntity(
  val date: Int, val taskId: Long, val position: Int
)
```

## 8. Экономика

```kotlin
@Entity(tableName = "wallet")
data class WalletEntity(
  @PrimaryKey val id: Int = 1,   // singleton
  val coins: Long = 0,
  val xp: Long = 0,
  val level: Int = 1
)

// Единый журнал начислений и списаний. Пишет только RewardEngine.
// Одна таблица на монеты и XP: currency различает, схема и отладка одинаковые.
@Entity(tableName = "ledger_transactions", indices = [Index("at"), Index("source")])
data class LedgerTransactionEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val at: Long,
  val currency: String,      // COIN | XP
  val amount: Long,          // + начисление, − списание
  val source: String,        // TASK_DONE | HABIT_DONE | ROUTINE_DONE | ABSTINENCE_DAY |
                             // MILESTONE | CRAVING_RESISTED | FOCUS_DONE | RITUAL_DONE |
                             // PURCHASE | UPGRADE | USER_REWARD | CHEST | ADJUST
  val refId: String? = null, // id сущности-источника
  val multiplierApplied: Float = 1f,  // итоговый множитель от экипировки — для отладки баланса
  val balanceAfter: Long,
  val note: String? = null
)

// Пользовательские награды: «час игры — 50 монет»
@Entity(tableName = "user_rewards")
data class UserRewardEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String, val icon: String?, val cost: Long,
  val repeatable: Boolean = true, val archived: Boolean = false, val createdAt: Long
)

@Entity(tableName = "user_reward_redemptions", indices = [Index("rewardId")])
data class UserRewardRedemptionEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val rewardId: Long, val at: Long, val cost: Long
)
```

## 9. Персонаж

Каталог (`items.json`, `palettes.json`, `affixes.json`, `sets.json`) — в assets.
В БД только то, что принадлежит пользователю.

```kotlin
@Entity(tableName = "character_profile")
data class CharacterProfileEntity(
  @PrimaryKey val id: Int = 1,
  val name: String,
  val body: String,          // SLIM | NORMAL | BULKY
  val skinToneId: String,
  val hairStyleId: String, val hairColorId: String,
  val faceId: String?,
  val reduceMotion: Boolean = false,
  val decayEnabled: Boolean = true,    // состояние упадка при пропусках (п. 15.8)
  val parallaxEnabled: Boolean = true
)

// Владение предметом. itemId — строковый id из каталога.
@Entity(tableName = "owned_items", indices = [Index("itemId", unique = true)])
data class OwnedItemEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val itemId: String,
  val acquiredAt: Long,
  val source: String,        // SHOP | ACHIEVEMENT | CHEST | STARTER
  val upgradeLevel: Int = 0, // 0..10
  val favorite: Boolean = false
)

// Текущая экипировка: слот → предмет. Слот первичный ключ, надет максимум один предмет.
@Entity(tableName = "equipped_items")
data class EquippedItemEntity(
  @PrimaryKey val slot: String,      // EquipSlot.name
  val ownedItemId: Long
)

@Entity(tableName = "appearance_presets")
data class AppearancePresetEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String, val position: Int    // 3 пресета (п. 16.5.6)
)

@Entity(tableName = "appearance_preset_items", primaryKeys = ["presetId", "slot"])
data class AppearancePresetItemEntity(
  val presetId: Long, val slot: String, val ownedItemId: Long
)

// Характеристики растут от поведения, не за монеты. value = floor(sqrt(points / 4)).
@Entity(tableName = "character_stats")
data class CharacterStatEntity(
  @PrimaryKey val stat: String,   // STRENGTH | AGILITY | ENDURANCE | INTELLECT | WILL | LUCK
  val points: Long = 0,
  val value: Int = 0
)

// Журнал начисления очков характеристик — чтобы можно было пересчитать с нуля.
@Entity(tableName = "stat_events", indices = [Index("at"), Index("stat")])
data class StatEventEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val at: Long, val stat: String, val points: Int,
  val source: String, val refId: String? = null
)

// Материалы улучшения, падают за стрики (7 / 30 / 100 дней)
@Entity(tableName = "material_inventory")
data class MaterialInventoryEntity(
  @PrimaryKey val materialId: String,   // COMMON | DOUBLE | RARE
  val amount: Int = 0
)

@Entity(tableName = "achievements")
data class AchievementEntity(
  @PrimaryKey val id: String,           // определение в assets/catalog/achievements.json
  val unlockedAt: Long? = null,
  val progress: Float = 0f
)

// Ежедневная награда: lootLuck и rerollChest из эффектов предметов
@Entity(tableName = "daily_chests")
data class DailyChestEntity(
  @PrimaryKey val date: Int,
  val openedAt: Long? = null,
  val rerollsUsed: Int = 0,
  val rewardJson: String? = null
)
```

## 10. Блокировка приложений

```kotlin
@Entity(tableName = "blocking_rules")
data class BlockingRuleEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val enabled: Boolean = false,       // функция выключена по умолчанию
  val conditionType: String,          // MORNING_HABITS_DONE | ROUTINE_DONE | TIME_AFTER
  val refRoutineId: Long? = null,
  val timeMinute: Int? = null,
  val createdAt: Long
)

@Entity(tableName = "blocked_apps",
  primaryKeys = ["ruleId", "packageName"])
data class BlockedAppEntity(val ruleId: Long, val packageName: String)

// Лог экстренных обходов (15 секунд задержки + запись)
@Entity(tableName = "bypass_log", indices = [Index("at")])
data class BypassLogEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val at: Long, val packageName: String, val ruleId: Long?, val delaySeconds: Int
)
```

## 11. Служебное: бэкапы, импорт, кэш аналитики

```kotlin
@Entity(tableName = "backup_log", indices = [Index("at")])
data class BackupLogEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val at: Long, val uri: String, val sizeBytes: Long,
  val encrypted: Boolean, val success: Boolean, val error: String? = null
)

@Entity(tableName = "import_log")
data class ImportLogEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val at: Long,
  val source: String,        // LOOP_CSV | LOOP_DB | TICKTICK | TODOIST
  val imported: Int, val skipped: Int,
  val reportJson: String     // причины пропусков, для экрана отчёта
)

// Кэши отчётов — единственное место, где допустим JSON в колонке.
@Entity(tableName = "weekly_reports")
data class WeeklyReportEntity(
  @PrimaryKey val weekStartDate: Int, val generatedAt: Long, val json: String
)

@Entity(tableName = "correlation_cache")
data class CorrelationCacheEntity(
  @PrimaryKey val id: Int = 1, val computedAt: Long, val windowDays: Int, val json: String
)
```

## Не в базе

- Настройки (тема, рабочие часы, буфер, время ритуала, «уменьшить движение», начало суток,
  ротация бэкапов, папка SAF) — **DataStore**.
- Пароль шифрования бэкапа — **не хранится нигде**, спрашивается при восстановлении.
- Каталоги предметов, палитр, аффиксов, сетов, достижений, готовых привычек и рутин — **assets**.
- Флаг «первая разблокировка за сегодня» для якоря `FIRST_UNLOCK` — DataStore, не история.

## Узлы, где схема сознательно избыточна

1. **`habit_score_snapshots` дублирует расчёт.** `HabitScoreCalculator` остаётся источником
   правды, снимки — кэш для heatmap за год и графиков. Инвалидация: правка любой отметки
   пересчитывает снимки от этой даты вперёд.
2. **`postponeCount` в задаче при наличии `task_postponements`.** Фильтр «залежавшиеся»
   и жёлтая/красная метка не должны делать `COUNT(*)` на каждый элемент списка.
3. **`hourOfDay` и `dayOfWeek` в `craving_events`.** Heatmap тяги строится группировкой,
   без конвертации миллисекунд в SQL.
4. **`currentAttemptId` в отказе.** Живой тикающий счётчик на главном экране не должен
   искать последнюю попытку подзапросом каждую секунду.
5. **`multiplierApplied` в транзакции.** Без него баланс экипировки нечем отлаживать:
   видно начисление, но не видно, почему оно такое.
