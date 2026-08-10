package dev.ashwake.domain.usecase.habits

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.ashwake.core.model.Sphere
import dev.ashwake.core.time.FixedAppClock
import dev.ashwake.data.assets.CatalogLoader
import dev.ashwake.data.db.AshwakeDatabase
import dev.ashwake.data.repository.character.CharacterRepositoryImpl
import dev.ashwake.domain.engine.character.EquipmentEngine
import dev.ashwake.domain.engine.character.ItemBudgetValidator
import dev.ashwake.domain.engine.character.StatProgressCalculator
import dev.ashwake.domain.engine.reward.RewardConfig
import dev.ashwake.domain.engine.reward.RewardEngine
import dev.ashwake.domain.model.habits.EntryStatus
import dev.ashwake.domain.model.habits.Habit
import dev.ashwake.domain.model.habits.HabitWithProgress
import dev.ashwake.domain.repository.character.CharacterRepository
import dev.ashwake.domain.repository.habits.HabitRepository
import dev.ashwake.domain.scheduler.HabitReminderScheduler
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Снятие отметки привычки забирает начисленное.
 *
 * Дыра та же, что была у задачи: отметить — начислили, снять — не забрали,
 * отметить снова — начислили опять. Отдельный тест нужен потому, что у
 * привычки начисление привязано к дню: отмена сегодняшней отметки не имеет
 * права трогать то, что начислено за вчера.
 */
@RunWith(RobolectricTestRunner::class)
class HabitRewardRevocationTest {

    private lateinit var db: AshwakeDatabase
    private lateinit var character: CharacterRepository
    private lateinit var habits: RecordingHabitRepository
    private lateinit var mark: MarkHabitUseCase
    private lateinit var clearMark: ClearHabitMarkUseCase

    private val today = LocalDate.of(2026, 5, 20)
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

        character = CharacterRepositoryImpl(
            db = db,
            dao = db.characterDao(),
            catalogLoader = CatalogLoader(context, ItemBudgetValidator()),
            equipmentEngine = EquipmentEngine(),
            statCalculator = StatProgressCalculator(),
            rewardEngine = RewardEngine(RewardConfig()),
            clock = clock
        )
        habits = RecordingHabitRepository()
        mark = MarkHabitUseCase(
            habits = habits,
            character = character,
            fireAnchors = FireAnchorsUseCase(habits, NoopHabitScheduler(), clock),
            clock = clock
        )
        clearMark = ClearHabitMarkUseCase(habits, character, clock)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `снятие отметки забирает монеты за неё`() = runTest {
        character.ensureBuiltinData()
        val start = coins()

        mark(progress(), EntryStatus.DONE, date = today)
        assertTrue("отметка должна что-то начислять", coins() > start)

        clearMark(HABIT_ID, today)
        assertEquals(start, coins())
    }

    @Test
    fun `многократная отметка и снятие не накручивают баланс`() = runTest {
        character.ensureBuiltinData()

        mark(progress(), EntryStatus.DONE, date = today)
        val afterFirst = coins()

        repeat(10) {
            clearMark(HABIT_ID, today)
            mark(progress(), EntryStatus.DONE, date = today)
        }

        assertEquals(afterFirst, coins())
    }

    @Test
    fun `снятие сегодняшней отметки не трогает вчерашнюю`() = runTest {
        character.ensureBuiltinData()
        val yesterday = today.minusDays(1)

        mark(progress(), EntryStatus.DONE, date = yesterday)
        val afterYesterday = coins()

        mark(progress(), EntryStatus.DONE, date = today)
        clearMark(HABIT_ID, today)

        assertEquals(afterYesterday, coins())
    }

    private suspend fun coins(): Long = character.state().wallet.coins

    /** Прогресс без отметки: use case сам решает, начислять ли, по этому полю. */
    private fun progress() = HabitWithProgress(
        habit = Habit(id = HABIT_ID, name = "Зарядка", sphere = Sphere.SPORT),
        score = 0.5f,
        todayEntry = null,
        dueToday = true
    )

    /**
     * Репозиторий, который только запоминает отметки: сама запись привычек
     * здесь не проверяется, проверяется движение монет.
     */
    private class RecordingHabitRepository : StubHabitRepository() {
        val marks = mutableMapOf<LocalDate, EntryStatus>()

        override suspend fun mark(
            habitId: Long,
            date: LocalDate,
            status: EntryStatus,
            value: Float?,
            note: String?,
            skipReasonId: Long?,
            source: dev.ashwake.domain.model.habits.EntrySource
        ) {
            marks[date] = status
        }

        override suspend fun clearMark(habitId: Long, date: LocalDate) {
            marks.remove(date)
        }
    }

    private class NoopHabitScheduler : HabitReminderScheduler {
        override fun schedule(habit: Habit) = Unit
        override fun cancel(habitId: Long) = Unit
        override fun snooze(habitId: Long, minutes: Int) = Unit
        override fun scheduleAnchored(habitId: Long, delayMinutes: Int) = Unit
        override suspend fun rescheduleAll() = Unit
    }

    private companion object {
        const val HABIT_ID = 7L
    }
}
