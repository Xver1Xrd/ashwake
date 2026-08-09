package dev.ashwake.domain.usecase.habits

import dev.ashwake.core.time.FixedAppClock
import dev.ashwake.domain.model.habits.AnchorType
import dev.ashwake.domain.model.habits.EntryStatus
import dev.ashwake.domain.model.habits.Habit
import dev.ashwake.domain.model.habits.HabitAnchor
import dev.ashwake.domain.model.habits.HabitEntry
import dev.ashwake.domain.model.habits.HabitWithProgress
import dev.ashwake.domain.scheduler.HabitReminderScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * Якоря: «делай Б после того, как сделал А» (п. 6 ТЗ).
 *
 * Раньше якорь можно было завести в редакторе, он сохранялся в базу — и
 * никогда не срабатывал. Тесты проверяют именно срабатывание, а не запись.
 */
class FireAnchorsUseCaseTest {

    private val today = LocalDate.of(2026, 3, 12)
    private val clock = FixedAppClock(
        Instant.parse("2026-03-12T09:00:00Z")
    )

    @Test
    fun `отметка привычки-триггера планирует напоминание связанной привычки`() = runTest {
        val repo = FakeHabitRepository(
            habits = listOf(habit(1, "Зарядка"), habit(2, "Растяжка")),
            anchors = listOf(anchor(id = 10, habitId = 2, refHabitId = 1, delay = 15))
        )
        val scheduler = RecordingScheduler()
        val useCase = FireAnchorsUseCase(repo, scheduler, clock)

        useCase.onHabitDone(habitId = 1)

        assertEquals(listOf(2L to 15), scheduler.anchored)
    }

    @Test
    fun `якорь не срабатывает повторно в тот же день`() = runTest {
        val repo = FakeHabitRepository(
            habits = listOf(habit(1, "Зарядка"), habit(2, "Растяжка")),
            anchors = listOf(anchor(id = 10, habitId = 2, refHabitId = 1, delay = 15))
        )
        val scheduler = RecordingScheduler()
        val useCase = FireAnchorsUseCase(repo, scheduler, clock)

        useCase.onHabitDone(habitId = 1)
        useCase.onHabitDone(habitId = 1)

        assertEquals(1, scheduler.anchored.size)
        assertEquals(today, repo.firedAt[10L])
    }

    @Test
    fun `цепочка из трёх якорей отрабатывает по порядку`() = runTest {
        val repo = FakeHabitRepository(
            habits = listOf(habit(1, "Зарядка"), habit(2, "Растяжка"), habit(3, "Душ")),
            anchors = listOf(
                anchor(id = 10, habitId = 2, refHabitId = 1, delay = 5),
                anchor(id = 11, habitId = 3, refHabitId = 2, delay = 10)
            )
        )
        val scheduler = RecordingScheduler()
        val useCase = FireAnchorsUseCase(repo, scheduler, clock)

        // Отметили первую — разбудили вторую
        useCase.onHabitDone(habitId = 1)
        // Пришло напоминание, человек отметил вторую — разбудили третью
        useCase.onHabitDone(habitId = 2)

        assertEquals(listOf(2L to 5, 3L to 10), scheduler.anchored)
    }

    @Test
    fun `уже отмеченную сегодня привычку якорь не будит`() = runTest {
        val repo = FakeHabitRepository(
            habits = listOf(habit(1, "Зарядка"), habit(2, "Растяжка")),
            anchors = listOf(anchor(id = 10, habitId = 2, refHabitId = 1, delay = 15)),
            doneToday = setOf(2L)
        )
        val scheduler = RecordingScheduler()
        val useCase = FireAnchorsUseCase(repo, scheduler, clock)

        useCase.onHabitDone(habitId = 1)

        assertTrue(scheduler.anchored.isEmpty())
    }

    @Test
    fun `архивная привычка не будится`() = runTest {
        val repo = FakeHabitRepository(
            habits = listOf(habit(1, "Зарядка"), habit(2, "Растяжка", archived = true)),
            anchors = listOf(anchor(id = 10, habitId = 2, refHabitId = 1, delay = 15))
        )
        val scheduler = RecordingScheduler()
        val useCase = FireAnchorsUseCase(repo, scheduler, clock)

        useCase.onHabitDone(habitId = 1)

        assertTrue(scheduler.anchored.isEmpty())
    }

