package dev.ashwake.data.db.entity.ritual

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Итог вечернего ритуала (п. 9).
 *
 * Настроение и энергия — отдельные колонки: именно их пары с привычками
 * ищет анализатор корреляций, и слитая оценка дня эти связи бы стёрла.
 */
@Entity(tableName = "daily_reviews")
data class DailyReviewEntity(
    @PrimaryKey val date: Int,
    val dayRating: Int? = null,
    val mood: Int? = null,
    val energy: Int? = null,
    val note: String? = null,
    val completedAt: Long,
    val completedAs: String
)

@Entity(
    tableName = "daily_review_top_tasks",
    primaryKeys = ["date", "taskId"],
    indices = [Index("taskId")]
)
data class DailyReviewTopTaskEntity(
    val date: Int,
    val taskId: Long,
    val position: Int
)

/** Кэш недельного отчёта: единственное место, где JSON в колонке допустим. */
@Entity(tableName = "weekly_reports")
data class WeeklyReportEntity(
    @PrimaryKey val weekStartDate: Int,
    val generatedAt: Long,
    val json: String
)
