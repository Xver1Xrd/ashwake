package dev.ashwake.data.db

import android.content.ContentValues
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Миграции доводят старую базу до текущей схемы, не теряя данных.
 *
 * Это самое дорогое из того, что может сломаться: у человека в базе история
 * за месяцы, и обновление, которое её роняет или стирает, ничем не лечится.
 * При этом в отладочной сборке стоит `fallbackToDestructiveMigration()` —
 * то есть на глазах у разработчика сломанная миграция молча сносит базу и
 * ничего не сообщает. Поймать это можно только тестом.
 *
 * База каждой версии собирается по выгруженной схеме из `app/schemas`, а не
 * по коду сущностей: код давно уехал вперёд, и построенная по нему «старая»
 * база проверяла бы миграцию сама на себе.
 */
@RunWith(RobolectricTestRunner::class)
class DatabaseMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `версия базы в коде совпадает с выгруженной схемой`() {
        // Схема выгружается при сборке. Если версию подняли, а json рядом не
        // появился, миграцию будет не на чем проверить — и это ошибка сейчас,
        // а не в момент, когда она понадобится
        assertEquals(
            "версия базы выросла, а схема не выгружена в app/schemas",
            SchemaFixture.latestVersion(),
            AshwakeDatabase.VERSION
        )
    }

    @Test
    fun `у каждой версии, кроме первой, есть миграция`() {
        val paths = AshwakeDatabase.MIGRATIONS.map { it.startVersion to it.endVersion }.toSet()
        for (from in 1 until SchemaFixture.latestVersion()) {
            assertTrue(
                "нет миграции $from → ${from + 1}: обновление с этой версии упадёт",
                (from to from + 1) in paths
            )
        }
    }

    @Test
    fun `база первой версии доезжает до текущей вместе с данными`() {
        val name = "migration-full.db"
        SchemaFixture.createDatabase(context, version = 1, name = name).use { old ->
            old.insert("tasks", null, taskRow(title = "Позвонить в банк"))
            old.insert("habits", null, habitRow(name = "Зарядка"))
            old.insert("abstinences", null, abstinenceRow(name = "Без сахара"))
        }

        withMigrated(name) { db ->
            assertEquals("Позвонить в банк", db.singleString("SELECT title FROM tasks"))
            assertEquals("Зарядка", db.singleString("SELECT name FROM habits"))
            assertEquals("Без сахара", db.singleString("SELECT name FROM abstinences"))
        }
    }

    @Test
    fun `новые столбцы появляются пустыми, а не отсутствуют`() {
        val name = "migration-columns.db"
        SchemaFixture.createDatabase(context, version = 1, name = name).use { old ->
            old.insert("tasks", null, taskRow(title = "Полить цветы"))
            old.insert("habits", null, habitRow(name = "Читать"))
        }

        withMigrated(name) { db ->
            // Столбцы 2→3 и 3→4: у старой задачи их не было, и после миграции
            // они обязаны быть пустыми, а не сломать чтение строки
            assertNull(db.singleString("SELECT emoji FROM tasks"))
            assertNull(db.singleString("SELECT iconPath FROM tasks"))
            assertNull(db.singleString("SELECT iconPath FROM habits"))
            assertEquals("1", db.singleString("SELECT difficulty FROM tasks"))
        }
    }

    @Test
    fun `локальный файл задачи переживает миграцию 7 в 8`() {
        val name = "migration-attachment.db"
        SchemaFixture.createDatabase(context, version = 7, name = name).use { old ->
            val taskId = old.insert("tasks", null, taskRow(title = "Документы", version = 7))
            // Снимок 7.json относится к онлайн-варианту; воспроизводим
            // опубликованный локальный вариант со старым именем поля.
            old.execSQL("DROP INDEX index_task_attachments_storedName")
            old.execSQL("ALTER TABLE task_attachments RENAME COLUMN storedName TO fileName")
            old.execSQL("ALTER TABLE task_attachments DROP COLUMN kind")
            old.insert("task_attachments", null, ContentValues().apply {
                put("taskId", taskId)
                put("fileName", "document.pdf")
                put("displayName", "Документ.pdf")
                put("mimeType", "application/pdf")
                put("sizeBytes", 42L)
                put("createdAt", NOW)
            })
        }

        withMigrated(name) { db ->
            assertEquals("Документы", db.singleString("SELECT title FROM tasks"))
            assertEquals("document.pdf", db.singleString("SELECT storedName FROM task_attachments"))
            assertEquals("FILE", db.singleString("SELECT kind FROM task_attachments"))
        }
    }

    @Test
    fun `каждая миграция применяется по отдельности`() {
        for (from in 1 until SchemaFixture.latestVersion()) {
            val name = "migration-step-$from.db"
            SchemaFixture.createDatabase(context, version = from, name = name).use { old ->
                old.insert("tasks", null, taskRow(title = "Шаг $from", version = from))
            }
            // Room сам сверяет получившуюся схему с ожидаемой и падает, если
            // миграция довела базу не туда, — отдельная проверка не нужна
            withMigrated(name) { db ->
                val count = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM tasks").use { cursor ->
                    cursor.moveToFirst()
                    cursor.getInt(0)
                }
                assertEquals("задача потерялась на миграции $from → ${from + 1}", 1, count)
                assertEquals("задача потерялась на миграции $from → ${from + 1}", "Шаг $from", db.singleString("SELECT title FROM tasks"))
            }
        }
    }

    @Test
    fun `вложения обеих схем версии 7 сохраняются при миграции`() {
        for (onlineSchema in listOf(false, true)) {
            val name = "migration-attachments-$onlineSchema.db"
            SchemaFixture.createDatabase(context, version = 7, name = name).use { old ->
                val taskId = old.insert("tasks", null, taskRow(title = "Фото", version = 7))
                if (!onlineSchema) {
                    old.execSQL("DROP INDEX index_task_attachments_storedName")
                    old.execSQL("ALTER TABLE task_attachments RENAME COLUMN storedName TO fileName")
                    old.execSQL("ALTER TABLE task_attachments DROP COLUMN kind")
                }
                val values = ContentValues().apply {
                    put("taskId", taskId)
                    put(if (onlineSchema) "storedName" else "fileName", "photo.jpg")
                    put("displayName", "Фото.jpg")
                    put("mimeType", "image/jpeg")
                    put("sizeBytes", 123L)
                    put("createdAt", 1234L)
                    if (onlineSchema) put("kind", "PHOTO")
                }
                old.insert("task_attachments", null, values)
            }
            withMigrated(name) { db ->
                assertEquals("photo.jpg", db.singleString("SELECT storedName FROM task_attachments"))
                assertEquals(if (onlineSchema) "PHOTO" else "FILE", db.singleString("SELECT kind FROM task_attachments"))
            }
        }
    }

    @Test
    fun `локальная схема 7 без онлайн полей сохраняет задачу`() {
        val name = "migration-local-v7.db"
        SchemaFixture.createDatabase(context, version = 7, name = name).use { old ->
            old.execSQL("DROP INDEX index_tasks_globalId")
            old.execSQL("DROP INDEX index_tasks_spaceId")
            listOf("globalId", "spaceId", "syncRevision", "syncState").forEach { column ->
                old.execSQL("ALTER TABLE tasks DROP COLUMN $column")
            }
            val row = taskRow(title = "Из старой версии", version = 7).apply {
                remove("syncRevision")
                remove("syncState")
            }
            old.insert("tasks", null, row)
        }
        withMigrated(name) { db ->
            assertEquals("Из старой версии", db.singleString("SELECT title FROM tasks"))
            assertEquals("LOCAL", db.singleString("SELECT syncState FROM tasks"))
        }
    }

    /**
     * Открывает базу через Room со всеми миграциями — ровно так, как это
     * делает приложение. Обращение к `writableDatabase` обязательно: Room
     * ленив, и без него миграция просто не запустится.
     */
    private fun <T> withMigrated(name: String, block: (AshwakeDatabase) -> T): T {
        val db = Room.databaseBuilder(context, AshwakeDatabase::class.java, name)
            .addMigrations(*AshwakeDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        return try {
            db.openHelper.writableDatabase
            block(db)
        } finally {
            db.close()
        }
    }

    private fun AshwakeDatabase.singleString(query: String): String? =
        openHelper.readableDatabase.query(query).use { cursor ->
            assertTrue("запрос не вернул ни одной строки: $query", cursor.moveToFirst())
            if (cursor.isNull(0)) null else cursor.getString(0)
        }

    private fun taskRow(title: String, version: Int = 1) = ContentValues().apply {
        put("title", title)
        put("priority", "P2")
        put("status", "ACTIVE")
        put("position", 0)
        put("isTemplate", 0)
        put("postponeCount", 0)
        put("createdAt", NOW)
        put("updatedAt", NOW)
        // Выгруженная схема 7 уже содержит обязательные онлайн-поля.
        // Без них SQLite отвергает тестовую строку, и тест миграции
        // ошибочно сообщает о «потере» записи, которой не было.
        if (version >= 6) {
            put("syncRevision", 0)
            put("syncState", "LOCAL")
        }
        if (version >= 7) put("difficulty", 1)
    }

    private fun habitRow(name: String) = ContentValues().apply {
        put("name", name)
        put("color", "ash")
        put("type", "CHECK")
        put("sphere", "HEALTH")
        put("targetValue", 1.0)
        put("scheduleType", "DAILY")
        put("timesPerWeek", 7)
        put("weekdaysMask", 127)
        put("freezeQuotaPerMonth", 2)
        put("position", 0)
        put("archived", 0)
        put("createdAt", NOW)
    }

    private fun abstinenceRow(name: String) = ContentValues().apply {
        put("name", name)
        put("paletteId", "ash")
        put("mode", "STRICT")
        put("gentlePenaltyDays", 0)
        put("milestonesEnabled", 1)
        put("stickyNotification", 0)
        put("substanceWarningAck", 0)
        put("position", 0)
        put("archived", 0)
        put("createdAt", NOW)
    }

    private companion object {
        const val NOW = 1_770_000_000_000L
    }
}
