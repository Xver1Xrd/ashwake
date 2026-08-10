package dev.ashwake.ui.tasks.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ashwake.ui.components.TextAction
import dev.ashwake.ui.theme.AshTheme
import dev.ashwake.domain.model.tasks.Task
import dev.ashwake.ui.tasks.CalendarScale
import dev.ashwake.ui.tasks.CalendarUiState
import dev.ashwake.ui.theme.PriorityColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import androidx.compose.ui.res.stringResource
import dev.ashwake.R

private val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("LLLL yyyy")
private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM, EEEE")
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private val WEEKDAY_LABELS = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")

/** Высота часа в сетке дня. Из неё же считаются высоты блоков по оценке времени. */
private val HOUR_HEIGHT = 56.dp

@Composable
fun TaskCalendar(
    state: CalendarUiState,
    today: LocalDate,
    onScaleChange: (CalendarScale) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onShift: (Boolean) -> Unit,
    onToday: () -> Unit,
    onTaskClick: (Task) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        CalendarHeader(state, onScaleChange, onShift, onToday)
        HorizontalDivider()

        when (state.scale) {
            CalendarScale.MONTH -> {
                MonthGrid(state, today, onDateSelected)
                HorizontalDivider()
                DayTaskList(state.tasksByDate[state.selected].orEmpty(), onTaskClick)
            }

            CalendarScale.WEEK -> {
                WeekStrip(state, today, onDateSelected)
                HorizontalDivider()
                DayTaskList(state.tasksByDate[state.selected].orEmpty(), onTaskClick)
            }

            CalendarScale.DAY -> DayGrid(
                tasks = state.tasksByDate[state.anchor].orEmpty(),
                onTaskClick = onTaskClick
            )
        }
    }
}

@Composable
private fun CalendarHeader(
    state: CalendarUiState,
    onScaleChange: (CalendarScale) -> Unit,
    onShift: (Boolean) -> Unit,
    onToday: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onShift(false) }) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.detail_nazad))
        }
        Text(
            text = when (state.scale) {
                CalendarScale.DAY -> state.anchor.format(DAY_FORMAT)
                else -> state.anchor.format(MONTH_FORMAT)
            }.replaceFirstChar { it.titlecase(Locale.getDefault()) },
            style = AshTheme.type.headline,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(onClick = { onShift(true) }) {
            Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.calendar_vpered))
        }
        TextAction(text = stringResource(R.string.calendar_segodnya), onClick = onToday)
    }

    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        CalendarScale.entries.forEachIndexed { index, scale ->
            SegmentedButton(
                selected = state.scale == scale,
                onClick = { onScaleChange(scale) },
                shape = SegmentedButtonDefaults.itemShape(index, CalendarScale.entries.size),
                label = { Text(scaleLabel(scale)) }
            )
        }
    }
}

