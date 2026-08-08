package dev.ashwake.data.db.entity.routines

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Сущности рутин и фокуса (п. 6, 8). */

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String? = null,
    val startTime: Int? = null,
    val alarmEnabled: Boolean = false,
    val ttsEnabled: Boolean = true,
    val vibrateOnStep: Boolean = true,
    val position: Int = 0,
    val archived: Boolean = false
)

@Entity(
    tableName = "routine_steps",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"], childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("routineId")]
)
data class RoutineStepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long,
    val title: String,
    val durationSeconds: Int,
    val position: Int,
    val note: String? = null,
    val ttsText: String? = null
)

@Entity(
    tableName = "routine_sessions",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"], childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("routineId"), Index("startedAt")]
)
data class RoutineSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long,
    val startedAt: Long,
    val endedAt: Long? = null,
    val completed: Boolean = false,
    val plannedSeconds: Int,
    val actualSeconds: Int = 0,
    val skippedSteps: Int = 0
)

/**
 * Снимок шага на момент сессии.
 *
 * Заголовок и план копируются, а не берутся по ссылке: правка рутины
 * не должна переписывать историю прошлых прохождений.
 */
@Entity(
    tableName = "routine_session_steps",
    foreignKeys = [
        ForeignKey(
            entity = RoutineSessionEntity::class,
            parentColumns = ["id"], childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class RoutineSessionStepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val stepId: Long? = null,
    val title: String,
    val position: Int,
    val plannedSeconds: Int,
    val actualSeconds: Int = 0,
    val skipped: Boolean = false,
    val addedOnTheFly: Boolean = false
)

@Entity(
    tableName = "focus_sessions",
    indices = [Index("taskId"), Index("startedAt")]
)
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    // Внешнего ключа на задачу нет намеренно: удалённая задача
    // не должна утаскивать за собой часы фокуса
    val taskId: Long? = null,
    val projectId: Long? = null,
    val mode: String,
    val phase: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val plannedSeconds: Int = 0,
    val actualSeconds: Int = 0,
    val completed: Boolean = false,
    val interruptions: Int = 0,
    val whiteNoiseId: String? = null
)
