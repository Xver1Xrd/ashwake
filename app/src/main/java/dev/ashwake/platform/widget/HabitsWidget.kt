package dev.ashwake.platform.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.ashwake.domain.model.habits.EntryStatus
import dev.ashwake.domain.model.habits.HabitWithProgress
import dev.ashwake.ui.theme.Ember
import dev.ashwake.ui.theme.WidgetPalette
import kotlinx.coroutines.flow.first
import java.time.temporal.ChronoUnit

/**
 * Виджет привычек на сегодня с недельным прогрессом (п. 18).
 *
 * Недельная полоска рисуется семью квадратами: это компактнее любого графика
 * и на виджете читается с одного взгляда.
 */
class HabitsWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val deps = context.widgetDependencies()
        val today = deps.clock().today()
        val habits = deps.habitRepository()
            .observeHabitsWithProgress(today)
            .first()
            .filter { it.dueToday && !it.paused }
            .take(MAX_ITEMS)

        val doneToday = habits.count { it.doneToday }

        provideContent {
            GlanceTheme { Content(habits, doneToday) }
        }
    }

    @Composable
    private fun Content(habits: List<HabitWithProgress>, doneToday: Int) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(12.dp)
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Привычки",
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        color = GlanceTheme.colors.onSurface
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )
                Text(
                    "$doneToday / ${habits.size}",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant)
                )
            }

            if (habits.isEmpty()) {
                Text(
                    "На сегодня ничего не запланировано",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant)
                )
                return@Column
            }

            LazyColumn {
                items(habits) { progress ->
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .clickable(
                                actionRunCallback<MarkHabitAction>(
                                    actionParametersOf(HabitIdKey to progress.habit.id)
                                )
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (progress.doneToday) "●  " else "○  ",
                            style = TextStyle(
                                color = if (progress.doneToday) ColorProvider(WidgetPalette.success)
                                else GlanceTheme.colors.onSurfaceVariant
                            )
                        )
                        Text(
                            progress.habit.name,
                            maxLines = 1,
                            style = TextStyle(color = GlanceTheme.colors.onSurface),
                            modifier = GlanceModifier.defaultWeight()
                        )
                        Text(
                            "${(progress.score * 100).toInt()}",
                            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant)
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val MAX_ITEMS = 8
    }
}

/** Отметка привычки из виджета — через тот же use case, что и экран. */
class MarkHabitAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val habitId = parameters[HabitIdKey] ?: return
        val deps = context.widgetDependencies()
        val today = deps.clock().today()

        val progress = deps.habitRepository()
            .observeHabitsWithProgress(today)
            .first()
            .firstOrNull { it.habit.id == habitId } ?: return

        // Повторный тап снимает отметку: виджет ведёт себя как карточка в списке
        if (progress.doneToday) {
            deps.clearHabitMark().invoke(habitId, today)
        } else {
            deps.markHabit().invoke(
                progress = progress,
                status = EntryStatus.DONE,
                source = dev.ashwake.domain.model.habits.EntrySource.WIDGET
            )
        }
        HabitsWidget().update(context, glanceId)
    }
}

class HabitsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitsWidget()
}

/**
 * Heatmap одной привычки за месяц (п. 18).
 *
 * Показывается первая привычка списка: выбор конкретной потребовал бы
 * экрана настройки виджета — он появится вместе с настройками приложения.
 */
class HabitHeatmapWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val deps = context.widgetDependencies()
        val today = deps.clock().today()
        val habit = deps.habitRepository().observeHabits().first().firstOrNull()

        val detail = habit?.let {
            deps.habitRepository().observeDetail(it.id, today, historyDays = 34).first()
        }

        val statuses = (0 until DAYS).map { offset ->
            val date = today.minusDays((DAYS - 1 - offset).toLong())
            detail?.entries?.get(date)?.status
        }

        provideContent {
            GlanceTheme { Content(habit?.name, statuses) }
        }
    }

    @Composable
    private fun Content(name: String?, statuses: List<EntryStatus?>) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(12.dp)
        ) {
            Text(
                name ?: "Привычек нет",
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.onSurface
                )
            )
            Spacer(GlanceModifier.height(6.dp))

            // Сетка 5×7: строка — неделя, столбец — день
            statuses.chunked(WEEK).forEach { week ->
                Row(modifier = GlanceModifier.padding(bottom = 3.dp)) {
                    week.forEach { status ->
                        Box(
                            modifier = GlanceModifier
                                .size(14.dp)
                                .padding(end = 3.dp)
                                .cornerRadius(3.dp)
                                .background(colorOf(status))
                        ) {}
                    }
                }
            }
        }
    }

    /**
     * Цвет отметки. Берётся из сырой палитры, а не из темы: виджет живёт
     * на рабочем столе, где композиции темы нет и настройки приложения
     * не читаются.
     */
    private fun colorOf(status: EntryStatus?): ColorProvider = when (status) {
        EntryStatus.DONE -> ColorProvider(WidgetPalette.success)
        EntryStatus.MINIMUM -> ColorProvider(WidgetPalette.success.copy(alpha = 0.5f))
        EntryStatus.SKIPPED -> ColorProvider(WidgetPalette.warm.copy(alpha = 0.6f))
        EntryStatus.FROZEN, EntryStatus.PAUSED -> ColorProvider(WidgetPalette.cold)
        null -> ColorProvider(WidgetPalette.surface2)
    }

    private companion object {
        const val WEEK = 7
        const val DAYS = 35
    }
}

class HabitHeatmapWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitHeatmapWidget()
}
