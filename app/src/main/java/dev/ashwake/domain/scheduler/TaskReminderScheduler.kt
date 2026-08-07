package dev.ashwake.domain.scheduler

import dev.ashwake.domain.model.tasks.Task

/**
 * Планировщик напоминаний по задачам.
 *
 * Интерфейс живёт в domain, реализация — в platform на AlarmManager.
 * Благодаря этому use case'ы, которые вызываются и из UI, и из обработчика
 * кнопок в уведомлении, не тянут за собой Android.
 */
interface TaskReminderScheduler {

    /** Ставит или переставляет напоминание. Задача без даты и времени просто отменяет своё. */
    fun schedule(task: Task)

    fun cancel(taskId: Long)

    /** Полный перезапуск после перезагрузки устройства: будильники её не переживают. */
    suspend fun rescheduleAll()
}
