package dev.ashwake.domain.usecase.tasks

import dev.ashwake.core.time.AppClock
import dev.ashwake.domain.model.tasks.PostponeSource
import dev.ashwake.domain.model.tasks.Task
import dev.ashwake.domain.repository.tasks.TaskRepository
import dev.ashwake.domain.scheduler.TaskReminderScheduler
import java.time.LocalDate
import javax.inject.Inject

/**
 * Операции над задачей, которые обязаны затрагивать не только базу.
 *
 * Любая правка срока или статуса тянет за собой перестановку будильника.
 * Если бы это жило в ViewModel, закрытие задачи из уведомления или виджета
 * оставляло бы висеть напоминание — поэтому точка входа одна для всех.
 */

class SaveTaskUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val scheduler: TaskReminderScheduler
) {
    suspend operator fun invoke(task: Task): Long {
        val id = tasks.upsert(task)
        tasks.getTask(id)?.let(scheduler::schedule)
        return id
    }
}

class CompleteTaskUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val scheduler: TaskReminderScheduler
) {
    /** @return id следующего экземпляра серии, если задача повторяющаяся. */
    suspend operator fun invoke(taskId: Long): Long? {
        val nextId = tasks.complete(taskId)
        scheduler.cancel(taskId)
        nextId?.let { id -> tasks.getTask(id)?.let(scheduler::schedule) }
        return nextId
    }
}

class ReopenTaskUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val scheduler: TaskReminderScheduler
) {
    suspend operator fun invoke(taskId: Long) {
        tasks.reopen(taskId)
        tasks.getTask(taskId)?.let(scheduler::schedule)
    }
}

class PostponeTaskUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val scheduler: TaskReminderScheduler,
    private val clock: AppClock
) {
    /**
     * @return число переносов после операции — по нему UI решает,
     * пора ли поднимать диалог разбора завала (5 переносов, п. 1).
     */
    suspend operator fun invoke(
        taskId: Long,
        toDate: LocalDate? = null,
        source: PostponeSource = PostponeSource.SWIPE
    ): Int {
        val target = toDate ?: clock.today().plusDays(1)
        tasks.postpone(taskId, target, source)
        val updated = tasks.getTask(taskId)
        updated?.let(scheduler::schedule)
        return updated?.postponeCount ?: 0
    }
}

class DeleteTaskUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val scheduler: TaskReminderScheduler
) {
    suspend operator fun invoke(taskId: Long) {
        scheduler.cancel(taskId)
        tasks.delete(taskId)
    }
}
