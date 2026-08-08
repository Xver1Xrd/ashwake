package dev.ashwake.platform.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.ashwake.domain.model.tasks.Task
import dev.ashwake.domain.repository.tasks.TaskFilter
import kotlinx.coroutines.flow.first

/**
 * Виджет «Задачи на сегодня» с отметкой в один тап (п. 18).
 *
 * Тап по строке закрывает задачу через тот же use case, что и экран, —
 * иначе отметка из виджета не начисляла бы монеты и оставляла бы висеть
 * напоминание.
 */
class TasksWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val deps = context.widgetDependencies()
        val today = deps.clock().today()
        val tasks = deps.taskRepository()
            .observeTasks(TaskFilter(dueBefore = today))
            .first()
            .filterNot { it.isDone }
            .take(MAX_ITEMS)

        provideContent {
            GlanceTheme { Content(tasks) }
        }
    }

    @Composable
    private fun Content(tasks: List<Task>) {
        val context = LocalContext.current

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
                    "Сегодня",
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        color = GlanceTheme.colors.onSurface
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )
                Text(
                    "＋",
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        color = GlanceTheme.colors.primary
                    ),
                    modifier = GlanceModifier.clickable(
                        actionStartActivity(appIntent(context, AppRoutes.NEW_TASK))
                    )
                )
            }

            if (tasks.isEmpty()) {
                Text(
                    "Задач на сегодня нет",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant)
                )
            } else {
                LazyColumn {
                    items(tasks) { task ->
                        Row(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable(
                                    actionRunCallback<CompleteTaskAction>(
                                        actionParametersOf(TaskIdKey to task.id)
                                    )
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("○  ", style = TextStyle(color = GlanceTheme.colors.primary))
                            Text(
                                task.title,
                                maxLines = 1,
                                style = TextStyle(color = GlanceTheme.colors.onSurface)
                            )
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val MAX_ITEMS = 8
    }
}

/** Закрытие задачи прямо из виджета. */
class CompleteTaskAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val taskId = parameters[TaskIdKey] ?: return
        context.widgetDependencies().completeTask().invoke(taskId)
        TasksWidget().update(context, glanceId)
    }
}

class TasksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TasksWidget()
}
