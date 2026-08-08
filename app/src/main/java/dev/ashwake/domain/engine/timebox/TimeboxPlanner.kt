package dev.ashwake.domain.engine.timebox

import dev.ashwake.domain.model.tasks.BlockKind
import dev.ashwake.domain.model.tasks.BlockOrigin
import dev.ashwake.domain.model.tasks.Task
import dev.ashwake.domain.model.tasks.TimeboxBlock
import dev.ashwake.domain.model.tasks.TimeboxSettings
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Свободный интервал дня в минутах от полуночи. */
data class FreeSlot(val startMinute: Int, val endMinute: Int) {
    val durationMinutes: Int get() = (endMinute - startMinute).coerceAtLeast(0)
}

data class PlanRequest(
    val date: LocalDate,
    val tasks: List<Task>,
    /** Рутины, события календаря, обед и закреплённые блоки. */
    val busy: List<TimeboxBlock> = emptyList(),
    val settings: TimeboxSettings = TimeboxSettings(),
    /** Не планировать в прошлое: null — планируем весь рабочий день. */
    val nowMinute: Int? = null
)

data class PlanResult(
    val blocks: List<TimeboxBlock>,
    /** Что не влезло: показываем честно, а не молча выбрасываем. */
    val deferred: List<Task>,
    val deficitMinutes: Int,
    /** Задачи без оценки времени: планировать их не из чего. */
    val withoutEstimate: List<Task>,
    val freeMinutes: Int
) {
    val fitsCompletely: Boolean get() = deferred.isEmpty()
}

/**
 * Раскладка дня — кнопка «Разложить день» (п. 2).
 *
 * Чистый класс: ни времени системы, ни базы. «Сейчас» приходит параметром,
 * поэтому поведение в 9 утра и в 6 вечера проверяется тестом, а не ожиданием.
 *
 * Алгоритм намеренно жадный, а не оптимальный. Идеальная упаковка рюкзака
 * дала бы на пару процентов плотнее, но переставляла бы задачи местами
 * непредсказуемо для человека. Здесь порядок понятный: сначала срочное
 * и важное, каждая задача — в первый подходящий свободный слот.
 */
@Singleton
class TimeboxPlanner @Inject constructor() {

    fun plan(request: PlanRequest): PlanResult {
        val settings = request.settings
        val windowStart = maxOf(settings.workStartMinute, request.nowMinute ?: 0)
        val windowEnd = settings.workEndMinute

        val busy = withLunch(request.busy, settings, request.date)
        val slots = freeSlots(windowStart, windowEnd, busy).toMutableList()
        val freeMinutes = slots.sumOf { it.durationMinutes }

        // Задача с точным временем уже стоит в расписании сама — её не двигаем
        val (fixed, flexible) = request.tasks
            .filterNot { it.isDone }
            .partition { it.dueTime != null }

        val (withEstimate, withoutEstimate) = flexible.partition {
            (it.estimateMinutes ?: 0) > 0
        }

        val planned = mutableListOf<TimeboxBlock>()
        val deferred = mutableListOf<Task>()

        // Блоки задач с фиксированным временем добавляем как есть
        fixed.forEach { task ->
            val start = task.dueTime!!.hour * 60 + task.dueTime.minute
            planned += TimeboxBlock(
                date = request.date,
                startMinute = start,
                endMinute = start + (task.estimateMinutes ?: DEFAULT_FIXED_MINUTES),
                kind = BlockKind.TASK,
                title = task.title,
                taskId = task.id,
                pinned = true,
                createdBy = BlockOrigin.AUTO
            )
        }

        sortForPacking(withEstimate, request.date).forEach { task ->
            val duration = task.estimateMinutes ?: return@forEach
            val slotIndex = slots.indexOfFirst { it.durationMinutes >= duration }

            if (slotIndex < 0) {
                deferred += task
                return@forEach
            }

            val slot = slots[slotIndex]
            planned += TimeboxBlock(
                date = request.date,
                startMinute = slot.startMinute,
                endMinute = slot.startMinute + duration,
                kind = BlockKind.TASK,
                title = task.title,
                taskId = task.id,
                createdBy = BlockOrigin.AUTO
            )

            // Буфер съедается вместе с блоком: следующая задача не встык
            val nextStart = slot.startMinute + duration + settings.bufferMinutes
            if (nextStart < slot.endMinute) {
                slots[slotIndex] = FreeSlot(nextStart, slot.endMinute)
            } else {
                slots.removeAt(slotIndex)
            }
        }

        return PlanResult(
            blocks = (planned + busy).sortedBy { it.startMinute },
            deferred = deferred,
            deficitMinutes = deferred.sumOf { it.estimateMinutes ?: 0 },
            withoutEstimate = withoutEstimate,
            freeMinutes = freeMinutes
        )
    }

