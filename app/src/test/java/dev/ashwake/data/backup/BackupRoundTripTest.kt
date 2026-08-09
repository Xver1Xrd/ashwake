package dev.ashwake.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.ashwake.data.db.AshwakeDatabase
import dev.ashwake.data.db.entity.abstinence.AbstinenceAttemptEntity
import dev.ashwake.data.db.entity.abstinence.AbstinenceEntity
import dev.ashwake.data.db.entity.character.CharacterStatEntity
import dev.ashwake.data.db.entity.character.EquippedItemEntity
import dev.ashwake.data.db.entity.character.OwnedItemEntity
import dev.ashwake.data.db.entity.character.WalletEntity
import dev.ashwake.data.db.entity.habits.HabitEntity
import dev.ashwake.data.db.entity.habits.HabitEntryEntity
import dev.ashwake.data.db.entity.ritual.DailyReviewEntity
import dev.ashwake.data.db.entity.tasks.TaskEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Полный круг резервной копии: экспорт → удаление данных → восстановление.
 *
 * До этого восстановление умело только показать содержимое архива, а записи
 * в базу не делало вовсе — то есть резервная копия ничего не гарантировала.
 * Тест проверяет ровно сценарий 18 приёмки: история отметок, попытки отказов,
 * экипировка, апгрейды предметов и монеты должны вернуться.
 *
 * База настоящая, in-memory: подделывать DAO здесь бессмысленно, проверяется
 * в том числе то, что порядок очистки не спотыкается о внешние ключи.
 */
@RunWith(RobolectricTestRunner::class)
class BackupRoundTripTest {

