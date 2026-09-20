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
     * в высокоэстетичную карточку-постер премиального качества для шеринга.
     */
    fun renderTasksCard(tasks: List<Task>, dateTitle: String = "Задачи"): Bitmap {
        val width = 1080
        val outerPad = 48f
        val cardInnerPad = 40f
        val cardWidth = width - outerPad * 2

        // Точный расчёт высоты для гармоничной компоновки без сжатия
        var contentHeight = 0f
        // Header
        contentHeight += 50f // brand & progress
        contentHeight += 65f // title
        contentHeight += 40f // summary
        contentHeight += 25f // divider gap

        tasks.forEach { task ->
            var itemH = 32f // top/bottom padding
            itemH += 44f    // title
            val hasBadges = task.difficulty > 1 || task.estimateMinutes != null || task.tags.isNotEmpty()
            if (hasBadges) itemH += 40f // badges
            if (task.subtasks.isNotEmpty()) {
                itemH += 20f + task.subtasks.size * 42f // subtask container
            }
            contentHeight += itemH + 18f // item + gap
        }

        contentHeight += 70f // footer
        val totalCardHeight = contentHeight + cardInnerPad * 2
        val height = (totalCardHeight + outerPad * 2).toInt().coerceAtLeast(800)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Внешний глубокий фон
        canvas.drawColor(android.graphics.Color.rgb(10, 9, 14))

        // Атмосферный световой ореол вверху (Ambient Glow)
        val glowPaint = Paint().apply {
            isAntiAlias = true
            shader = android.graphics.RadialGradient(
                width / 2f, 160f, 650f,
                android.graphics.Color.argb(45, 139, 124, 255),
                android.graphics.Color.TRANSPARENT,
                android.graphics.Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(width / 2f, 160f, 650f, glowPaint)

        // 2. Основная парящая карточка
        val cardRect = android.graphics.RectF(outerPad, outerPad, width - outerPad, height - outerPad)
        val cardBgPaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.rgb(19, 18, 26)
            style = Paint.Style.FILL
        }
        val cardBorderPaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.rgb(40, 37, 52)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRoundRect(cardRect, 32f, 32f, cardBgPaint)
        canvas.drawRoundRect(cardRect, 32f, 32f, cardBorderPaint)

        // 3. Шапка
        var curY = outerPad + cardInnerPad

        // Бренд-бейдж слева
        val brandPillPaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.rgb(30, 27, 42)
            style = Paint.Style.FILL
        }
        val brandBorderPaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.rgb(55, 48, 76)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        val brandTextPaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.rgb(201, 162, 39)
            textSize = 22f
            isFakeBoldText = true
            letterSpacing = 0.08f
        }
        val brandRect = android.graphics.RectF(outerPad + cardInnerPad, curY, outerPad + cardInnerPad + 180f, curY + 38f)
        canvas.drawRoundRect(brandRect, 19f, 19f, brandPillPaint)
        canvas.drawRoundRect(brandRect, 19f, 19f, brandBorderPaint)
        canvas.drawText("✦ ASHWAKE", outerPad + cardInnerPad + 22f, curY + 26f, brandTextPaint)

        // Счётчик выполнения справа
        val doneCount = tasks.count { it.isDone }
        val totalCount = tasks.size
        val progressText = "$doneCount из $totalCount выполнено"
        val progressTextPaint = Paint().apply {
            isAntiAlias = true
            color = if (doneCount == totalCount && totalCount > 0) android.graphics.Color.rgb(52, 211, 153)
                    else android.graphics.Color.rgb(165, 160, 180)
            textSize = 24f
            isFakeBoldText = true
        }
        val progTextW = progressTextPaint.measureText(progressText)
        canvas.drawText(progressText, width - outerPad - cardInnerPad - progTextW, curY + 26f, progressTextPaint)

        curY += 60f

        // Заголовок даты
        val titlePaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.rgb(244, 244, 248)
            textSize = 46f
            isFakeBoldText = true
        }
        canvas.drawText(dateTitle, outerPad + cardInnerPad, curY + 36f, titlePaint)
        curY += 58f

        // Подпись с общим временем
        val totalMinutes = tasks.mapNotNull { it.estimateMinutes }.sum()
        val summaryText = if (totalMinutes > 0) "Оценка: ${formatEstimateRu(totalMinutes)}" else "Список дел на день"
        val subTitlePaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.rgb(140, 135, 155)
            textSize = 24f
        }
        canvas.drawText(summaryText, outerPad + cardInnerPad, curY + 16f, subTitlePaint)
        curY += 36f

        // Тонкий разделитель шапки
        val divPaint = Paint().apply {
            color = android.graphics.Color.rgb(34, 32, 45)
            strokeWidth = 1.5f
        }
        canvas.drawLine(outerPad + cardInnerPad, curY, width - outerPad - cardInnerPad, curY, divPaint)
        curY += 24f

        // 4. Карточки задач
        val taskBgPaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.rgb(27, 25, 36)
            style = Paint.Style.FILL
        }
        val taskBorderPaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.rgb(42, 39, 56)
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
        }
        val taskTitlePaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.rgb(240, 240, 245)
            textSize = 32f
            isFakeBoldText = true
        }
        val taskTitleDonePaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.rgb(120, 115, 135)
            textSize = 32f
            isStrikeThruText = true
        }
        val checkFillPaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.rgb(16, 185, 129)
            style = Paint.Style.FILL
        }
        val checkBorderPaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.rgb(90, 85, 110)
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        val checkmarkPaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val badgeBgPaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.rgb(36, 33, 48)
            style = Paint.Style.FILL
        }
        val badgeTextPaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.rgb(175, 170, 190)
            textSize = 20f
        }
        val subtaskBgPaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.rgb(21, 20, 29)
            style = Paint.Style.FILL
        }
        val subtaskTextPaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.rgb(205, 200, 218)
            textSize = 24f
        }
        val subtaskDoneTextPaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.rgb(110, 105, 125)
            textSize = 24f
            isStrikeThruText = true
        }

        tasks.forEach { task ->
            val hasBadges = task.difficulty > 1 || task.estimateMinutes != null || task.tags.isNotEmpty()
            var itemH = 32f + 44f
            if (hasBadges) itemH += 38f
            if (task.subtasks.isNotEmpty()) {
                itemH += 18f + task.subtasks.size * 42f
            }

            val itemRect = android.graphics.RectF(outerPad + cardInnerPad, curY, width - outerPad - cardInnerPad, curY + itemH)
            canvas.drawRoundRect(itemRect, 20f, 20f, taskBgPaint)
            canvas.drawRoundRect(itemRect, 20f, 20f, taskBorderPaint)

            val contentX = outerPad + cardInnerPad + 22f
            var taskY = curY + 22f

            // Чекбокс задачи
            val boxRadius = 14f
            val boxCenterX = contentX + boxRadius
            val boxCenterY = taskY + 16f

            if (task.isDone) {
                canvas.drawCircle(boxCenterX, boxCenterY, boxRadius, checkFillPaint)
                val path = android.graphics.Path().apply {
                    moveTo(boxCenterX - 6f, boxCenterY)
                    lineTo(boxCenterX - 1.5f, boxCenterY + 4.5f)
                    lineTo(boxCenterX + 6f, boxCenterY - 4.5f)
                }
                canvas.drawPath(path, checkmarkPaint)
            } else {
                canvas.drawCircle(boxCenterX, boxCenterY, boxRadius, checkBorderPaint)
            }

            // Название задачи
            val textLeft = contentX + boxRadius * 2 + 18f
            val titleP = if (task.isDone) taskTitleDonePaint else taskTitlePaint
            canvas.drawText(task.title, textLeft, taskY + 24f, titleP)
            taskY += 44f

            // Бейджи (сложность, время, теги)
            if (hasBadges) {
                var badgeX = textLeft

                // Сложность (кружочки 1..5)
                if (task.difficulty > 0) {
                    val diffPillW = 120f
                    val diffRect = android.graphics.RectF(badgeX, taskY, badgeX + diffPillW, taskY + 28f)
                    canvas.drawRoundRect(diffRect, 14f, 14f, badgeBgPaint)

                    val dotR = 4.5f
                    var dotX = badgeX + 16f
                    val dotY = taskY + 14f
                    val dotColor = when (task.difficulty) {
                        1 -> android.graphics.Color.rgb(52, 211, 153)
                        2 -> android.graphics.Color.rgb(56, 189, 248)
                        3 -> android.graphics.Color.rgb(251, 191, 36)
                        4 -> android.graphics.Color.rgb(251, 146, 60)
                        else -> android.graphics.Color.rgb(248, 113, 113)
                    }
                    val dotP = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
                    for (i in 1..5) {
                        dotP.color = if (i <= task.difficulty) dotColor else android.graphics.Color.rgb(65, 60, 80)
                        canvas.drawCircle(dotX, dotY, dotR, dotP)
                        dotX += dotR * 2 + 6f
                    }
                    badgeX += diffPillW + 10f
                }

                // Время оценки
                task.estimateMinutes?.let { mins ->
                    val estText = "⏱ ${formatEstimateRu(mins)}"
                    val estW = badgeTextPaint.measureText(estText) + 24f
                    val estRect = android.graphics.RectF(badgeX, taskY, badgeX + estW, taskY + 28f)
                    canvas.drawRoundRect(estRect, 14f, 14f, badgeBgPaint)
                    canvas.drawText(estText, badgeX + 12f, taskY + 20f, badgeTextPaint)
                    badgeX += estW + 10f
                }

                // Теги
                task.tags.take(3).forEach { tag ->
                    val tagText = "#${tag.name}"
                    val tagW = badgeTextPaint.measureText(tagText) + 20f
                    val tagRect = android.graphics.RectF(badgeX, taskY, badgeX + tagW, taskY + 28f)
                    canvas.drawRoundRect(tagRect, 14f, 14f, badgeBgPaint)
                    canvas.drawText(tagText, badgeX + 10f, taskY + 20f, badgeTextPaint)
                    badgeX += tagW + 10f
                }

                taskY += 38f
            }

            // Подзадачи
            if (task.subtasks.isNotEmpty()) {
                val subH = 14f + task.subtasks.size * 42f
                val subRect = android.graphics.RectF(textLeft, taskY, width - outerPad - cardInnerPad - 20f, taskY + subH)
                canvas.drawRoundRect(subRect, 14f, 14f, subtaskBgPaint)

                var subY = taskY + 12f
                task.subtasks.forEach { sub ->
                    val sCenterX = textLeft + 22f
                    val sCenterY = subY + 16f
                    val sRadius = 9f

                    if (sub.isDone) {
                        canvas.drawCircle(sCenterX, sCenterY, sRadius, checkFillPaint)
                        val path = android.graphics.Path().apply {
                            moveTo(sCenterX - 4f, sCenterY)
                            lineTo(sCenterX - 1f, sCenterY + 3f)
                            lineTo(sCenterX + 4f, sCenterY - 3f)
                        }
                        val miniCheckPaint = Paint(checkmarkPaint).apply { strokeWidth = 2.2f }
                        canvas.drawPath(path, miniCheckPaint)
                    } else {
                        val miniBorderPaint = Paint(checkBorderPaint).apply { strokeWidth = 1.8f }
                        canvas.drawCircle(sCenterX, sCenterY, sRadius, miniBorderPaint)
                    }

                    val stp = if (sub.isDone) subtaskDoneTextPaint else subtaskTextPaint
                    canvas.drawText(sub.title, textLeft + 42f, subY + 23f, stp)
                    subY += 42f
                }
            }

            curY += itemH + 18f
        }

        // 5. Подвал
        val footerPaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.rgb(115, 110, 130)
            textSize = 22f
            letterSpacing = 0.05f
        }
        val footerText = "✦ Создано в Ashwake — порядок и ясность каждый день"
        val footW = footerPaint.measureText(footerText)
        canvas.drawText(footerText, (width - footW) / 2f, curY + 36f, footerPaint)

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
