package dev.ashwake.ui.ritual

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ashwake.R
import dev.ashwake.domain.engine.ritual.RitualAccessStatus
import dev.ashwake.domain.model.habits.HabitWithProgress
import dev.ashwake.domain.model.ritual.DailyReview
import dev.ashwake.domain.model.tasks.Task
import dev.ashwake.ui.components.AshIcons
import dev.ashwake.ui.components.AshNavBar
import dev.ashwake.ui.components.AshTextField
import dev.ashwake.ui.components.ChipButton
import dev.ashwake.ui.components.EmptyState
import dev.ashwake.ui.components.PrimaryButton
import dev.ashwake.ui.components.SecondaryButton
import dev.ashwake.ui.components.TextAction
import dev.ashwake.ui.theme.AshShapes
import dev.ashwake.ui.theme.AshTheme
import dev.ashwake.ui.theme.LocalIs24Hour
import dev.ashwake.ui.theme.formatTime
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale("ru"))
private val FULL_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE · d MMMM yyyy", Locale("ru"))

/**
 * Вечерний ритуал (п. 9).
 *
 * Ограничения и возможности:
 * 1. Ритуал доступен только с 20:00 (8 вечера) до начала нового дня (dayStartHour, по умолчанию 04:00).
 * 2. Если за целевой день ритуал уже пройден, повторно ответить нельзя — отображается отчёт и время следующего ритуала.
 * 3. Вне интервала отображается экран ожидания с временем открытия.
 * 4. В любой момент доступен просмотр всей истории пройденных ритуалов с деталями.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RitualScreen(
    onDone: () -> Unit,
    viewModel: RitualViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val accessStatus by viewModel.accessStatus.collectAsStateWithLifecycle()
    val viewingHistory by viewModel.viewingHistory.collectAsStateWithLifecycle()
    val allReviews by viewModel.allReviews.collectAsStateWithLifecycle()
    val taskTitles by viewModel.taskTitles.collectAsStateWithLifecycle()

    val step by viewModel.step.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    val date by viewModel.date.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()

    LaunchedEffect(finished) {
        if (finished) {
            viewModel.reset()
        }
    }

    val stepIndex = RitualStep.entries.indexOf(step)
    val colors = AshTheme.colors

    Scaffold(
        containerColor = colors.background,
        topBar = {
            if (viewingHistory) {
                AshNavBar(
                    title = stringResource(R.string.ritual_history),
                    onBack = viewModel::closeHistory
                )
            } else {
                AshNavBar(
                    title = stringResource(R.string.ritual_vecherniy_ritual),
                    subtitle = if (state.isCatchUp) stringResource(R.string.ritual_za_1_s, date.format(DATE_FORMAT))
                    else date.format(DATE_FORMAT),
                    onBack = {
                        if (accessStatus !is RitualAccessStatus.Available || stepIndex == 0) {
                            onDone()
                        } else {
                            viewModel.back()
                        }
                    },
                    actions = {
                        TextAction(
                            text = stringResource(R.string.ritual_history),
                            onClick = viewModel::openHistory
                        )
                    }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            when {
                viewingHistory -> {
                    RitualHistoryView(
                        reviews = allReviews,
                        taskTitles = taskTitles
                    )
                }

                accessStatus is RitualAccessStatus.TooEarly -> {
                    RitualTooEarlyView(
                        status = accessStatus as RitualAccessStatus.TooEarly,
                        onOpenHistory = viewModel::openHistory,
                        onDone = onDone
                    )
                }

                accessStatus is RitualAccessStatus.AlreadyCompleted -> {
                    val completedReview = state.review ?: allReviews.firstOrNull { it.date == (accessStatus as RitualAccessStatus.AlreadyCompleted).targetDate }
                    RitualAlreadyCompletedView(
                        review = completedReview,
                        status = accessStatus as RitualAccessStatus.AlreadyCompleted,
                        taskTitles = taskTitles,
                        onOpenHistory = viewModel::openHistory,
                        onDone = onDone
                    )
                }

                accessStatus is RitualAccessStatus.Available -> {
                    // Опросник из 5 шагов
                    Column(modifier = Modifier.fillMaxSize()) {
                        LinearProgressIndicator(
                            progress = { (stepIndex + 1f) / RitualStep.entries.size },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            when (step) {
                                RitualStep.SCALES -> ScalesStep(form, viewModel)
                                RitualStep.TASKS -> TasksStep(state.openTasks, viewModel)
                                RitualStep.HABITS -> HabitsStep(state.unmarkedHabits, viewModel)
                                RitualStep.TOMORROW -> TomorrowStep(state.tomorrowCandidates, form, viewModel)
                                RitualStep.NOTE -> NoteStep(form, viewModel)
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (stepIndex > 0) {
                                ChipButton(
                                    text = stringResource(R.string.detail_nazad),
                                    onClick = viewModel::back
                                )
                            }
                            PrimaryButton(
                                text = if (step == RitualStep.NOTE) stringResource(R.string.routines_zakonchit)
                                else stringResource(R.string.onboarding_dalshe),
                                modifier = Modifier.weight(1f),
                                onClick = viewModel::next
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Экран ожидания (до 20:00)
// ---------------------------------------------------------------------------

@Composable
private fun RitualTooEarlyView(
    status: RitualAccessStatus.TooEarly,
    onOpenHistory: () -> Unit,
    onDone: () -> Unit
) {
    val colors = AshTheme.colors
    val is24Hour = LocalIs24Hour.current
    val opensAtTime = status.opensAt.toLocalTime()
    val formattedOpensAt = formatTime(opensAtTime, is24Hour)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(colors.cold.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AshIcons.Moon,
                contentDescription = null,
                tint = colors.cold,
                modifier = Modifier.size(40.dp)
            )
        }

        Text(
            text = stringResource(R.string.ritual_too_early_title),
            style = AshTheme.type.title2,
            textAlign = TextAlign.Center,
            color = colors.text
        )

        Text(
            text = stringResource(R.string.ritual_too_early_desc, status.dayStartHour),
            style = AshTheme.type.callout,
            color = colors.text2,
            textAlign = TextAlign.Center
        )

        Box(
            modifier = Modifier
                .clip(AshShapes.pill)
                .background(colors.surface2)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Доступно сегодня в $formattedOpensAt",
                style = AshTheme.type.footnote,
                fontWeight = FontWeight.SemiBold,
                color = colors.accent
            )
        }

        Spacer(Modifier.height(8.dp))

        PrimaryButton(
            text = stringResource(R.string.ritual_btn_view_history),
            onClick = onOpenHistory
        )

        SecondaryButton(
            text = stringResource(R.string.components_zakryt),
            onClick = onDone
        )
    }
}

// ---------------------------------------------------------------------------
// Экран уже завершённого ритуала за день
// ---------------------------------------------------------------------------

@Composable
private fun RitualAlreadyCompletedView(
    review: DailyReview?,
    status: RitualAccessStatus.AlreadyCompleted,
    taskTitles: Map<Long, String>,
    onOpenHistory: () -> Unit,
    onDone: () -> Unit
) {
    val colors = AshTheme.colors
    val is24Hour = LocalIs24Hour.current
    val nextTime = status.nextAvailableAt.toLocalTime()
    val nextTimeFormatted = formatTime(nextTime, is24Hour)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(colors.success.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AshIcons.Check,
                contentDescription = null,
                tint = colors.success,
                modifier = Modifier.size(32.dp)
            )
        }

        Text(
            text = stringResource(R.string.ritual_already_completed_title),
            style = AshTheme.type.title2,
            color = colors.text,
            textAlign = TextAlign.Center
        )

        Text(
            text = stringResource(R.string.ritual_already_completed_subtitle),
            style = AshTheme.type.footnote,
            color = colors.text2,
            textAlign = TextAlign.Center
        )

        // Карточка ответов за сегодняшний день
        if (review != null) {
            ReviewCard(review = review, taskTitles = taskTitles)
        }

        Box(
            modifier = Modifier
                .clip(AshShapes.pill)
                .background(colors.surface2)
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = "Следующий ритуал: завтра в $nextTimeFormatted",
                style = AshTheme.type.caption,
                color = colors.text2
            )
        }

        PrimaryButton(
            text = stringResource(R.string.ritual_btn_view_history),
            onClick = onOpenHistory
        )

        SecondaryButton(
            text = stringResource(R.string.components_zakryt),
            onClick = onDone
        )
    }
}

// ---------------------------------------------------------------------------
// Экран истории прошлых ритуалов
// ---------------------------------------------------------------------------

@Composable
private fun RitualHistoryView(
    reviews: List<DailyReview>,
    taskTitles: Map<Long, String>
) {
    if (reviews.isEmpty()) {
        EmptyState(
            icon = AshIcons.Moon,
            title = stringResource(R.string.ritual_history_empty),
            description = stringResource(R.string.ritual_history_empty_desc)
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(reviews, key = { it.date.toEpochDay() }) { review ->
            ReviewCard(review = review, taskTitles = taskTitles)
        }
    }
}

@Composable
private fun ReviewCard(
    review: DailyReview,
    taskTitles: Map<Long, String>
) {
    val colors = AshTheme.colors
    val is24Hour = LocalIs24Hour.current

    val fullDate = remember(review.date) {
        val raw = review.date.format(FULL_DATE_FORMAT)
        raw.replaceFirstChar { it.uppercase() }
    }

    val completedTimeStr = remember(review.completedAt) {
        if (review.completedAt == Instant.EPOCH) null
        else {
            val localTime = review.completedAt.atZone(ZoneId.systemDefault()).toLocalTime()
            formatTime(localTime, is24Hour)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AshShapes.card)
            .background(colors.surface1)
            .border(1.dp, colors.surface3, AshShapes.card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = fullDate,
                style = AshTheme.type.headline,
                color = colors.text,
                modifier = Modifier.weight(1f)
            )

            completedTimeStr?.let {
                Text(
                    text = it,
                    style = AshTheme.type.caption,
                    color = colors.text3
                )
            }
        }

        // Шкалы (Оценка дня, Настроение, Энергия)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            review.dayRating?.let { rating ->
                ScaleBadge(
                    label = "День",
                    value = "$rating/5",
                    icon = "⭐"
                )
            }
            review.mood?.let { mood ->
                ScaleBadge(
                    label = "Настроение",
                    value = "$mood/5",
                    icon = "😊"
                )
            }
            review.energy?.let { energy ->
                ScaleBadge(
                    label = "Энергия",
                    value = "$energy/5",
                    icon = "⚡"
                )
            }
        }

        // Заметка дня
        if (!review.note.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AshShapes.small)
                    .background(colors.surface2)
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Что запомнилось:",
                        style = AshTheme.type.caption,
                        color = colors.text3
                    )
                    Text(
                        text = review.note,
                        style = AshTheme.type.body,
                        color = colors.text
                    )
                }
            }
        }

        // Топ-3 задачи на следующий день
        if (review.topTaskIds.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Запланированные главные задачи:",
                    style = AshTheme.type.caption,
                    color = colors.text3
                )
                review.topTaskIds.forEach { taskId ->
                    val title = taskTitles[taskId] ?: "Задача #$taskId"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("•", color = colors.accent, fontWeight = FontWeight.Bold)
                        Text(
                            text = title,
                            style = AshTheme.type.footnote,
                            color = colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScaleBadge(label: String, value: String, icon: String) {
    val colors = AshTheme.colors
    Row(
        modifier = Modifier
            .clip(AshShapes.pill)
            .background(colors.surface2)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = icon, style = AshTheme.type.caption)
        Text(
            text = "$label $value",
            style = AshTheme.type.caption,
            color = colors.text2
        )
    }
}

// ---------------------------------------------------------------------------
// Шаги опросника
// ---------------------------------------------------------------------------

@Composable
private fun ScalesStep(form: RitualForm, viewModel: RitualViewModel) {
    Text(stringResource(R.string.ritual_kak_proshel_den), style = AshTheme.type.title3)
    ScaleRow(stringResource(R.string.ritual_ocenka_dnya), form.dayRating, viewModel::setDayRating)

    HorizontalDivider()
    Text(
        stringResource(R.string.ritual_nastroenie_i_energiya_otdelno_svyazi_s_privy),
        style = AshTheme.type.footnote,
        color = AshTheme.colors.text2
    )
    ScaleRow(stringResource(R.string.ritual_nastroenie), form.mood, viewModel::setMood)
    ScaleRow(stringResource(R.string.ritual_energiya), form.energy, viewModel::setEnergy)
}

@Composable
private fun ScaleRow(label: String, value: Int?, onSelect: (Int) -> Unit) {
    Column {
        Text(label, style = AshTheme.type.subhead)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..5).forEach { level ->
                ChipButton(
                    text = "$level",
                    selected = value == level,
                    onClick = { onSelect(level) }
                )
            }
        }
    }
}

@Composable
private fun TasksStep(tasks: List<Task>, viewModel: RitualViewModel) {
    Text(stringResource(R.string.ritual_nezakrytye_zadachi), style = AshTheme.type.title3)

    if (tasks.isEmpty()) {
        EmptyHint(stringResource(R.string.ritual_vse_zakryto))
        return
    }

    TaskSwipeStack(
        tasks = tasks,
        onComplete = viewModel::complete,
        onPostpone = viewModel::postponeToTomorrow,
        onPostponeAll = { viewModel.postponeAll(tasks) }
    )
}

@Composable
private fun HabitsStep(habits: List<HabitWithProgress>, viewModel: RitualViewModel) {
    Text(stringResource(R.string.ritual_neprostavlennye_privychki), style = AshTheme.type.title3)

    if (habits.isEmpty()) {
        EmptyHint(stringResource(R.string.ritual_vse_privychki_otmecheny))
        return
    }

    habits.forEach { progress ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(AshTheme.colors.surface1)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                progress.habit.name,
                modifier = Modifier.weight(1f),
                style = AshTheme.type.callout
            )
            TextAction(
                text = stringResource(R.string.ritual_sdelal),
                onClick = { viewModel.markDone(progress) }
            )
            TextAction(
                text = stringResource(R.string.ritual_propustil),
                onClick = { viewModel.markSkipped(progress) }
            )
        }
    }
}

@Composable
private fun TomorrowStep(
    candidates: List<Task>,
    form: RitualForm,
    viewModel: RitualViewModel
) {
    Text(stringResource(R.string.ritual_tri_glavnye_zadachi_na_zavtra), style = AshTheme.type.title3)
    Text(
        stringResource(R.string.ritual_vybrano_1_s_iz_3, form.topTaskIds.size),
        style = AshTheme.type.footnote,
        color = AshTheme.colors.text2
    )

    candidates.take(15).forEach { task ->
        val selected = task.id in form.topTaskIds
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (selected) AshTheme.colors.surface2
                    else AshTheme.colors.surface1
                )
                .clickable { viewModel.toggleTopTask(task) }
                .padding(10.dp)
        ) {
            Text(
                task.title,
                style = AshTheme.type.callout,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    HorizontalDivider()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.ritual_razlozhit_zavtrashniy_den), style = AshTheme.type.callout)
            Text(
                stringResource(R.string.ritual_srazu_posle_rituala_rasstavit_zadachi_po_slo),
                style = AshTheme.type.footnote,
                color = AshTheme.colors.text2
            )
        }
        Switch(checked = form.planTomorrow, onCheckedChange = viewModel::setPlanTomorrow)
    }
}

@Composable
private fun NoteStep(form: RitualForm, viewModel: RitualViewModel) {
    Text(stringResource(R.string.ritual_zametka_dnya), style = AshTheme.type.title3)
    AshTextField(
        value = form.note,
        onValueChange = viewModel::setNote,
        modifier = Modifier.fillMaxWidth(),
        placeholder = stringResource(R.string.ritual_chto_zapomnilos),
        singleLine = false,
        minLines = 4,
        maxLines = 10
    )
}

@Composable
private fun EmptyHint(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = AshTheme.type.callout,
            color = AshTheme.colors.success,
            textAlign = TextAlign.Center
        )
    }
}
