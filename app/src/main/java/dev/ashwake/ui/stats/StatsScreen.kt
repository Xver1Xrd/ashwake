package dev.ashwake.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ashwake.ui.components.TextAction
import dev.ashwake.ui.components.AshNavBar
import dev.ashwake.ui.theme.AshTheme
import dev.ashwake.domain.engine.analytics.CorrelationPair
import dev.ashwake.domain.engine.analytics.Trend
import dev.ashwake.domain.engine.analytics.WeeklyReport
import dev.ashwake.domain.engine.analytics.YearSummary
import dev.ashwake.platform.service.formatTime
import dev.ashwake.ui.theme.Ember
import dev.ashwake.ui.theme.Gold
import dev.ashwake.ui.theme.Moss
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import dev.ashwake.R

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit = {},
    viewModel: StatsViewModel = hiltViewModel()
) {
    val weekly by viewModel.weekly.collectAsStateWithLifecycle()
    val correlations by viewModel.correlations.collectAsStateWithLifecycle()
    val year by viewModel.year.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        containerColor = AshTheme.colors.background,
        topBar = {
            AshNavBar(
                title = stringResource(R.string.stats_statistika),
                onBack = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(stringResource(R.string.stats_nedelya), stringResource(R.string.stats_svyazi), stringResource(R.string.stats_god)).forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = tab == index,
                        onClick = { tab = index },
                        shape = SegmentedButtonDefaults.itemShape(index, 3),
                        label = { Text(label) }
                    )
                }
            }

            when (tab) {
                0 -> weekly?.let { WeeklyBlock(it) } ?: Loading()
                1 -> CorrelationsBlock(viewModel, correlations)
                else -> year?.let {
                    YearBlock(it)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextAction(
                            text = stringResource(R.string.stats_sohranit_kartinkoy),
                            onClick = { viewModel.exportYear(share = false) }
                        )
                        TextAction(
                            text = stringResource(R.string.character_podelitsya),
                            onClick = { viewModel.exportYear(share = true) }
                        )
                    }
                } ?: Loading()
            }
        }
    }
}

@Composable
private fun Loading() {
    Text(stringResource(R.string.stats_schitayu), color = AshTheme.colors.text2)
}

@Composable
private fun WeeklyBlock(report: WeeklyReport) {
    Text(
        "${report.weekStart.format(DATE_FORMAT)} — ${report.weekEnd.format(DATE_FORMAT)}",
        style = AshTheme.type.headline
    )

    TrendRow(stringResource(R.string.stats_zadachi_zakryty), report.tasksCompleted) { it.toString() }
    TrendRow(stringResource(R.string.stats_otmetki_privychek), report.habitMarks) { it.toString() }
    TrendRow(stringResource(R.string.focus_fokus), report.focusSeconds) { formatTime(it.toInt()) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(stringResource(R.string.stats_vypolnenie_privychek), style = AshTheme.type.callout)
        Text(
            "${(report.habitCompletionRate * 100).roundToInt()}%",
            style = AshTheme.type.callout,
            color = if (report.completionRateDelta >= 0) Moss else Ember
        )
    }

    Text(
        stringResource(R.string.stats_ritual_proyden_1_s_iz_7_dney, report.ritualDays),
        style = AshTheme.type.footnote,
        color = AshTheme.colors.text2
    )

    report.averageMood?.let {
        Text(
            stringResource(R.string.stats_srednee_nastroenie_1f).format(it),
            style = AshTheme.type.footnote,
            color = AshTheme.colors.text2
        )
    }

    if (report.abstinenceDays.isNotEmpty()) {
        HorizontalDivider()
        Text(stringResource(R.string.stats_schetchiki_otkazov), style = AshTheme.type.subhead)
        report.abstinenceDays.forEach { (name, days) ->
            Text(stringResource(R.string.stats_1_s_2_s_dn, name, days), style = AshTheme.type.subhead)
        }
    }
}

/** Величина с прошлым периодом: без сравнения отчёт — просто набор чисел. */
@Composable
private fun TrendRow(label: String, trend: Trend, format: (Long) -> String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = AshTheme.type.callout)
        Row {
            Text(format(trend.current), style = AshTheme.type.callout)
            trend.percent?.let { percent ->
                Text(
                    "  ${if (percent >= 0) "+" else ""}$percent%",
                    style = AshTheme.type.footnote,
                    color = if (percent >= 0) Moss else Ember
                )
            }
        }
    }
}

