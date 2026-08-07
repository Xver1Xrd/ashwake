package dev.ashwake.data.db.dao.tasks

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import dev.ashwake.data.db.entity.tasks.ProjectEntity
import dev.ashwake.data.db.entity.tasks.RecurrenceRuleEntity
import dev.ashwake.data.db.entity.tasks.TagEntity
import dev.ashwake.data.db.entity.tasks.TaskEntity
import dev.ashwake.data.db.entity.tasks.TaskPostponementEntity
import dev.ashwake.data.db.entity.tasks.TaskTagCrossRef
import dev.ashwake.data.db.entity.tasks.TaskWithRelations
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    /**
     * Единственный запрос списка на все фильтры сразу.
     * Каждое условие — «параметр не задан ИЛИ совпал», поэтому RawQuery не нужен,
     * а Room проверяет SQL на этапе компиляции.
     *
     * Сортировка: сначала с дедлайном (NULL в конец), потом по приоритету
     * (текст 'P1'..'P4' сортируется лексикографически в нужном порядке), потом вручную.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE isTemplate = 0
          AND parentTaskId IS NULL
          AND (:includeDone = 1 OR status = 'ACTIVE')
          AND (:projectId IS NULL OR projectId = :projectId)
          AND (:priority IS NULL OR priority = :priority)
          AND (:dueBefore IS NULL OR (dueDate IS NOT NULL AND dueDate <= :dueBefore))
          AND (:minPostpones = 0 OR postponeCount >= :minPostpones)
          AND (:query IS NULL OR title LIKE '%' || :query || '%')
          AND (:tagId IS NULL OR id IN (
                SELECT taskId FROM task_tag_cross_ref WHERE tagId = :tagId))
        ORDER BY (dueDate IS NULL), dueDate, dueTime, priority, position, id
        """
    )
    fun observeTasks(
        includeDone: Int,
        projectId: Long?,
        tagId: Long?,
        priority: String?,
        dueBefore: Int?,
        minPostpones: Int,
        query: String?
    ): Flow<List<TaskWithRelations>>

    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE isTemplate = 0
          AND parentTaskId IS NULL
          AND dueDate IS NOT NULL
          AND dueDate BETWEEN :from AND :to
          AND (:includeDone = 1 OR status = 'ACTIVE')
        ORDER BY dueDate, (dueTime IS NULL), dueTime, priority
        """
    )
    fun observeTasksInRange(from: Int, to: Int, includeDone: Int): Flow<List<TaskWithRelations>>

    /** Активные задачи с точным временем — их будильники переживают перезагрузку. */
    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE status = 'ACTIVE' AND isTemplate = 0
          AND dueDate IS NOT NULL AND dueTime IS NOT NULL
        """
    )
    suspend fun tasksWithReminders(): List<TaskWithRelations>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeTask(id: Long): Flow<TaskWithRelations?>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTask(id: Long): TaskWithRelations?

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE tasks SET status = :status, completedAt = :completedAt, updatedAt = :now WHERE id = :id")
    suspend fun setStatus(id: Long, status: String, completedAt: Long?, now: Long)

    @Query(
        """
        UPDATE tasks
        SET dueDate = :toDate,
            postponeCount = postponeCount + 1,
            lastPostponedAt = :now,
            updatedAt = :now
        WHERE id = :id
        """
    )
    suspend fun applyPostpone(id: Long, toDate: Int?, now: Long)

    @Query(
        """
        UPDATE tasks
        SET quadrantOverride = :quadrant,
            priority = COALESCE(:priority, priority),
            updatedAt = :now
        WHERE id = :id
        """
    )
    suspend fun setQuadrant(id: Long, quadrant: String, priority: String?, now: Long)

    @Query("UPDATE tasks SET delegatedTo = :delegateTo, updatedAt = :now WHERE id = :id")
    suspend fun setDelegate(id: Long, delegateTo: String?, now: Long)

    // --- теги задачи ---

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRefs(refs: List<TaskTagCrossRef>)

    @Query("DELETE FROM task_tag_cross_ref WHERE taskId = :taskId")
    suspend fun clearTags(taskId: Long)

    // --- повторы и история переносов ---

    @Insert
    suspend fun insertRecurrence(rule: RecurrenceRuleEntity): Long

    @Update
    suspend fun updateRecurrence(rule: RecurrenceRuleEntity)

    @Insert
    suspend fun insertPostponement(entry: TaskPostponementEntity)

    @Query("SELECT * FROM task_postponements WHERE taskId = :taskId ORDER BY at DESC")
    suspend fun postponementsOf(taskId: Long): List<TaskPostponementEntity>

    @Query("SELECT COALESCE(MAX(position), 0) FROM tasks WHERE parentTaskId = :parentId")
    suspend fun maxSubtaskPosition(parentId: Long): Int
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects WHERE (:includeArchived = 1 OR archived = 0) ORDER BY position, id")
    fun observeProjects(includeArchived: Int): Flow<List<ProjectEntity>>

    @Upsert
    suspend fun upsert(project: ProjectEntity): Long

    @Query("UPDATE projects SET archived = 1 WHERE id = :id")
    suspend fun archive(id: Long)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name")
    fun observeTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity): Long

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteById(id: Long)
}
