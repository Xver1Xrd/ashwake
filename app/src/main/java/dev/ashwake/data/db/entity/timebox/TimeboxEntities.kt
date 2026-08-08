package dev.ashwake.data.db.entity.timebox

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Итог одной раскладки дня. Дефицит хранится, чтобы честно показать нехватку (п. 2). */
@Entity(tableName = "timebox_days")
data class TimeboxDayEntity(
    @PrimaryKey val date: Int,
    val plannedAt: Long,
    val workStartMinute: Int,
    val workEndMinute: Int,
    val bufferMinutes: Int,
    val lunchStartMinute: Int? = null,
    val lunchEndMinute: Int? = null,
    val deficitMinutes: Int = 0
)

@Entity(
    tableName = "timebox_blocks",
    foreignKeys = [
        ForeignKey(
            entity = TimeboxDayEntity::class,
            parentColumns = ["date"], childColumns = ["date"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("date"), Index("taskId"), Index("routineId")]
)
data class TimeboxBlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Int,
    val startMinute: Int,
    val endMinute: Int,
    val kind: String,
    val title: String,
    // Внешних ключей на задачу и рутину нет: их удаление не должно
    // обрушивать раскладку прошедшего дня
    val taskId: Long? = null,
    val routineId: Long? = null,
    val calendarEventId: Long? = null,
    val pinned: Boolean = false,
    val createdBy: String,
    val actualStartMinute: Int? = null,
    val actualEndMinute: Int? = null
)
