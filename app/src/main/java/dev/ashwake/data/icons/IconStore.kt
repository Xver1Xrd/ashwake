package dev.ashwake.data.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.util.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Значки, выбранные из галереи.
 *
 * Картинка не остаётся ссылкой на файл в галерее: разрешение на чужой URI
 * живёт до перезагрузки, сам файл человек может удалить, а резервная копия
 * такую ссылку не увезёт. Поэтому выбранное сразу уменьшается до размера
 * значка и копируется в хранилище приложения — дальше это обычный файл,
 * за который отвечает приложение.
 *
 * Уменьшение обязательно: фотография с камеры это 4000×3000 и несколько
 * мегабайт, а в строке списка значок занимает тридцать точек.
 */
@Singleton
class IconStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val directory: File
        get() = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    /**
     * Декодированные значки. Список прокручивается, строки пересоздаются
     * постоянно, и читать файл на каждый кадр нельзя. Кэш маленький:
     * значков в приложении десятки, а не тысячи.
     */
    private val cache = LruCache<String, Bitmap>(CACHE_ENTRIES)

    /**
     * Копирует картинку из галереи и возвращает имя файла.
     * null — картинку не удалось прочитать: URI протух или это не изображение.
     */
    suspend fun save(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val source = decodeScaled(uri) ?: return@runCatching null
            val square = source.cropToSquare()
            val name = "${UUID.randomUUID()}.png"

            File(directory, name).outputStream().use { out ->
                square.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
            }
            if (square !== source) source.recycle()
            cache.put(name, square)
            name
        }.getOrElse { error ->
            Log.e(TAG, "Значок не сохранён", error)
            null
        }
    }

    /** Значок по имени файла. null — файла нет, строка покажет запасной вид. */
    fun load(name: String): Bitmap? {
        cache.get(name)?.let { return it }
        val file = File(directory, name)
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(file.path)?.also { cache.put(name, it) }
    }

    fun delete(name: String) {
        cache.remove(name)
        runCatching { File(directory, name).delete() }
    }

    /**
     * Убирает файлы, на которые больше никто не ссылается.
     *
     * Уборка сделана подметанием, а не удалением по месту: значок теряет
     * хозяина не только при удалении задачи, но и при замене картинки, при
     * отмене правки, при восстановлении из архива и при очистке корзины —
     * перечислять эти пути по одному значит однажды забыть новый.
     *
     * @return сколько файлов удалено
     */
    suspend fun sweep(used: Set<String>): Int = withContext(Dispatchers.IO) {
        val files = directory.listFiles().orEmpty()
        files.count { file ->
            if (file.name in used) return@count false
            cache.remove(file.name)
            file.delete()
        }
    }

    /**
     * Все значки в виде base64 — для резервной копии.
     *
     * Картинки едут внутри того же файла, а не отдельной папкой: архив
     * обещает быть одним файлом, который можно открыть и прочитать, и
     * ссылка на картинку без самой картинки восстанавливает пустой кружок.
     * Значок весит десятки килобайт, поэтому размер архива это переживёт.
     */
    suspend fun exportAll(names: Set<String>): Map<String, String> =
        withContext(Dispatchers.IO) {
            names.mapNotNull { name ->
                val file = File(directory, name)
                if (!file.exists()) return@mapNotNull null
                name to Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
            }.toMap()
        }

    /** Кладёт значки из архива на диск. Уже существующие не перезаписывает. */
    suspend fun importAll(icons: Map<String, String>) = withContext(Dispatchers.IO) {
        icons.forEach { (name, encoded) ->
            runCatching {
                val file = File(directory, name)
                if (file.exists()) return@runCatching
                file.writeBytes(Base64.decode(encoded, Base64.NO_WRAP))
                cache.remove(name)
            }.onFailure { Log.e(TAG, "Значок из архива не записан: $name", it) }
        }
    }

    /**
     * Читает картинку сразу уменьшенной. `inSampleSize` уменьшает при
     * декодировании, поэтому полноразмерный кадр в память вообще не попадает —
     * иначе выбор фотографии из галереи мог бы уронить приложение по памяти.
     */
    private fun decodeScaled(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        var side = min(width, height)
        while (side / 2 >= TARGET_SIZE) {
            side /= 2
            sample *= 2
        }
        return sample
    }

    /**
     * Квадрат по центру: значок рисуется в квадратной рамке, и обрезать
     * лучше здесь, один раз, чем растягивать картинку в каждой строке.
     */
    private fun Bitmap.cropToSquare(): Bitmap {
        val side = min(width, height)
        val cropped = Bitmap.createBitmap(
            this,
            (width - side) / 2,
            (height - side) / 2,
            side,
            side
        )
        val target = min(side, TARGET_SIZE)
        return if (side == target) {
            cropped
        } else {
            Bitmap.createScaledBitmap(cropped, target, target, true).also {
                if (it !== cropped) cropped.recycle()
            }
        }
    }

    private companion object {
        const val DIRECTORY = "icons"
        const val TAG = "IconStore"

        /** Сторона значка в точках хранения: хватает на любой размер в списке. */
        const val TARGET_SIZE = 192
        const val PNG_QUALITY = 100
        const val CACHE_ENTRIES = 64
    }
}
