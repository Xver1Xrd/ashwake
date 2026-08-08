package dev.ashwake.data.db.dao.ritual

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import dev.ashwake.data.db.entity.ritual.DailyReviewEntity
import dev.ashwake.data.db.entity.ritual.DailyReviewTopTaskEntity
import dev.ashwake.data.db.entity.ritual.WeeklyReportEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RitualDao {

    @Query("SELECT * FROM daily_reviews WHERE date = :date")
    fun observeReview(date: Int): Flow<DailyReviewEntity?>

    @Query("SELECT * FROM daily_reviews WHERE date = :date")
    suspend fun review(date: Int): DailyReviewEntity?

    @Query("SELECT * FROM daily_reviews WHERE date BETWEEN :from AND :to ORDER BY date")
    fun observeReviews(from: Int, to: Int): Flow<List<DailyReviewEntity>>

    @Query("SELECT * FROM daily_reviews WHERE date BETWEEN :from AND :to ORDER BY date")
    suspend fun reviews(from: Int, to: Int): List<DailyReviewEntity>

    @Upsert
    suspend fun upsertReview(review: DailyReviewEntity)

    @Query("DELETE FROM daily_review_top_tasks WHERE date = :date")
    suspend fun clearTopTasks(date: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopTasks(tasks: List<DailyReviewTopTaskEntity>)

    @Query("SELECT * FROM daily_review_top_tasks WHERE date = :date ORDER BY position")
    suspend fun topTasks(date: Int): List<DailyReviewTopTaskEntity>

    @Upsert
    suspend fun upsertWeeklyReport(report: WeeklyReportEntity)

    @Query("SELECT * FROM weekly_reports ORDER BY weekStartDate DESC LIMIT :limit")
    fun observeWeeklyReports(limit: Int = 12): Flow<List<WeeklyReportEntity>>
}
