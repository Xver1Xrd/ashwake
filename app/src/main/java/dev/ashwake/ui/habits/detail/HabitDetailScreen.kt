package dev.ashwake.ui.habits.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ashwake.ui.components.AshNavBar
import dev.ashwake.ui.theme.AshTheme
import dev.ashwake.domain.model.habits.EntryStatus
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import dev.ashwake.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: HabitDetailViewModel = hiltViewModel()
) {
    val detail by viewModel.detail.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = AshTheme.colors.background,
        topBar = {
            AshNavBar(
                title = detail?.progress?.habit?.name ?: "Привычка",
                onBack = onBack,
                actions = {
                    detail?.let { data ->
                        IconButton(onClick = { onEdit(data.progress.habit.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.detail_izmenit))
                        }
                    }
                }
            )
        }
    ) { padding ->
        val data = detail
        if (data == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.detail_zagruzka), color = AshTheme.colors.text2)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Metric("${(data.progress.score * 100).roundToInt()}%", "score")
                Metric("${data.progress.currentStreak}", "серия")
                Metric("${data.progress.recordStreak}", "рекорд")
                Metric("${data.progress.freezesLeftThisMonth}", "заморозки")
            }

            HorizontalDivider()

            Text(stringResource(R.string.detail_rost_score), style = AshTheme.type.headline)
            ScoreChart(series = data.scoreSeries)
            Text(
                "Пунктир — 80%: значение, к которому приходит идеально выполняемая привычка примерно за месяц",
                style = AshTheme.type.footnote,
                color = AshTheme.colors.text2
            )

            HorizontalDivider()

            Text(stringResource(R.string.detail_istoriya_za_god), style = AshTheme.type.headline)
            HabitHeatmap(
                entries = data.entries,
                excludedDays = data.excludedDays,
                from = viewModel.today.minusDays(364),
                to = viewModel.today,
                onDayClick = viewModel::cycleDay,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Тап по дню меняет отметку задним числом",
                style = AshTheme.type.footnote,
                color = AshTheme.colors.text2
            )

            HorizontalDivider()

            Statistics(data)
        }
    }
}

@Composable
private fun Metric(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = AshTheme.type.title2)
        Text(
            label,
            style = AshTheme.type.footnote,
            color = AshTheme.colors.text2
        )
    }
}

@Composable
private fun Statistics(data: dev.ashwake.domain.repository.habits.HabitDetail) {
    val entries = data.entries.values
    val done = entries.count { it.status == EntryStatus.DONE }
    val minimum = entries.count { it.status == EntryStatus.MINIMUM }
    val skipped = entries.count { it.status == EntryStatus.SKIPPED }
    val marked = done + minimum

    Text(stringResource(R.string.detail_za_god), style = AshTheme.type.headline)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Metric("$done", "выполнено")
        Metric("$minimum", "по минимуму")
        Metric("$skipped", "пропущено")
    }
    if (marked > 0) {
        Text(
            // Доля дней, закрытых по минимальной планке (п. 5)
            "Доля дней по минимуму: ${(minimum * 100 / marked)}%",
            style = AshTheme.type.footnote,
            color = AshTheme.colors.text2,
            textAlign = TextAlign.Start
        )
    }
}
