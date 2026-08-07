package dev.ashwake.ui.abstinence.detail

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ashwake.domain.engine.abstinence.describeCravingPeak
import dev.ashwake.ui.abstinence.components.AttemptsChart
import dev.ashwake.ui.abstinence.components.CravingHeatmap
import dev.ashwake.ui.abstinence.components.LiveCounter
import dev.ashwake.ui.abstinence.components.SavingsBlock
import dev.ashwake.ui.abstinence.components.StatsRow
import dev.ashwake.ui.abstinence.components.MilestoneRing
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbstinenceDetailScreen(
    onBack: () -> Unit,
    viewModel: AbstinenceDetailViewModel = hiltViewModel()
) {
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showRelapse by remember { mutableStateOf(false) }
    var showCraving by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.abstinence?.name ?: "Отказ") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        val data = detail
        if (data == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Загрузка…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MilestoneRing(
                progress = data.stats.progressToNextMilestone,
                label = data.stats.nextMilestone?.title
            ) {
                LiveCounter(duration = data.stats.current)
            }

            data.stats.nextMilestone?.userText?.let { text ->
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            StatsRow(data.stats)

            Button(
                onClick = { showCraving = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Тяжело") }

            data.stats.savings?.let { savings ->
                HorizontalDivider()
                SavingsBlock(savings)
            }

            HorizontalDivider()
            AttemptsChart(
                attempts = data.abstinence.attempts,
                currentDuration = data.stats.current,
                modifier = Modifier.fillMaxWidth()
            )

            if (data.analytics.totalEvents > 0) {
                HorizontalDivider()
                CravingAnalyticsBlock(data)
            }

            HorizontalDivider()
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (data.undoAvailableUntil != null) {
                    UndoRelapseButton(onClick = {
                        viewModel.undoRelapse { ok ->
                            scope.launch {
                                snackbar.showSnackbar(
                                    if (ok) "Срыв отменён" else "Срок отмены истёк"
                                )
                            }
                        }
                    })
                }
                RelapseButton(onClick = { showRelapse = true })
            }
        }

        if (showRelapse) {
            RelapseDialog(
                reasons = data.relapseReasons,
                onConfirm = { reasonId, note, at ->
                    viewModel.registerRelapse(reasonId, note, at)
                    showRelapse = false
                    scope.launch {
                        // Формулировка не обвиняющая: новая попытка, а не потеря
                        snackbar.showSnackbar("Попытка №${data.stats.attemptNumber + 1} началась")
                    }
                },
                onDismiss = { showRelapse = false }
            )
        }

        if (showCraving) {
            CravingSheet(
                abstinence = data.abstinence,
                triggers = data.triggers,
                onStart = viewModel::startCraving,
                onFinish = { resisted, duration, note ->
                    viewModel.finishCraving(resisted, duration, note)
                    showCraving = false
                },
                onDismiss = {
                    viewModel.cancelCraving()
                    showCraving = false
                }
            )
        }

        if (!data.abstinence.substanceWarningAck && needsSubstanceWarning(data.abstinence.name)) {
            SubstanceWarningDialog(onAcknowledge = viewModel::acknowledgeWarning)
        }
    }
}

@Composable
private fun CravingAnalyticsBlock(data: dev.ashwake.domain.repository.abstinence.AbstinenceDetail) {
    val analytics = data.analytics

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Тяга", style = MaterialTheme.typography.titleSmall)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Metric("${analytics.totalEvents}", "эпизодов")
            analytics.resistedShare?.let {
                Metric("${(it * 100).roundToInt()}%", "переждано")
            }
            analytics.averageIntensity?.let {
                Metric("%.1f".format(it), "средняя сила")
            }
        }

        // Формулировка осторожная: это наблюдение по своим же данным
        describeCravingPeak(analytics.peak)?.let { peak ->
            Text(
                peak,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        CravingHeatmap(analytics.heatmap, modifier = Modifier.fillMaxWidth())

        if (analytics.topTriggers.isNotEmpty()) {
            Text("Топ триггеров", style = MaterialTheme.typography.labelLarge)
            analytics.topTriggers.take(3).forEach { entry ->
                Text(
                    "${entry.trigger?.label ?: "без триггера"} — ${entry.count}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun Metric(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Грубая эвристика по названию: показывать предупреждение имеет смысл там,
 * где резкий отказ действительно может быть опасен. Ошибка в сторону лишнего
 * показа безобиднее, чем пропуск — экран одноразовый и без нравоучений.
 */
private fun needsSubstanceWarning(name: String): Boolean {
    val lower = name.lowercase()
    return SUBSTANCE_KEYWORDS.any { lower.contains(it) }
}

private val SUBSTANCE_KEYWORDS = listOf(
    "алког", "пью", "пить", "выпив", "спирт", "пиво", "вино", "водк",
    "вещест", "нарко", "транквил", "бензо"
)