    /**
     * Свободные интервалы окна за вычетом занятых.
     * Пересекающиеся занятые интервалы сливаются: два наложившихся события
     * календаря не должны порождать слот отрицательной длины.
     */
    fun freeSlots(
        windowStart: Int,
        windowEnd: Int,
        busy: List<TimeboxBlock>
    ): List<FreeSlot> {
        if (windowEnd <= windowStart) return emptyList()

        val merged = mutableListOf<FreeSlot>()
        busy.map { FreeSlot(it.startMinute, it.effectiveEndMinute) }
            .filter { it.endMinute > windowStart && it.startMinute < windowEnd }
            .sortedBy { it.startMinute }
            .forEach { interval ->
                val last = merged.lastOrNull()
                if (last != null && interval.startMinute <= last.endMinute) {
                    merged[merged.lastIndex] =
                        FreeSlot(last.startMinute, maxOf(last.endMinute, interval.endMinute))
                } else {
                    merged += interval
                }
            }

        val result = mutableListOf<FreeSlot>()
        var cursor = windowStart
        merged.forEach { interval ->
            if (interval.startMinute > cursor) {
                result += FreeSlot(cursor, minOf(interval.startMinute, windowEnd))
            }
            cursor = maxOf(cursor, interval.endMinute)
        }
        if (cursor < windowEnd) result += FreeSlot(cursor, windowEnd)

        return result.filter { it.durationMinutes > 0 }
    }

    /**
     * Пересчёт на лету: задача заняла больше времени, чем оценка (п. 2).
     *
     * Сдвигаются только незакреплённые блоки после изменившегося.
     * Закреплённый блок остаётся на месте — в этом весь смысл «не двигать»,
     * даже если из-за этого блоки наложатся: врать про расписание хуже,
     * чем показать конфликт.
     */
    fun reflow(
        blocks: List<TimeboxBlock>,
        changedBlockId: Long,
        newEndMinute: Int,
        settings: TimeboxSettings
    ): List<TimeboxBlock> {
        val sorted = blocks.sortedBy { it.startMinute }
        val changedIndex = sorted.indexOfFirst { it.id == changedBlockId }
        if (changedIndex < 0) return blocks

        val result = sorted.toMutableList()
        result[changedIndex] = result[changedIndex].copy(actualEndMinute = newEndMinute)

        var cursor = newEndMinute + settings.bufferMinutes
        for (index in (changedIndex + 1) until result.size) {
            val block = result[index]
            if (block.pinned) {
                cursor = maxOf(cursor, block.endMinute + settings.bufferMinutes)
                continue
            }
            if (block.startMinute >= cursor) break

            val duration = block.durationMinutes
            result[index] = block.copy(startMinute = cursor, endMinute = cursor + duration)
            cursor += duration + settings.bufferMinutes
        }
        return result
    }

    /** Перетаскивание блока: время меняется, длительность сохраняется. */
    fun move(
        blocks: List<TimeboxBlock>,
        blockId: Long,
        newStartMinute: Int,
        settings: TimeboxSettings
    ): List<TimeboxBlock> = blocks.map { block ->
        if (block.id != blockId) block
        else {
            val duration = block.durationMinutes
            val start = newStartMinute.coerceIn(
                0,
                (settings.workEndMinute - duration).coerceAtLeast(0)
            )
            block.copy(
                startMinute = start,
                endMinute = start + duration,
                createdBy = BlockOrigin.MANUAL
            )
        }
    }

    /**
     * Порядок упаковки: сначала просроченное и сегодняшнее, потом по приоритету,
     * при равенстве — длинное вперёд. Длинное первым потому, что короткие задачи
     * потом легко разложить по остаткам, а наоборот не выйдет.
     */
    private fun sortForPacking(tasks: List<Task>, date: LocalDate): List<Task> =
        tasks.sortedWith(
            compareBy<Task> { task ->
                when {
                    task.dueDate == null -> 2
                    task.dueDate <= date -> 0
                    else -> 1
                }
            }
                .thenBy { it.priority.ordinal }
                .thenByDescending { it.estimateMinutes ?: 0 }
        )

    /** Обеденный перерыв — такой же занятый интервал, как встреча. */
    private fun withLunch(
        busy: List<TimeboxBlock>,
        settings: TimeboxSettings,
        date: LocalDate
    ): List<TimeboxBlock> {
        val start = settings.lunchStartMinute ?: return busy
        val end = settings.lunchEndMinute ?: return busy
        if (busy.any { it.kind == BlockKind.LUNCH }) return busy

        return busy + TimeboxBlock(
            date = date,
            startMinute = start,
            endMinute = end,
            kind = BlockKind.LUNCH,
            title = "Обед",
            pinned = true
        )
    }

    private companion object {
        /** Задаче с точным временем, но без оценки, отводим полчаса. */
        const val DEFAULT_FIXED_MINUTES = 30
    }
}
