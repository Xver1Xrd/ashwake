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
import dev.ashwake.domain.model.tasks.Task
import dev.ashwake.ui.tasks.components.formatEstimateRu
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

    /**
     * Рендерит список задач (вместе со сложностью, временем и подзадачами)
     * в красивую карточку для отправки картинкой.
     */
    fun renderTasksCard(tasks: List<Task>, dateTitle: String = "Задачи"): Bitmap {
        val width = 1080
        val margin = 64f

        var estimatedHeight = 220f
        tasks.forEach { task ->
            estimatedHeight += 70f
            estimatedHeight += 40f
            estimatedHeight += task.subtasks.size * 50f
            estimatedHeight += 45f
        }
        estimatedHeight += 120f
        val height = estimatedHeight.toInt().coerceAtLeast(600)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BACKGROUND)

        val headerBrandPaint = Paint().apply {
            isAntiAlias = true
            color = ACCENT
            textSize = 28f
            isFakeBoldText = true
        }

        val headerTitlePaint = Paint().apply {
            isAntiAlias = true
            color = TEXT
            textSize = 52f
            isFakeBoldText = true
        }

        val taskTitlePaint = Paint().apply {
            isAntiAlias = true
            color = TEXT
            textSize = 34f
            isFakeBoldText = true
        }

        val taskTitleDonePaint = Paint().apply {
            isAntiAlias = true
            color = MUTED
            textSize = 34f
            isStrikeThruText = true
        }

        val metaPaint = Paint().apply {
            isAntiAlias = true
            color = MUTED
            textSize = 24f
        }

        val subtaskPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(200, 195, 210)
            textSize = 28f
        }

        val subtaskDonePaint = Paint().apply {
            isAntiAlias = true
            color = MUTED
            textSize = 28f
            isStrikeThruText = true
        }

        val boxPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = MUTED
        }

        val checkPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = Color.rgb(76, 175, 80)
        }

        val checkmarkPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = Color.WHITE
        }

        val dividerPaint = Paint().apply {
            color = Color.rgb(40, 36, 50)
            strokeWidth = 1.5f
        }

        var y = 90f
        canvas.drawText("ASHWAKE", margin, y, headerBrandPaint)
        y += 60f
        canvas.drawText(dateTitle, margin, y, headerTitlePaint)
        y += 40f
        canvas.drawLine(margin, y, width - margin, y, dividerPaint)
        y += 55f

        tasks.forEach { task ->
            val boxSize = 32f
            val boxLeft = margin
            val boxTop = y - 26f

            if (task.isDone) {
                canvas.drawRoundRect(boxLeft, boxTop, boxLeft + boxSize, boxTop + boxSize, 8f, 8f, checkPaint)
                canvas.drawLine(boxLeft + 8f, boxTop + 16f, boxLeft + 14f, boxTop + 22f, checkmarkPaint)
                canvas.drawLine(boxLeft + 14f, boxTop + 22f, boxLeft + 24f, boxTop + 10f, checkmarkPaint)
            } else {
                canvas.drawRoundRect(boxLeft, boxTop, boxLeft + boxSize, boxTop + boxSize, 8f, 8f, boxPaint)
            }

            val textX = margin + boxSize + 24f
            val p = if (task.isDone) taskTitleDonePaint else taskTitlePaint
            canvas.drawText(task.title, textX, y, p)
            y += 38f

            // Кружочки сложности (1..5)
            val dotRadius = 7f
            var dotX = textX + dotRadius
            val dotY = y - 8f
            val dotActiveColor = when (task.difficulty) {
                1 -> Color.rgb(76, 175, 80)
                2 -> Color.rgb(33, 150, 243)
                3 -> Color.rgb(255, 193, 7)
                4 -> Color.rgb(255, 152, 0)
                else -> Color.rgb(244, 67, 54)
            }
            val dotPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
            for (i in 1..5) {
                dotPaint.color = if (i <= task.difficulty) dotActiveColor else Color.rgb(60, 55, 70)
                canvas.drawCircle(dotX, dotY, dotRadius, dotPaint)
                dotX += dotRadius * 2 + 8f
            }

            // Метаданные (время и теги)
            val metaParts = mutableListOf<String>()
            task.estimateMinutes?.let { metaParts += formatEstimateRu(it) }
            if (task.tags.isNotEmpty()) {
                metaParts += task.tags.joinToString(" ") { "#${it.name}" }
            }
            if (metaParts.isNotEmpty()) {
                canvas.drawText(metaParts.joinToString(" · "), dotX + 16f, y, metaPaint)
            }
            y += 45f

            // Подзадачи
            if (task.subtasks.isNotEmpty()) {
                task.subtasks.forEach { subtask ->
                    val sBoxSize = 22f
                    val sLeft = textX + 10f
                    val sTop = y - 18f
                    if (subtask.isDone) {
                        canvas.drawRoundRect(sLeft, sTop, sLeft + sBoxSize, sTop + sBoxSize, 6f, 6f, checkPaint)
                        canvas.drawLine(sLeft + 5f, sTop + 11f, sLeft + 10f, sTop + 15f, checkmarkPaint)
                        canvas.drawLine(sLeft + 10f, sTop + 15f, sLeft + 17f, sTop + 7f, checkmarkPaint)
                    } else {
                        canvas.drawRoundRect(sLeft, sTop, sLeft + sBoxSize, sTop + sBoxSize, 6f, 6f, boxPaint)
                    }
                    val stp = if (subtask.isDone) subtaskDonePaint else subtaskPaint
                    canvas.drawText(subtask.title, sLeft + sBoxSize + 16f, y, stp)
                    y += 42f
                }
            }

            y += 15f
            canvas.drawLine(margin, y, width - margin, y, dividerPaint)
            y += 45f
        }

        val footerPaint = Paint().apply {
            isAntiAlias = true
            color = MUTED
            textSize = 24f
        }
        canvas.drawText("ashwake · фокус и порядок каждый день", margin, height - 35f, footerPaint)

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
