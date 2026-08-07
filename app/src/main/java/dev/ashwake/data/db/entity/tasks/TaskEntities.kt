package dev.ashwake.data.db.entity.tasks

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * Сущности фичи «Задачи».
 *
 * Хранение: Instant → Long (epochMillis), LocalDate → Int (epochDay),
 * LocalTime → Int (минуты от полуночи), enum → String. Конвертеров Room нет
 * намеренно: типы доменных моделей собираются в мапперах, поэтому в схеме
 * видно ровно то, что лежит в SQLite.
 */

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,
    val icon: String? = null,
    val position: Int = 0,
    val archived: Boolean = false,
    val createdAt: Long
)

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int
)

@Entity(tableName = "recurrence_rules")
data class RecurrenceRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val intervalDays: Int? = null,
    val weekdaysMask: Int = 0,
    val dayOfMonth: Int? = null,
    val startDate: Int,
    val endDate: Int? = null,
    val fromCompletion: Boolean = false
)

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"], childColumns = ["projectId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"], childColumns = ["parentTaskId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = RecurrenceRuleEntity::class,
            parentColumns = ["id"], childColumns = ["recurrenceId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("projectId"), Index("parentTaskId"), Index("recurrenceId"),
        Index("dueDate"), Index("status"), Index("seriesId")
    ]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String? = null,
    val projectId: Long? = null,
    val parentTaskId: Long? = null,
    val priority: String,
    val dueDate: Int? = null,
    val dueTime: Int? = null,
    val estimateMinutes: Int? = null,
    val status: String,
    val completedAt: Long? = null,
    val position: Int = 0,
    val quadrantOverride: String? = null,
    val recurrenceId: Long? = null,
    val seriesId: String? = null,
    val isTemplate: Boolean = false,
    val persistentReminderMinutes: Int? = null,
    val postponeCount: Int = 0,
    val lastPostponedAt: Long? = null,
    val sourceLink: String? = null,
    val delegatedTo: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "task_tag_cross_ref",
    primaryKeys = ["taskId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"], childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"], childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tagId")]
)
data class TaskTagCrossRef(
    val taskId: Long,
    val tagId: Long
)

/**
 * Полная история переносов. `tasks.postponeCount` — её денормализованный кэш:
 * фильтр «залежавшиеся» и цветные метки не должны делать COUNT(*) на каждую строку списка.
 */
@Entity(
    tableName = "task_postponements",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"], childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("taskId")]
)
data class TaskPostponementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val fromDate: Int? = null,
    val toDate: Int? = null,
    val at: Long,
    val source: String
)

/** Задача со всем, что нужно списку: теги, правило повтора, подзадачи. */
data class TaskWithRelations(
    @Embedded val task: TaskEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = TaskTagCrossRef::class,
            parentColumn = "taskId",
            entityColumn = "tagId"
        )
    )
    val tags: List<TagEntity> = emptyList(),

    @Relation(parentColumn = "recurrenceId", entityColumn = "id")
    val recurrence: RecurrenceRuleEntity? = null,

    @Relation(parentColumn = "id", entityColumn = "parentTaskId")
    val subtasks: List<TaskEntity> = emptyList()
)