@Composable
private fun MonthGrid(
    state: CalendarUiState,
    today: LocalDate,
    onDateSelected: (LocalDate) -> Unit
) {
    val firstCell = state.anchor.withDayOfMonth(1)
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    Column(Modifier.padding(horizontal = 8.dp)) {
        WeekdayHeader()
        repeat(6) { week ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { dayOfWeek ->
                    val date = firstCell.plusDays((week * 7 + dayOfWeek).toLong())
                    DayCell(
                        date = date,
                        tasks = state.tasksByDate[date].orEmpty(),
                        isToday = date == today,
                        isSelected = date == state.selected,
                        isCurrentMonth = date.month == state.anchor.month,
                        modifier = Modifier.weight(1f),
                        onClick = { onDateSelected(date) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekStrip(
    state: CalendarUiState,
    today: LocalDate,
    onDateSelected: (LocalDate) -> Unit
) {
    val monday = state.anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    Column(Modifier.padding(horizontal = 8.dp)) {
        WeekdayHeader()
        Row(Modifier.fillMaxWidth()) {
            repeat(7) { index ->
                val date = monday.plusDays(index.toLong())
                DayCell(
                    date = date,
                    tasks = state.tasksByDate[date].orEmpty(),
                    isToday = date == today,
                    isSelected = date == state.selected,
                    isCurrentMonth = true,
                    modifier = Modifier.weight(1f),
                    onClick = { onDateSelected(date) }
                )
            }
        }
    }
}

@Composable
private fun WeekdayHeader() {
    Row(Modifier.fillMaxWidth()) {
        WEEKDAY_LABELS.forEach { label ->
            Text(
                text = label,
                style = AshTheme.type.footnote,
                color = AshTheme.colors.text2,
                modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    tasks: List<Task>,
    isToday: Boolean,
    isSelected: Boolean,
    isCurrentMonth: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .aspectRatio(0.9f)
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) AshTheme.colors.surface2
                else AshTheme.colors.surface1
            )
            .then(
                if (isToday) Modifier.border(
                    1.dp, AshTheme.colors.warm, RoundedCornerShape(8.dp)
                ) else Modifier
            )
            .clickable(onClick = onClick)
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = AshTheme.type.footnote,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            // Дни соседних месяцев приглушены, но кликабельны
            color = if (isCurrentMonth) AshTheme.colors.text
            else AshTheme.colors.text2.copy(alpha = 0.5f)
        )
        // Точки по приоритетам: больше трёх не показываем, дальше «+N»
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            tasks.take(3).forEach { task ->
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(PriorityColors[task.priority.ordinal])
                )
            }
        }
        if (tasks.size > 3) {
            Text(
                "+${tasks.size - 3}",
                style = AshTheme.type.footnote,
                color = AshTheme.colors.text2
            )
        }
    }
}

@Composable
private fun DayTaskList(tasks: List<Task>, onTaskClick: (Task) -> Unit) {
    if (tasks.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                "На этот день задач нет",
                style = AshTheme.type.callout,
                color = AshTheme.colors.text2
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(tasks, key = { it.id }) { task ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(AshTheme.colors.surface1)
                    .clickable { onTaskClick(task) }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(width = 3.dp, height = 18.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(PriorityColors[task.priority.ordinal])
                )
                Text(
                    text = "  " + (task.dueTime?.format(TIME_FORMAT)?.plus("  ") ?: "") + task.title,
                    style = AshTheme.type.callout,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Сетка дня: задачи стоят на своих часах, высота блока — из оценки времени.
 *
 * Это только отображение уже назначенного. Кнопка «Разложить день»
 * с автоматической расстановкой по свободным слотам — этап 6 (TimeboxPlanner).
 */
@Composable
private fun DayGrid(tasks: List<Task>, onTaskClick: (Task) -> Unit) {
    val timed = tasks.filter { it.dueTime != null }
    val untimed = tasks.filter { it.dueTime == null }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (untimed.isNotEmpty()) {
            Text(
                "Без времени",
                style = AshTheme.type.footnote,
                color = AshTheme.colors.text2,
                modifier = Modifier.padding(start = 12.dp, top = 8.dp)
            )
            untimed.forEach { task ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 3.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(AshTheme.colors.surface2)
                        .clickable { onTaskClick(task) }
                        .padding(8.dp)
                ) {
                    Text(task.title, style = AshTheme.type.subhead, maxLines = 1)
                }
            }
            HorizontalDivider(Modifier.padding(top = 8.dp))
        }

        Box(Modifier.fillMaxWidth().height(HOUR_HEIGHT * 24)) {
            // Часовая разметка
            repeat(24) { hour ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(HOUR_HEIGHT)
                        .offset(y = HOUR_HEIGHT * hour)
                ) {
                    Text(
                        text = "%02d".format(hour),
                        style = AshTheme.type.footnote,
                        color = AshTheme.colors.text2,
                        modifier = Modifier.width(32.dp).padding(start = 6.dp, top = 2.dp)
                    )
                    HorizontalDivider(Modifier.padding(top = 6.dp))
                }
            }

            timed.forEach { task ->
                val time = task.dueTime!!
                val minutesFromMidnight = time.hour * 60 + time.minute
                val blockMinutes = (task.estimateMinutes ?: 30).coerceAtLeast(20)
                Box(
                    modifier = Modifier
                        .padding(start = 40.dp, end = 8.dp)
                        .offset(y = HOUR_HEIGHT * (minutesFromMidnight / 60f))
                        .fillMaxWidth()
                        .height(HOUR_HEIGHT * (blockMinutes / 60f))
                        .clip(RoundedCornerShape(6.dp))
                        .background(PriorityColors[task.priority.ordinal].copy(alpha = 0.25f))
                        .border(
                            1.dp,
                            PriorityColors[task.priority.ordinal],
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { onTaskClick(task) }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${time.format(TIME_FORMAT)}  ${task.title}",
                        style = AshTheme.type.footnote,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun scaleLabel(scale: CalendarScale): String = when (scale) {
    CalendarScale.MONTH -> "Месяц"
    CalendarScale.WEEK -> "Неделя"
    CalendarScale.DAY -> "День"
}

/** Локализованное имя дня недели — пригодится подписям в других экранах. */
internal fun DayOfWeek.shortRu(): String =
    getDisplayName(TextStyle.SHORT, Locale("ru"))