    private lateinit var db: AshwakeDatabase
    private lateinit var serializer: JsonBackupSerializer

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AshwakeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        serializer = JsonBackupSerializer(
            db = db,
            taskDao = db.taskDao(),
            habitDao = db.habitDao(),
            abstinenceDao = db.abstinenceDao(),
            ritualDao = db.ritualDao(),
            characterDao = db.characterDao(),
            backupDao = db.backupDao()
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `данные возвращаются после полного удаления`() = runTest {
        seed()

        val (json, exported) = serializer.export()
        assertEquals(1, exported.tasks)
        assertEquals(1, exported.habits)
        assertEquals(3, exported.habitEntries)

        wipe()
        assertEquals(0, db.taskDao().observeTasks(1, null, null, null, null, 0, null).first().size)

        val restored = serializer.import(json)

        assertEquals(exported.tasks, restored.tasks)
        assertEquals(exported.habitEntries, restored.habitEntries)

        // История отметок — то, без чего score и heatmap не пересчитать
        val entries = db.habitDao().observeEntriesInRange(0, 47_500).first()
        assertEquals(3, entries.size)
        assertEquals(setOf(20_000, 20_001, 20_002), entries.map { it.date }.toSet())

        // Попытки отказов: рекорд и сумма чистых дней считаются по ним
        val attempts = db.abstinenceDao().observeAttempts().first()
        assertEquals(2, attempts.size)
        assertEquals(listOf(1, 2), attempts.map { it.ordinal }.sorted())

        // Экипировка и апгрейд предмета
        val equipped = db.characterDao().equipped()
        assertEquals(1, equipped.size)
        assertEquals("helm_iron", equipped.first().itemId)

        val owned = db.characterDao().observeOwned().first()
        assertEquals(1, owned.size)
        assertEquals(3, owned.first().upgradeLevel)

        // Монеты
        assertEquals(1234L, db.characterDao().wallet()?.coins)

        // Baseline отказа: без него блок экономии показывал бы нули
        val abstinence = db.abstinenceDao().observeAll(1).first().first()
        assertEquals(20f, abstinence.baselineUnitsPerDay)
        assertEquals("сигарета", abstinence.baselineUnitName)
    }

    @Test
    fun `восстановление заменяет данные, а не смешивает их`() = runTest {
        seed()
        val (json, _) = serializer.export()

        // Между экспортом и восстановлением появилась лишняя задача
        db.backupDao().insertTasks(
            listOf(task(id = 99, title = "Задача, которой не было в архиве"))
        )

        serializer.import(json)

        val tasks = db.taskDao().observeTasks(1, null, null, null, null, 0, null).first()
        assertEquals(1, tasks.size)
        assertEquals("Дописать отчёт", tasks.first().task.title)
    }

    @Test
    fun `архив первой версии со списком id предметов ещё читается`() = runTest {
        val legacy = """
            {
              "version": 1,
              "tasks": [],
              "habits": [],
              "habitEntries": [],
              "abstinences": [],
              "abstinenceAttempts": [],
              "dailyReviews": [],
              "wallet": {"coins": 10, "xp": 0},
              "ownedItems": ["helm_iron", "boots_leather"],
              "equippedItems": {},
              "stats": {"STRENGTH": 40}
            }
        """.trimIndent()

        serializer.import(legacy)

        val owned = db.characterDao().observeOwned().first()
        assertEquals(setOf("helm_iron", "boots_leather"), owned.map { it.itemId }.toSet())
        assertEquals(0, owned.first().upgradeLevel)

        val stat = db.characterDao().stat("STRENGTH")
        assertNotNull(stat)
        assertEquals(40L, stat?.points)
    }

    // --- данные для теста ----------------------------------------------------

    private suspend fun seed() {
        db.backupDao().insertTasks(listOf(task(id = 1, title = "Дописать отчёт")))
        db.backupDao().insertHabits(
            listOf(
                HabitEntity(
                    id = 1,
                    name = "Зарядка",
                    type = "CHECK",
                    sphere = "HEALTH",
                    scheduleType = "DAILY",
                    createdAt = 0
                )
            )
        )
        db.backupDao().insertHabitEntries(
            (0..2).map { offset ->
                HabitEntryEntity(
                    habitId = 1,
                    date = 20_000 + offset,
                    status = "DONE",
                    source = "MANUAL"
                )
            }
        )
        db.backupDao().insertAbstinences(
            listOf(
                AbstinenceEntity(
                    id = 1,
                    name = "Не курю",
                    mode = "STRICT",
                    baselineUnitName = "сигарета",
                    baselineUnitsPerDay = 20f,
                    baselineCostPerUnit = 12f,
                    baselineCurrency = "RUB",
                    createdAt = 0
                )
            )
        )
        db.backupDao().insertAttempts(
            listOf(
                AbstinenceAttemptEntity(
                    id = 1, abstinenceId = 1, ordinal = 1,
                    startedAt = 1_000, endedAt = 2_000
                ),
                AbstinenceAttemptEntity(
                    id = 2, abstinenceId = 1, ordinal = 2, startedAt = 2_000
                )
            )
        )
        db.backupDao().insertReviews(
            listOf(DailyReviewEntity(date = 20_002, mood = 4, energy = 3, completedAt = 5_000, completedAs = "EVENING"))
        )
        db.backupDao().upsertWallet(WalletEntity(id = 1, coins = 1234, xp = 500, level = 3))
        db.backupDao().insertOwned(
            listOf(
                OwnedItemEntity(
                    itemId = "helm_iron",
                    acquiredAt = 100,
                    source = "SHOP",
                    upgradeLevel = 3
                )
            )
        )
        db.backupDao().insertEquipped(listOf(EquippedItemEntity(slot = "HELMET", itemId = "helm_iron")))
        db.backupDao().insertStats(listOf(CharacterStatEntity(stat = "STRENGTH", points = 64, value = 4)))
    }

    private suspend fun wipe() {
        with(db.backupDao()) {
            clearHabitEntries()
            clearHabits()
            clearTasks()
            clearAttempts()
            clearAbstinences()
            clearReviews()
            clearEquipped()
            clearOwned()
            clearStats()
            upsertWallet(WalletEntity(id = 1, coins = 0, xp = 0, level = 1))
        }
    }

    private fun task(id: Long, title: String) = TaskEntity(
        id = id,
        title = title,
        priority = "P2",
        status = "ACTIVE",
        createdAt = 0,
        updatedAt = 0
    )
}
