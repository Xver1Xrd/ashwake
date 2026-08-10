package dev.ashwake.domain.usecase.tasks

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.ashwake.core.model.Priority
import dev.ashwake.core.time.FixedAppClock
import dev.ashwake.data.assets.CatalogLoader
import dev.ashwake.data.db.AshwakeDatabase
import dev.ashwake.data.repository.character.CharacterRepositoryImpl
import dev.ashwake.data.repository.tasks.TagRepositoryImpl
import dev.ashwake.data.repository.tasks.TaskRepositoryImpl
import dev.ashwake.domain.engine.character.EquipmentEngine
import dev.ashwake.domain.engine.character.ItemBudgetValidator
import dev.ashwake.domain.engine.character.StatProgressCalculator
import dev.ashwake.domain.engine.reward.RewardConfig
import dev.ashwake.domain.engine.reward.RewardEngine
import dev.ashwake.domain.engine.tasks.RecurrenceCalculator
import dev.ashwake.domain.model.habits.Habit
import dev.ashwake.domain.model.tasks.RecurrenceRule
import dev.ashwake.domain.model.tasks.RecurrenceType
import dev.ashwake.domain.model.tasks.Task
import dev.ashwake.domain.repository.character.CharacterRepository
import dev.ashwake.domain.repository.tasks.TaskRepository
import dev.ashwake.domain.scheduler.HabitReminderScheduler
import dev.ashwake.domain.scheduler.TaskReminderScheduler
import dev.ashwake.domain.usecase.habits.FireAnchorsUseCase
import dev.ashwake.domain.usecase.habits.StubHabitRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Закрытие задачи обратимо, значит и награда за него обратима.
 *
 * Баг, из-за которого тест появился: возврат задачи в работу не снимал
 * начисленное, а повторное закрытие начисляло снова. Достаточно было долбить
 * по чекбоксу, чтобы получить сколько угодно монет — вся экономика с
 * магазином и характеристиками при этом обесценивалась.
 *
 * База настоящая, in-memory: проверяется в том числе то, что встречная
 * запись в журнал считается правильно, а этого на подделке DAO не видно.
 */
@RunWith(RobolectricTestRunner::class)
class TaskRewardRevocationTest {

    private lateinit var db: AshwakeDatabase
    private lateinit var tasks: TaskRepository
    private lateinit var character: CharacterRepository
    private lateinit var complete: CompleteTaskUseCase
    private lateinit var reopen: ReopenTaskUseCase

    private val clock = FixedAppClock(
        Instant.parse("2026-05-20T10:00:00Z"),
        ZoneId.of("UTC")
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AshwakeDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        tasks = TaskRepositoryImpl(
            db = db,
            dao = db.taskDao(),
            tags = TagRepositoryImpl(db.tagDao()),
            clock = clock,
            recurrenceCalculator = RecurrenceCalculator()
        )
        character = CharacterRepositoryImpl(
            db = db,
            dao = db.characterDao(),
            catalogLoader = CatalogLoader(context, ItemBudgetValidator()),
            equipmentEngine = EquipmentEngine(),
            statCalculator = StatProgressCalculator(),
            rewardEngine = RewardEngine(RewardConfig()),
            clock = clock
        )

        val scheduler = NoopTaskScheduler()
        val anchors = FireAnchorsUseCase(
            habits = object : StubHabitRepository() {},
            scheduler = NoopHabitScheduler(),
            clock = clock
        )
        complete = CompleteTaskUseCase(tasks, scheduler, character, anchors, clock)
        reopen = ReopenTaskUseCase(tasks, scheduler, character)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `возврат задачи в работу снимает начисленные монеты`() = runTest {
        character.ensureBuiltinData()
        val start = coins()
        val taskId = tasks.upsert(Task(title = "Позвонить в банк", priority = Priority.P1))

        complete(taskId)
        val afterComplete = coins()
        assertTrue("закрытие должно что-то начислять", afterComplete > start)

        reopen(taskId)
        assertEquals(start, coins())
    }

    @Test
    fun `многократное закрытие и возврат не накручивают баланс`() = runTest {
        character.ensureBuiltinData()
        val taskId = tasks.upsert(Task(title = "Позвонить в банк", priority = Priority.P1))

        complete(taskId)
        val afterFirst = coins()

        // Ровно то, что делает человек, долбящий по чекбоксу
        repeat(10) {
            reopen(taskId)
            complete(taskId)
        }

        assertEquals(afterFirst, coins())
    }

    @Test
    fun `возврат убирает экземпляр повтора, созданный закрытием`() = runTest {
        character.ensureBuiltinData()
        val taskId = tasks.upsert(
            Task(
                title = "Полить цветы",
                dueDate = LocalDate.of(2026, 5, 20),
                recurrence = RecurrenceRule(
                    type = RecurrenceType.DAILY,
                    startDate = LocalDate.of(2026, 5, 20)
                )
            )
        )

        val spawnedId = complete(taskId)
        assertTrue("повтор должен породить следующий экземпляр", spawnedId != null)

        reopen(taskId)
        assertNull("нетронутый экземпляр должен исчезнуть", tasks.getTask(spawnedId!!))
    }

    @Test
    fun `правленый экземпляр повтора остаётся при возврате`() = runTest {
        character.ensureBuiltinData()
        val taskId = tasks.upsert(
            Task(
                title = "Полить цветы",
                dueDate = LocalDate.of(2026, 5, 20),
                recurrence = RecurrenceRule(
                    type = RecurrenceType.DAILY,
                    startDate = LocalDate.of(2026, 5, 20)
                )
            )
        )
        val spawnedId = complete(taskId)!!

        // Человек успел переименовать следующий экземпляр — это уже его задача,
        // а не побочный эффект закрытия, и трогать её нельзя
        clock.set(Instant.parse("2026-05-20T12:00:00Z"))
        val spawned = tasks.getTask(spawnedId)!!
        tasks.upsert(spawned.copy(title = "Полить цветы и подкормить"))

        reopen(taskId)
        assertEquals("Полить цветы и подкормить", tasks.getTask(spawnedId)?.title)
    }

    private suspend fun coins(): Long = character.state().wallet.coins

    private class NoopTaskScheduler : TaskReminderScheduler {
        override fun schedule(task: Task) = Unit
        override fun cancel(taskId: Long) = Unit
        override suspend fun rescheduleAll() = Unit
    }

    private class NoopHabitScheduler : HabitReminderScheduler {
        override fun schedule(habit: Habit) = Unit
        override fun cancel(habitId: Long) = Unit
        override fun snooze(habitId: Long, minutes: Int) = Unit
        override fun scheduleAnchored(habitId: Long, delayMinutes: Int) = Unit
        override suspend fun rescheduleAll() = Unit
    }
}