    @Test
    fun `якорь рутины будит привязанную привычку`() = runTest {
        val repo = FakeHabitRepository(
            habits = listOf(habit(2, "Растяжка")),
            anchors = listOf(
                HabitAnchor(
                    id = 20,
                    habitId = 2,
                    type = AnchorType.ROUTINE_DONE,
                    refRoutineId = 7,
                    delayMinutes = 3
                )
            )
        )
        val scheduler = RecordingScheduler()
        val useCase = FireAnchorsUseCase(repo, scheduler, clock)

        useCase.onRoutineDone(routineId = 7)

        assertEquals(listOf(2L to 3), scheduler.anchored)
    }

    @Test
    fun `якорь тега будит привычку после закрытия задачи`() = runTest {
        val repo = FakeHabitRepository(
            habits = listOf(habit(2, "Разбор почты")),
            anchors = listOf(
                HabitAnchor(
                    id = 30,
                    habitId = 2,
                    type = AnchorType.TASK_TAG_DONE,
                    refTagId = 5,
                    delayMinutes = 0
                )
            )
        )
        val scheduler = RecordingScheduler()
        val useCase = FireAnchorsUseCase(repo, scheduler, clock)

        useCase.onTaskWithTagDone(tagId = 5)

        assertEquals(listOf(2L to 0), scheduler.anchored)
    }

    // --- вспомогательное -----------------------------------------------------

    private fun habit(id: Long, name: String, archived: Boolean = false) =
        Habit(id = id, name = name, archived = archived)

    private fun anchor(id: Long, habitId: Long, refHabitId: Long, delay: Int) = HabitAnchor(
        id = id,
        habitId = habitId,
        type = AnchorType.HABIT_DONE,
        refHabitId = refHabitId,
        delayMinutes = delay
    )

    private class RecordingScheduler : HabitReminderScheduler {
        val anchored = mutableListOf<Pair<Long, Int>>()

        override fun schedule(habit: Habit) = Unit
        override fun cancel(habitId: Long) = Unit
        override fun snooze(habitId: Long, minutes: Int) = Unit
        override suspend fun rescheduleAll() = Unit

        override fun scheduleAnchored(habitId: Long, delayMinutes: Int) {
            anchored += habitId to delayMinutes
        }
    }

    /**
     * Фейк ровно на те методы, которые нужны якорям. Поднимать здесь Room
     * незачем: проверяется решение «будить или нет», а не SQL.
     */
    private class FakeHabitRepository(
        private val habits: List<Habit>,
        private var anchors: List<HabitAnchor>,
        private val doneToday: Set<Long> = emptySet()
    ) : StubHabitRepository() {

        val firedAt = mutableMapOf<Long, LocalDate>()

        override suspend fun getHabit(id: Long): Habit? = habits.firstOrNull { it.id == id }

        override suspend fun progressFor(habitId: Long, date: LocalDate): HabitWithProgress? {
            val habit = habits.firstOrNull { it.id == habitId } ?: return null
            val entry = if (habitId in doneToday) {
                HabitEntry(habitId = habitId, date = date, status = EntryStatus.DONE)
            } else null
            return HabitWithProgress(habit = habit, todayEntry = entry)
        }

        override suspend fun anchorsTriggeredByHabit(
            habitId: Long,
            today: LocalDate
        ): List<HabitAnchor> = anchors.filter {
            it.type == AnchorType.HABIT_DONE &&
                it.refHabitId == habitId &&
                (it.lastFiredDate == null || it.lastFiredDate < today)
        }

        override suspend fun anchorsTriggeredByRoutine(
            routineId: Long,
            today: LocalDate
        ): List<HabitAnchor> = anchors.filter {
            it.type == AnchorType.ROUTINE_DONE &&
                it.refRoutineId == routineId &&
                (it.lastFiredDate == null || it.lastFiredDate < today)
        }

        override suspend fun anchorsTriggeredByTag(
            tagId: Long,
            today: LocalDate
        ): List<HabitAnchor> = anchors.filter {
            it.type == AnchorType.TASK_TAG_DONE &&
                it.refTagId == tagId &&
                (it.lastFiredDate == null || it.lastFiredDate < today)
        }

        override suspend fun markAnchorFired(anchorId: Long, date: LocalDate) {
            firedAt[anchorId] = date
            anchors = anchors.map {
                if (it.id == anchorId) it.copy(lastFiredDate = date) else it
            }
        }
    }
}
