package dev.ashwake.ui.stats

import dev.ashwake.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ashwake.core.time.AppClock
import dev.ashwake.domain.engine.analytics.CorrelationAnalyzer
import dev.ashwake.domain.engine.analytics.CorrelationPair
import dev.ashwake.domain.engine.analytics.CorrelationReport
import dev.ashwake.domain.engine.analytics.WeeklyReport
import dev.ashwake.domain.engine.analytics.YearSummary
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ashwake.data.export.ExportResult
import dev.ashwake.data.export.ImageExporter
import dev.ashwake.domain.repository.ritual.RitualRepository
import kotlinx.coroutines.flow.asStateFlow
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
    private val imageExporter: ImageExporter,
    @ApplicationContext private val context: Context,
    private val clock: AppClock
) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

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

    /**
     * Экспорт «Года в цифрах» в картинку (п. 10).
     * Карточка рисуется заново, а не снимается с экрана: скриншот утащил бы
     * системные панели и обрезался бы по высоте устройства.
     */
    fun exportYear(share: Boolean) {
        val summary = _year.value ?: return
        viewModelScope.launch {
            val bitmap = imageExporter.renderYearSummary(summary)
            val name = "ashwake-year-${summary.year}"

            if (share) {
                val intent = imageExporter.shareIntent(bitmap, name)
                if (intent == null) {
                    _message.value = context.getString(R.string.character_ne_udalos_podgotovit_kartinku)
                    return@launch
                }
                context.startActivity(
                    Intent.createChooser(intent, context.getString(R.string.stats_podelitsya_itogami_goda))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } else {
                _message.value = when (val result = imageExporter.saveToGallery(bitmap, name)) {
                    is ExportResult.Saved -> context.getString(R.string.stats_kartinka_sohranena_v_galereyu)
                    is ExportResult.Failed -> result.reason
                }
            }
        }
    }

    fun consumeMessage() { _message.value = null }

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
