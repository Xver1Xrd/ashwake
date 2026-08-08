package dev.ashwake.data.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ashwake.domain.engine.analytics.YearSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ExportResult {
    data class Saved(val uri: Uri) : ExportResult
    data class Failed(val reason: String) : ExportResult
}

/**
 * Сохранение картинок: портрет персонажа и «Год в цифрах» (п. 15.9, 10).
 *
 * В галерею пишем через MediaStore: на API 29+ это работает без разрешения
 * на запись во внешнюю память вообще, а просить его ради одной картинки
 * в приложении, которое гордится минимумом разрешений, было бы неуместно.
 */
@Singleton
class ImageExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun saveToGallery(bitmap: Bitmap, name: String): ExportResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(
                            MediaStore.Images.Media.RELATIVE_PATH,
                            "${Environment.DIRECTORY_PICTURES}/Ashwake"
                        )
                    }
                }

                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values
                ) ?: return@runCatching ExportResult.Failed("Не удалось создать файл")

                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
                ExportResult.Saved(uri)
            }.getOrElse { ExportResult.Failed(it.message ?: "Не удалось сохранить") }
        }

    /** Интент «поделиться» через FileProvider: прямой file:// система не примет. */
    suspend fun shareIntent(bitmap: Bitmap, name: String): Intent? =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, "shared").apply { mkdirs() }
                val file = File(dir, "$name.png")
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }.getOrNull()
        }

    /**
     * «Год в цифрах» в картинку (п. 10).
     *
     * Рисуется отдельно, а не снимком экрана: скриншот утащил бы системные
     * панели и обрезался бы по высоте устройства, а карточку хочется
     * одинаковой у всех.
     */
    fun renderYearSummary(summary: YearSummary): Bitmap {
        val bitmap = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BACKGROUND)

        val title = Paint().apply {
            isAntiAlias = true
            color = TEXT
            textSize = 64f
        }
        val label = Paint().apply {
            isAntiAlias = true
            color = MUTED
            textSize = 32f
        }
        val value = Paint().apply {
            isAntiAlias = true
            color = ACCENT
            textSize = 56f
        }

        var y = 140f
        canvas.drawText("Год ${summary.year}", MARGIN, y, title)
        y += 90f

        val rows = listOf(
            "Задач закрыто" to summary.tasksCompleted.toString(),
            "Отметок привычек" to summary.habitMarks.toString(),
            "Самая длинная серия" to "${summary.longestStreak}",
            "Часы фокуса" to "${summary.focusSeconds / 3600}",
            "Чистых дней" to summary.cleanDays.toString()
        )
        rows.forEach { (name, number) ->
            canvas.drawText(name, MARGIN, y, label)
            canvas.drawText(number, CARD_WIDTH - MARGIN - value.measureText(number), y, value)
            y += 80f
        }

        y += 20f
        summary.mostStableHabit?.let {
            canvas.drawText("Самая стабильная: $it", MARGIN, y, label)
            y += 50f
        }
        summary.mostProblematicHabit?.let {
            canvas.drawText("Самая проблемная: $it", MARGIN, y, label)
            y += 50f
        }

        canvas.drawText("Ashwake", MARGIN, CARD_HEIGHT - MARGIN, label)
        return bitmap
    }

    private companion object {
        const val CARD_WIDTH = 1080
        const val CARD_HEIGHT = 1080
        const val MARGIN = 80f

        val BACKGROUND = Color.rgb(13, 11, 18)
        val TEXT = Color.rgb(232, 224, 208)
        val MUTED = Color.rgb(154, 147, 168)
        val ACCENT = Color.rgb(201, 162, 39)
    }
}
