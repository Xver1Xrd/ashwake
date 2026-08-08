package dev.ashwake.ui.timebox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ashwake.core.time.AppClock
import dev.ashwake.data.calendar.SystemCalendarReader
import dev.ashwake.data.settings.AppSettings
import dev.ashwake.domain.engine.timebox.PlanResult
import dev.ashwake.domain.model.tasks.TimeboxDay
import dev.ashwake.domain.model.tasks.TimeboxSettings
import dev.ashwake.domain.repository.timebox.TimeboxRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Что показать после раскладки: сколько не влезло и что именно. */
data class PlanOutcome(
    val deficitMinutes: Int,
    val deferredTitles: List<String>,
    val withoutEstimateTitles: List<String>
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TimeboxViewModel @Inject constructor(
    private val timebox: TimeboxRepository,
    private val settings: AppSettings,
    private val calendar: SystemCalendarReader,
    private val clock: AppClock
) : ViewModel() {

    private val _date = MutableStateFlow(clock.today())
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    val day: StateFlow<TimeboxDay> = _date
        .flatMapLatest { timebox.observeDay(it) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            TimeboxDay(clock.today())
        )

    val timeboxSettings: StateFlow<TimeboxSettings> = settings.timebox
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimeboxSettings())

    val useCalendar: StateFlow<Boolean> = settings.useSystemCalendar
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _outcome = MutableStateFlow<PlanOutcome?>(null)
    val outcome: StateFlow<PlanOutcome?> = _outcome.asStateFlow()

    private val _planning = MutableStateFlow(false)
    val planning: StateFlow<Boolean> = _planning.asStateFlow()

    fun selectDate(date: LocalDate) { _date.value = date }

    fun shiftDate(days: Long) { _date.value = _date.value.plusDays(days) }

    fun planDay() {
        if (_planning.value) return
        viewModelScope.launch {
            _planning.value = true
            val result = timebox.planDay(_date.value)
            _outcome.value = outcomeOf(result)
            _planning.value = false
        }
    }

    /**
     * Итог показывается всегда, даже когда всё влезло: молчание после нажатия
     * выглядит как «кнопка не сработала».
     */
    private fun outcomeOf(result: PlanResult) = PlanOutcome(
        deficitMinutes = result.deficitMinutes,
        deferredTitles = result.deferred.map { it.title },
        withoutEstimateTitles = result.withoutEstimate.map { it.title }
    )

    fun dismissOutcome() { _outcome.value = null }

    fun moveBlock(blockId: Long, newStartMinute: Int) {
        viewModelScope.launch { timebox.moveBlock(blockId, newStartMinute) }
    }

    fun togglePin(blockId: Long, pinned: Boolean) {
        viewModelScope.launch { timebox.setPinned(blockId, pinned) }
    }

    fun deleteBlock(blockId: Long) {
        viewModelScope.launch { timebox.deleteBlock(blockId) }
    }

    fun clearDay() {
        viewModelScope.launch { timebox.clearDay(_date.value) }
    }

    /** Блок затянулся — сдвигаем остаток дня. */
    fun markOverran(blockId: Long, actualEndMinute: Int) {
        viewModelScope.launch { timebox.reflow(_date.value, blockId, actualEndMinute) }
    }

    fun setUseCalendar(enabled: Boolean) {
        viewModelScope.launch { settings.setUseSystemCalendar(enabled) }
    }

    fun calendarPermissionGranted(): Boolean = calendar.hasPermission()

    fun setWorkHours(startMinute: Int, endMinute: Int) {
        viewModelScope.launch { settings.setWorkHours(startMinute, endMinute) }
    }

    fun setBuffer(minutes: Int) {
        viewModelScope.launch { settings.setBuffer(minutes) }
    }
}
