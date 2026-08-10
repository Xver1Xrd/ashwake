package dev.ashwake.domain.usecase.tasks

import dev.ashwake.core.time.AppClock
import dev.ashwake.domain.engine.character.StatSource
import dev.ashwake.domain.engine.reward.RewardContext
import dev.ashwake.domain.engine.reward.RewardSource
import dev.ashwake.domain.model.tasks.TaskStatus
import dev.ashwake.domain.repository.character.CharacterRepository
import dev.ashwake.domain.repository.character.RewardScope
import dev.ashwake.domain.model.tasks.PostponeSource
import dev.ashwake.domain.model.tasks.Task
import dev.ashwake.domain.repository.tasks.TaskRepository
import dev.ashwake.domain.usecase.habits.FireAnchorsUseCase
import dev.ashwake.domain.scheduler.TaskReminderScheduler
import dev.ashwake.platform.widget.WidgetRefresher
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
    private val scheduler: TaskReminderScheduler,
    private val widgets: WidgetRefresher
) {
    suspend operator fun invoke(task: Task): Long {
        val id = tasks.upsert(task)
        tasks.getTask(id)?.let(scheduler::schedule)
        widgets.refreshTasks()
        return id
    }
}

class CompleteTaskUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val scheduler: TaskReminderScheduler,
    private val character: CharacterRepository,
    private val fireAnchors: FireAnchorsUseCase,
    private val widgets: WidgetRefresher,
    private val clock: AppClock
) {
    /** @return id следующего экземпляра серии, если задача повторяющаяся. */
    suspend operator fun invoke(taskId: Long): Long? {
        val before = tasks.getTask(taskId)
        // Повторное закрытие уже закрытой задачи не должно приносить монеты второй раз
        val alreadyDone = before?.status == TaskStatus.DONE

        val nextId = tasks.complete(taskId)
        scheduler.cancel(taskId)
        nextId?.let { id -> tasks.getTask(id)?.let(scheduler::schedule) }

        if (before != null && !alreadyDone) {
            // Якорь «после задачи с тегом»: закрыли задачу #работа —
            // напомнили о привычке, привязанной к концу рабочих дел
            before.tags.forEach { tag -> fireAnchors.onTaskWithTagDone(tag.id) }

            character.grantReward(
                RewardContext(
                    source = RewardSource.TASK_DONE,
                    priority = before.priority,
                    postponeCount = before.postponeCount,
                    time = clock.now().atZone(clock.zone()).toLocalTime()
                ),
                refId = taskId.toString()
            )
            val onTime = before.dueDate == null || before.dueDate >= clock.today()
            character.grantStatPoints(
                if (onTime) StatSource.TASK_ON_TIME else StatSource.TASK_DONE,
                refId = taskId.toString()
            )
            if (before.postponeCount >= STALE_TASK_THRESHOLD) {
                character.grantStatPoints(
                    StatSource.STALE_TASK_CLOSED, refId = taskId.toString()
                )
            }
        }
        // Виджет на экране обязан показывать то же, что список в приложении
        widgets.refreshTasks()
        widgets.refreshCharacter()
        return nextId
    }

    private companion object {
        const val STALE_TASK_THRESHOLD = 3
    }
}

/**
 * Возврат задачи в работу.
 *
 * Возврат обязан быть полной противоположностью закрытия, иначе закрытие
 * становится источником монет: нажал — начислили, вернул — не сняли, нажал
 * снова — начислили опять. Поэтому здесь не только меняется статус, но и
 * отменяется награда, и убирается экземпляр повтора, созданный тем самым
 * закрытием.
 */
class ReopenTaskUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val scheduler: TaskReminderScheduler,
    private val character: CharacterRepository,
    private val widgets: WidgetRefresher
) {
    suspend operator fun invoke(taskId: Long) {
        val before = tasks.getTask(taskId)
        // Не была закрыта — отменять нечего, остаётся обычная смена статуса
        val wasDone = before?.status == TaskStatus.DONE

        // Строго до возврата: порождённый экземпляр ищется по времени
        // закрытия, а возврат это время стирает
        if (wasDone) {
            tasks.discardSpawnedRecurrence(taskId)?.let(scheduler::cancel)
        }

        tasks.reopen(taskId)
        tasks.getTask(taskId)?.let(scheduler::schedule)

        if (wasDone) {
            character.revokeReward(RewardScope.TASK, taskId.toString())
        }
        widgets.refreshTasks()
        widgets.refreshCharacter()
    }
}

class PostponeTaskUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val scheduler: TaskReminderScheduler,
    private val widgets: WidgetRefresher,
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
        widgets.refreshTasks()
        return updated?.postponeCount ?: 0
    }
}

/**
 * Отмена последнего переноса.
 *
 * Пара к [PostponeTaskUseCase]: свайп применяется сразу, и без обратного
 * хода промах по нему стоит человеку потерянной задачи.
 */
class UndoPostponeUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val scheduler: TaskReminderScheduler,
    private val widgets: WidgetRefresher
) {
    suspend operator fun invoke(taskId: Long): Boolean {
        val undone = tasks.undoLastPostpone(taskId)
        if (undone) {
            tasks.getTask(taskId)?.let(scheduler::schedule)
            widgets.refreshTasks()
        }
        return undone
    }
}

class DeleteTaskUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val scheduler: TaskReminderScheduler,
    private val widgets: WidgetRefresher
) {
    suspend operator fun invoke(taskId: Long) {
        scheduler.cancel(taskId)
        tasks.delete(taskId)
        widgets.refreshTasks()
    }
}
