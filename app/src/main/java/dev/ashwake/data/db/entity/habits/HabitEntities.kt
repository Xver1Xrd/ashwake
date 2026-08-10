package dev.ashwake.data.db.entity.habits

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Сущности фичи «Привычки».
 *
 * Снимки score (`habit_score_snapshots` из docs/02-database.md) сознательно
 * не заведены: год истории — это 365 контрольных точек, движок считает их
 * за микросекунды. Таблица-кэш появится, когда профилирование покажет нужду,
 * а не заранее — иначе пришлось бы держать её в согласии с историей отметок.
 */

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String? = null,
    val iconPath: String? = null,
    val color: Int = 0,
    val type: String,
    val sphere: String,
    val targetValue: Float = 1f,
    val unitName: String? = null,
    val minimumValue: Float? = null,
    val scheduleType: String,
    val timesPerWeek: Int = 3,
    val weekdaysMask: Int = 0b1111111,
    val intervalAnchor: Int? = null,
    val biweeklyAnchor: Int? = null,
    val reminderTime: Int? = null,
    val freezeQuotaPerMonth: Int = 3,
    val position: Int = 0,
    val archived: Boolean = false,
    val createdAt: Long
)

/**
 * Одна отметка за сутки. Уникальность (habitId, date) — источник правды для score:
 * без неё двойное нажатие в списке и в уведомлении дало бы две записи за день.
 */
@Entity(
    tableName = "habit_entries",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"], childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["habitId", "date"], unique = true),
        Index("date")
    ]
)
data class HabitEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val date: Int,
    val status: String,
    val value: Float = 0f,
    val note: String? = null,
    val completedAt: Long? = null,
    val skipReasonId: Long? = null,
    val source: String
)

@Entity(tableName = "habit_skip_reasons", indices = [Index(value = ["code"], unique = true)])
data class HabitSkipReasonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val label: String,
    val builtin: Boolean = true
)

/** Потраченные заморозки. `monthKey` = год*100 + месяц, по нему считается остаток квоты. */
@Entity(
    tableName = "habit_freezes",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"], childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["habitId", "date"], unique = true),
        Index("monthKey")
    ]
)
data class HabitFreezeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val date: Int,
    val monthKey: Int,
    val spentAt: Long,
    val auto: Boolean = false
)

/** `habitId = null` — пауза всех привычек сразу (режим отпуска, п. 5). */
@Entity(tableName = "habit_pauses", indices = [Index("habitId")])
data class HabitPauseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long? = null,
    val startDate: Int,
    val endDate: Int? = null,
    val reason: String? = null
)

@Entity(
    tableName = "habit_anchors",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"], childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("habitId")]
)
data class HabitAnchorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val type: String,
    val refHabitId: Long? = null,
    val refRoutineId: Long? = null,
    val refTagId: Long? = null,
    val delayMinutes: Int = 0,
    /**
     * День последнего срабатывания, epochDay. Якорь не срабатывает повторно
     * в тот же день (п. 6 ТЗ): иначе привычка «после зарядки» звонила бы
     * столько раз, сколько человек трогал зарядку.
     */
    val lastFiredDate: Int? = null
)