@Composable
private fun CorrelationsBlock(
    viewModel: StatsViewModel,
    report: dev.ashwake.domain.engine.analytics.CorrelationReport?
) {
    if (report == null) {
        Loading()
        return
    }

    if (!report.hasEnoughData) {
        // Меньше двух недель — выводов не делаем вовсе
        Text(
            stringResource(R.string.stats_poka_malo_dannyh_svyazi_schitayutsya_ot_14_d, report.sampleDays),
            style = AshTheme.type.callout,
            color = AshTheme.colors.text2
        )
        return
    }

    Text(stringResource(R.string.stats_chto_svyazano_s_nastroeniem_i_energiey), style = AshTheme.type.headline)
    Text(
        viewModel.disclaimer,
        style = AshTheme.type.footnote,
        color = AshTheme.colors.text2
    )

    val positive = report.topPositive()
    val negative = report.topNegative()

    if (positive.isNotEmpty()) {
        HorizontalDivider()
        Text(stringResource(R.string.stats_svyazano_polozhitelno), style = AshTheme.type.subhead, color = Moss)
        positive.forEach { PairRow(viewModel, it) }
    }
    if (negative.isNotEmpty()) {
        HorizontalDivider()
        Text(stringResource(R.string.stats_svyazano_otricatelno), style = AshTheme.type.subhead, color = Ember)
        negative.forEach { PairRow(viewModel, it) }
    }
    if (positive.isEmpty() && negative.isEmpty()) {
        Text(
            stringResource(R.string.stats_zametnyh_svyazey_ne_nashlos),
            style = AshTheme.type.callout,
            color = AshTheme.colors.text2
        )
    }
}

@Composable
private fun PairRow(viewModel: StatsViewModel, pair: CorrelationPair) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(viewModel.describe(pair), style = AshTheme.type.callout)
        Text(
            stringResource(R.string.stats_koefficient_2f_vyborka_d_dney).format(pair.coefficient, pair.sampleSize),
            style = AshTheme.type.footnote,
            color = AshTheme.colors.text2
        )
    }
}

@Composable
private fun YearBlock(summary: YearSummary) {
    Text(stringResource(R.string.stats_god_1_s_v_cifrah, summary.year), style = AshTheme.type.headline)

    if (!summary.hasData) {
        Text(
            stringResource(R.string.stats_za_etot_god_dannyh_poka_net),
            style = AshTheme.type.callout,
            color = AshTheme.colors.text2
        )
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Metric("${summary.tasksCompleted}", stringResource(R.string.stats_zadach))
        Metric("${summary.habitMarks}", stringResource(R.string.stats_otmetok))
        Metric("${summary.longestStreak}", stringResource(R.string.stats_rekord_serii))
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Metric(formatTime(summary.focusSeconds.toInt()), stringResource(R.string.stats_fokus))
        Metric("${summary.cleanDays}", stringResource(R.string.stats_chistyh_dney))
    }

    HorizontalDivider()
    summary.longestStreakHabit?.let {
        Text(stringResource(R.string.stats_samaya_dlinnaya_seriya_1_s, it), style = AshTheme.type.callout)
    }
    summary.mostStableHabit?.let {
        Text(stringResource(R.string.stats_samaya_stabilnaya_1_s, it), style = AshTheme.type.callout, color = Moss)
    }
    summary.mostProblematicHabit?.let {
        Text(stringResource(R.string.stats_samaya_problemnaya_1_s, it), style = AshTheme.type.callout, color = Ember)
    }
    summary.bestMonth?.let {
        Text(
            stringResource(R.string.stats_luchshiy_mesyac_1_s_2_s, it.month.value, it.year),
            style = AshTheme.type.footnote,
            color = Gold
        )
    }

    if (summary.relapseReasons.isNotEmpty()) {
        HorizontalDivider()
        Text(stringResource(R.string.stats_prichiny_sryvov), style = AshTheme.type.subhead)
        summary.relapseReasons.forEach { (reason, count) ->
            Text("$reason — $count", style = AshTheme.type.subhead)
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
