package dev.ashwake.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
import java.io.File

/**
 * База нужной версии, собранная по выгруженной схеме.
 *
 * Room умеет `MigrationTestHelper`, но он читает схемы из assets тестового
 * apk и живёт в инструментальных тестах. Здесь схема берётся прямо из
 * json-файлов в `app/schemas` — тех самых файлов, которые Room выгружает при сборке
 * и которые лежат в репозитории. Тест от этого проверяет ровно то, что нужно:
 * что миграция доводит **настоящую старую базу** до текущей схемы.
 *
 * Файлы схем — не тестовые данные, а часть исходников: если версия базы
 * выросла, а json рядом не появился, это ошибка сборки, а не теста.
 */
object SchemaFixture {

    /** Каталог со схемами. Ищется от рабочего каталога вверх, до корня репозитория. */
    private val schemaDir: File by lazy {
        var dir: File? = File(System.getProperty("user.dir").orEmpty()).absoluteFile
        while (dir != null) {
            val candidate = File(dir, "app/schemas/${AshwakeDatabase::class.java.canonicalName}")
            if (candidate.isDirectory) return@lazy candidate
            val here = File(dir, "schemas/${AshwakeDatabase::class.java.canonicalName}")
            if (here.isDirectory) return@lazy here
            dir = dir.parentFile
        }
        error("не найден каталог схем: Room выгружает их при сборке в app/schemas")
    }

    fun schema(version: Int): JSONObject {
        val file = File(schemaDir, "$version.json")
        check(file.isFile) { "нет схемы версии $version: ${file.absolutePath}" }
        return JSONObject(file.readText()).getJSONObject("database")
    }

    /** Самая свежая выгруженная версия. По ней же сверяется версия в коде. */
    fun latestVersion(): Int =
        schemaDir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { it.nameWithoutExtension.toIntOrNull() }
            ?.maxOrNull()
            ?: error("в каталоге схем нет ни одного json")

    /**
     * Создаёт на диске базу указанной версии — пустую, но с полной схемой
     * и служебной записью Room об идентичности схемы. Без неё Room при
     * открытии решит, что базу создали мимо него.
     */
    fun createDatabase(context: Context, version: Int, name: String): SQLiteDatabase {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        file.delete()

        val schema = schema(version)
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        db.beginTransaction()
        try {
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    db.execSQL(
                        indices.getJSONObject(j).getString("createSql")
                            .replace("\${TABLE_NAME}", table)
                    )
                }
            }
            val views = schema.optJSONArray("views")
            for (i in 0 until (views?.length() ?: 0)) {
                val view = views!!.getJSONObject(i)
                db.execSQL(
                    view.getString("createSql").replace("\${VIEW_NAME}", view.getString("viewName"))
                )
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) {
                db.execSQL(setup.getString(i))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        db.version = version
        return db
    }
}
