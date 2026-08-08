package dev.ashwake.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ashwake.core.time.AppClock
import dev.ashwake.domain.engine.analytics.CorrelationAnalyzer
import dev.ashwake.domain.engine.analytics.CorrelationPair
import dev.ashwake.domain.engine.analytics.CorrelationReport
import dev.ashwake.domain.engine.analytics.WeeklyReport
import dev.ashwake.domain.engine.analytics.YearSummary
import dev.ashwake.domain.repository.ritual.RitualRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val ritual: RitualRepository,
    private val analyzer: CorrelationAnalyzer,
    private val clock: AppClock
) : ViewModel() {

    private val _weekly = MutableStateFlow<WeeklyReport?>(null)
    val weekly: StateFlow<WeeklyReport?> = _weekly.asStateFlow()

    private val _correlations = MutableStateFlow<CorrelationReport?>(null)
    val correlations: StateFlow<CorrelationReport?> = _correlations.asStateFlow()

    private val _year = MutableStateFlow<YearSummary?>(null)
    val year: StateFlow<YearSummary?> = _year.asStateFlow()

    val disclaimer: String get() = analyzer.disclaimer

    init {
        // Отчёты считаются по требованию, а не подпиской: это тяжёлые выборки,
        // и пересчитывать их на каждое изменение базы незачем
        viewModelScope.launch {
            val weekStart = clock.today()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            _weekly.value = ritual.weeklyReport(weekStart)
            _correlations.value = ritual.correlations()
            _year.value = ritual.yearSummary(clock.today().year)
        }
    }

    fun describe(pair: CorrelationPair): String = analyzer.describe(pair)

    fun refresh() {
        viewModelScope.launch {
            val weekStart = clock.today()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            _weekly.value = ritual.weeklyReport(weekStart)
            _correlations.value = ritual.correlations()
            _year.value = ritual.yearSummary(clock.today().year)
        }
    }
}
