package dev.ashwake.domain.repository.tasks

import dev.ashwake.core.model.Priority
import dev.ashwake.domain.model.tasks.EisenhowerQuadrant
import dev.ashwake.domain.model.tasks.PostponeSource
import dev.ashwake.domain.model.tasks.Project
import dev.ashwake.domain.model.tasks.Tag
import dev.ashwake.domain.model.tasks.Task
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Набор фильтров списка задач. Пустые поля — «не фильтровать». */
data class TaskFilter(
    val projectId: Long? = null,
    val tagId: Long? = null,
    val priority: Priority? = null,
    val dueBefore: LocalDate? = null,
    val includeDone: Boolean = false,
    /** Только «залежавшиеся»: 3 и более переносов (п. 1). */
    val onlyStale: Boolean = false,
    val query: String? = null
)

interface TaskRepository {

    fun observeTasks(filter: TaskFilter = TaskFilter()): Flow<List<Task>>

    fun observeTask(id: Long): Flow<Task?>

    suspend fun getTask(id: Long): Task?

    /** Создаёт или обновляет задачу вместе с её тегами. Возвращает id. */
    suspend fun upsert(task: Task): Long

    /**
     * Закрывает задачу. Если у неё есть правило повтора — порождает следующий экземпляр
     * серии, поэтому это операция репозитория, а не простой UPDATE из ViewModel.
     */
    suspend fun complete(id: Long): Long?

    suspend fun reopen(id: Long)

    /** Перенос с записью в историю и инкрементом счётчика переносов. */
    suspend fun postpone(id: Long, toDate: LocalDate?, source: PostponeSource)

    suspend fun setQuadrant(id: Long, quadrant: EisenhowerQuadrant, priority: Priority?)

    suspend fun delete(id: Long)

    suspend fun setDelegate(id: Long, delegateTo: String?)

    /** Разбивка залежавшейся задачи на подзадачи (одно из действий диалога на 5-м переносе). */
    suspend fun addSubtasks(parentId: Long, titles: List<String>)
}

interface ProjectRepository {
    fun observeProjects(includeArchived: Boolean = false): Flow<List<Project>>
    suspend fun upsert(project: Project): Long
    suspend fun archive(id: Long)
    suspend fun delete(id: Long)
}

interface TagRepository {
    fun observeTags(): Flow<List<Tag>>
    /** Возвращает id существующего тега или создаёт новый — нужно быстрому вводу. */
    suspend fun ensureTag(name: String): Long
    suspend fun delete(id: Long)
}
