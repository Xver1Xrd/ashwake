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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ashwake.ui.components.AshNavBar
import dev.ashwake.domain.engine.abstinence.describeCravingPeak
import dev.ashwake.ui.abstinence.components.AttemptsChart
import dev.ashwake.ui.abstinence.components.CravingHeatmap
import dev.ashwake.ui.abstinence.components.LiveCounter
import dev.ashwake.ui.components.tappable
import dev.ashwake.ui.theme.AshShapes
import dev.ashwake.ui.theme.AshTheme
import dev.ashwake.ui.abstinence.components.SavingsBlock
import dev.ashwake.ui.abstinence.components.StatsRow
import dev.ashwake.ui.abstinence.components.MilestoneRing
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import dev.ashwake.R

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
        containerColor = AshTheme.colors.background,
        topBar = {
            AshNavBar(
                title = detail?.abstinence?.name ?: stringResource(R.string.detail_otkaz),
                onBack = onBack
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
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
                    style = AshTheme.type.callout,
                    color = AshTheme.colors.text2
                )
            }

            StatsRow(data.stats)

            // Два действия рядом и на виду.
            //
            // Раньше «тяжело» стояло под счётчиком, а «срыв» — текстовой
            // ссылкой в самом низу, под графиками: до неё не доскроллить,
            // и «тяжело» принимали за отметку срыва. Это ровно те две вещи,
            // ради которых экран открывают, и они обязаны быть рядом.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ActionButton(
                    text = stringResource(R.string.detail_tyazhelo),
                    color = AshTheme.colors.cold,
                    filled = true,
                    onClick = { showCraving = true },
                    modifier = Modifier.weight(1f)
                )
                ActionButton(
                    text = stringResource(R.string.detail_sryv),
                    color = AshTheme.colors.danger,
                    filled = false,
                    onClick = { showRelapse = true },
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                stringResource(R.string.detail_tyazhelo_otmetka_o_tom_chto_nakrylo_no_vy_ud),
                style = AshTheme.type.footnote,
                color = AshTheme.colors.text2,
                textAlign = TextAlign.Center
            )

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
                onRelapse = { duration, note ->
                    // Тяга закрывается как непреодолённая, а сам срыв
                    // отмечается отдельно и с подтверждением: одно нажатие
                    // не должно обнулять три месяца
                    viewModel.finishCraving(false, duration, note)
                    showCraving = false
                    showRelapse = true
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
        Text(stringResource(R.string.detail_tyaga), style = AshTheme.type.headline)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Metric("${analytics.totalEvents}", stringResource(R.string.detail_epizodov))
            analytics.resistedShare?.let {
                Metric("${(it * 100).roundToInt()}%", stringResource(R.string.detail_perezhdano))
            }
            analytics.averageIntensity?.let {
                Metric("%.1f".format(it), stringResource(R.string.detail_srednyaya_sila))
            }
        }

        // Формулировка осторожная: это наблюдение по своим же данным
        describeCravingPeak(analytics.peak)?.let { peak ->
            Text(
                peak,
                style = AshTheme.type.callout,
                color = AshTheme.colors.warm
            )
        }

        CravingHeatmap(analytics.heatmap, modifier = Modifier.fillMaxWidth())

        if (analytics.topTriggers.isNotEmpty()) {
            Text(stringResource(R.string.detail_top_triggerov), style = AshTheme.type.subhead)
            analytics.topTriggers.take(3).forEach { entry ->
                Text(
                    "${entry.trigger?.label ?: "без триггера"} — ${entry.count}",
                    style = AshTheme.type.subhead,
                    color = AshTheme.colors.text2
                )
            }
        }
    }
}

@Composable
private fun Metric(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = AshTheme.type.title3)
        Text(
            label,
            style = AshTheme.type.footnote,
            color = AshTheme.colors.text2
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

/**
 * Кнопка действия отказа. Залитая — то, что делают чаще и без последствий;
 * контурная — то, что меняет историю и требует секунды на подумать.
 */
@Composable
private fun ActionButton(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AshTheme.colors
    Box(
        modifier = modifier
            .height(48.dp)
            .background(
                if (filled) color else color.copy(alpha = 0.12f),
                AshShapes.pill
            )
            .tappable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = AshTheme.type.body.copy(
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            ),
            color = when {
                !filled -> color
                colors.isDark -> androidx.compose.ui.graphics.Color.Black
                else -> androidx.compose.ui.graphics.Color.White
            }
        )
    }
}
