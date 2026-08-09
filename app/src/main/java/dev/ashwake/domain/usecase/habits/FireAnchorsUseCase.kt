package dev.ashwake.domain.usecase.habits

import dev.ashwake.core.time.AppClock
import dev.ashwake.domain.model.habits.AnchorType
import dev.ashwake.domain.repository.habits.HabitRepository
import dev.ashwake.domain.scheduler.HabitReminderScheduler
import javax.inject.Inject

/**
 * Якоря привычек (п. 6 ТЗ): «делай Б после того, как сделал А».
 *
 * Привязка к уже существующему действию — самый рабочий способ завести
 * новую привычку, поэтому якорь здесь не подсказка в интерфейсе, а
 * настоящее напоминание: отметил зарядку — через заданную задержку пришло
 * уведомление о растяжке.
 *
 * Цепочка получается сама собой: напоминание о Б приводит к отметке Б,
 * отметка Б снова проходит через этот же use case и будит В. Ограничитель
 * один — якорь срабатывает не чаще раза в сутки, иначе повторные тапы
 * по привычке-триггеру превращались бы в очередь уведомлений.
 */
class FireAnchorsUseCase @Inject constructor(
    private val habits: HabitRepository,
    private val scheduler: HabitReminderScheduler,
    private val clock: AppClock
) {

    /** Отмечена привычка [habitId] — будим всё, что на неё завязано. */
    suspend fun onHabitDone(habitId: Long) =
        fire(habits.anchorsTriggeredByHabit(habitId, clock.today()))

    /** Завершена рутина [routineId]. */
    suspend fun onRoutineDone(routineId: Long) =
        fire(habits.anchorsTriggeredByRoutine(routineId, clock.today()))

    /** Закрыта задача с тегом [tagId]. */
    suspend fun onTaskWithTagDone(tagId: Long) =
        fire(habits.anchorsTriggeredByTag(tagId, clock.today()))

    private suspend fun fire(anchors: List<dev.ashwake.domain.model.habits.HabitAnchor>) {
        val today = clock.today()
        anchors.forEach { anchor ->
            val target = habits.getHabit(anchor.habitId) ?: return@forEach
            if (target.archived) return@forEach

            // Уже отмеченную сегодня привычку будить незачем: напоминание
            // о сделанном раздражает сильнее, чем отсутствие напоминания
            val progress = habits.progressFor(anchor.habitId, today)
            if (progress?.doneToday == true) return@forEach

            scheduler.scheduleAnchored(anchor.habitId, anchor.delayMinutes)
            habits.markAnchorFired(anchor.id, today)
        }
    }

    companion object {
        /** Типы, которые умеет будить этот use case. */
        val SUPPORTED = setOf(
            AnchorType.HABIT_DONE,
            AnchorType.ROUTINE_DONE,
            AnchorType.TASK_TAG_DONE
        )
    }
}
