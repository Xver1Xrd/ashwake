package dev.ashwake.domain.repository.timebox

import dev.ashwake.domain.engine.timebox.PlanResult
import dev.ashwake.domain.model.tasks.TimeboxDay
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface TimeboxRepository {

    fun observeDay(date: LocalDate): Flow<TimeboxDay>

    /** Кнопка «Разложить день» (п. 2). */
    suspend fun planDay(date: LocalDate): PlanResult

    suspend fun moveBlock(blockId: Long, newStartMinute: Int)

    /** Фиксация блока: планировщик его больше не трогает. */
    suspend fun setPinned(blockId: Long, pinned: Boolean)

    suspend fun deleteBlock(blockId: Long)

    suspend fun clearDay(date: LocalDate)

    /** Задача заняла больше времени, чем оценка — сдвигаем остаток дня. */
    suspend fun reflow(date: LocalDate, blockId: Long, actualEndMinute: Int)
}
