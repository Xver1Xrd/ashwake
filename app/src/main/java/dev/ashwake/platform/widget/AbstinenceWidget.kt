package dev.ashwake.platform.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlinx.coroutines.flow.first

/**
 * Виджет счётчика отказа: крупное число дней, без лишнего (п. 18).
 *
 * Секунды на виджете не показываются намеренно: система обновляет виджеты
 * не чаще раза в несколько минут, и тикающий счётчик выглядел бы сломанным.
 */
class AbstinenceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val deps = context.widgetDependencies()
        val items = deps.abstinenceRepository().observeAll(deps.clock().now()).first()
        val first = items.firstOrNull()

        provideContent {
            GlanceTheme {
                Content(
                    name = first?.abstinence?.name,
                    days = first?.stats?.currentDays ?: 0,
                    record = first?.stats?.record?.toDays() ?: 0
                )
            }
        }
    }

    @Composable
    private fun Content(name: String?, days: Long, record: Long) {
        val context = LocalContext.current

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(12.dp)
                .clickable(actionStartActivity(appIntent(context, AppRoutes.ABSTINENCE))),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (name == null) {
                Text(
                    "Счётчиков нет",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant)
                )
                return@Column
            }

            Text(
                days.toString(),
                style = TextStyle(
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.onSurface
                )
            )
            Text(
                dayWord(days),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant)
            )
            Text(
                name,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface)
            )
            if (record > days) {
                Text(
                    "рекорд $record",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant)
                )
            }
        }
    }

    private fun dayWord(days: Long): String {
        val mod100 = days % 100
        val mod10 = days % 10
        return when {
            mod100 in 11..14 -> "дней"
            mod10 == 1L -> "день"
            mod10 in 2..4 -> "дня"
            else -> "дней"
        }
    }
}

class AbstinenceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AbstinenceWidget()
}

/** Кнопки быстрых действий: добавить задачу и запустить помодоро (п. 18). */
class QuickActionsWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme { Content() }
        }
    }

    @Composable
    private fun Content() {
        val context = LocalContext.current

        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionButton(
                "＋ Задача",
                appIntent(context, AppRoutes.NEW_TASK),
                GlanceModifier.defaultWeight()
            )
            ActionButton(
                "▶ Помодоро",
                appIntent(context, AppRoutes.FOCUS_START),
                GlanceModifier.defaultWeight()
            )
            ActionButton(
                "☾ Ритуал",
                appIntent(context, AppRoutes.RITUAL),
                GlanceModifier.defaultWeight()
            )
        }
    }

    // Вес задаётся снаружи: defaultWeight доступен только внутри Row
    @Composable
    private fun ActionButton(
        label: String,
        intent: android.content.Intent,
        modifier: GlanceModifier
    ) {
        Column(
            modifier = modifier
                .padding(6.dp)
                .clickable(actionStartActivity(intent)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                label,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

class QuickActionsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickActionsWidget()
}
