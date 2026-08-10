package dev.ashwake.data.db.entity.abstinence

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Сущности фичи «Отказы» (п. 4). */

@Entity(tableName = "abstinences")
data class AbstinenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String? = null,
    val iconPath: String? = null,
    val paletteId: String = "default",
    val mode: String,
    val gentlePenaltyDays: Int = 7,
    val milestonesEnabled: Boolean = true,
    val motivationText: String? = null,
    // baseline: если пусто, блок «сэкономлено» на экране скрыт целиком
    val baselineUnitName: String? = null,
    val baselineUnitsPerDay: Float? = null,
    val baselineCostPerUnit: Float? = null,
    val baselineCurrency: String? = null,
    val stickyNotification: Boolean = false,
    val substanceWarningAck: Boolean = false,
    val position: Int = 0,
    val archived: Boolean = false,
    val createdAt: Long
)

/**
 * История попыток. Счётчик обнуляется — история не удаляется никогда:
 * именно из неё считаются рекорд и сумма чистых дней за всё время.
 */
@Entity(
    tableName = "abstinence_attempts",
    foreignKeys = [
        ForeignKey(
            entity = AbstinenceEntity::class,
            parentColumns = ["id"], childColumns = ["abstinenceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("abstinenceId")]
)
data class AbstinenceAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val abstinenceId: Long,
    val ordinal: Int,
    val startedAt: Long,
    val endedAt: Long? = null,
    val relapseReasonId: Long? = null,
    val note: String? = null,
    val penaltyDays: Int = 0,
    val undoDeadline: Long? = null
)

@Entity(
    tableName = "abstinence_milestones",
    foreignKeys = [
        ForeignKey(
            entity = AbstinenceEntity::class,
            parentColumns = ["id"], childColumns = ["abstinenceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["abstinenceId", "days"], unique = true)]
)
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

@Entity(
    tableName = "abstinence_relapse_reasons",
    indices = [Index(value = ["code"], unique = true)]
)
data class RelapseReasonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val label: String,
    val builtin: Boolean = true
)

@Entity(tableName = "craving_triggers", indices = [Index(value = ["code"], unique = true)])
data class CravingTriggerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val label: String,
    val builtin: Boolean = true
)

/**
 * Эпизод тяги. `hourOfDay` и `dayOfWeek` денормализованы намеренно:
 * heatmap строится группировкой, без конвертации миллисекунд средствами SQL.
 */
@Entity(
    tableName = "craving_events",
    foreignKeys = [
        ForeignKey(
            entity = AbstinenceEntity::class,
            parentColumns = ["id"], childColumns = ["abstinenceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("abstinenceId"), Index("at")]
)
data class CravingEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val abstinenceId: Long,
    val at: Long,
    val hourOfDay: Int,
    val dayOfWeek: Int,
    val intensity: Int,
    val triggerId: Long? = null,
    val note: String? = null,
    val resisted: Boolean? = null,
    val durationSeconds: Int? = null,
    val usedBreathing: Boolean = false,
    val usedSubstituteId: Long? = null
)

@Entity(
    tableName = "abstinence_substitutes",
    foreignKeys = [
        ForeignKey(
            entity = AbstinenceEntity::class,
            parentColumns = ["id"], childColumns = ["abstinenceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("abstinenceId")]
)
data class AbstinenceSubstituteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val abstinenceId: Long,
    val text: String,
    val position: Int = 0
)
