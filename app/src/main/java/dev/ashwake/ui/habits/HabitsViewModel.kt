package dev.ashwake.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ashwake.core.time.AppClock
import dev.ashwake.data.assets.HabitPresetCategory
import dev.ashwake.data.assets.HabitPresetLoader
import dev.ashwake.domain.model.habits.EntryStatus
import dev.ashwake.domain.model.habits.Habit
import dev.ashwake.domain.model.habits.HabitPause
import dev.ashwake.domain.model.habits.HabitType
import dev.ashwake.domain.model.habits.HabitWithProgress
import dev.ashwake.domain.model.habits.SkipReason
import dev.ashwake.domain.repository.habits.HabitRepository
import dev.ashwake.domain.scheduler.HabitReminderScheduler
import dev.ashwake.domain.usecase.habits.ClearHabitMarkUseCase
import dev.ashwake.domain.usecase.habits.MarkHabitUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class HabitsUiState(
    val habits: List<HabitWithProgress> = emptyList(),
    val skipReasons: List<SkipReason> = emptyList(),
    val pauses: List<HabitPause> = emptyList(),
    val today: LocalDate = LocalDate.EPOCH,
    val showOnlyDueToday: Boolean = true
) {
    /** На сегодня ожидаются: остальные показываются отдельным свёрнутым блоком. */
    val dueToday: List<HabitWithProgress> get() = habits.filter { it.dueToday && !it.paused }
    val restToday: List<HabitWithProgress> get() = habits.filter { !it.dueToday || it.paused }
    val vacationMode: Boolean get() = pauses.any { it.habitId == null && it.endDate == null }
}

@HiltViewModel
class HabitsViewModel @Inject constructor(
    private val habits: HabitRepository,
    private val presetLoader: HabitPresetLoader,
    private val reminderScheduler: HabitReminderScheduler,
    private val markHabit: MarkHabitUseCase,
    private val clearHabitMark: ClearHabitMarkUseCase,
    private val clock: AppClock
) : ViewModel() {

    private val showOnlyDue = MutableStateFlow(true)

    private val _presets = MutableStateFlow<List<HabitPresetCategory>>(emptyList())
    val presets: StateFlow<List<HabitPresetCategory>> = _presets.asStateFlow()

    val state: StateFlow<HabitsUiState> = combine(
        habits.observeHabitsWithProgress(clock.today()),
        habits.observeSkipReasons(),
        habits.observePauses(),
        showOnlyDue
    ) { list, reasons, pauses, onlyDue ->
        HabitsUiState(
            habits = list,
            skipReasons = reasons,
            pauses = pauses,
            today = clock.today(),
            showOnlyDueToday = onlyDue
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HabitsUiState(today = clock.today())
    )

    init {
        viewModelScope.launch {
            habits.ensureBuiltinData()
            _presets.value = presetLoader.load()
        }
    }

    // --- отметки -----------------------------------------------------------

    /**
     * Основное действие карточки.
     *
     * Счётчик прибавляет шаг, а не закрывает день целиком: «выпил ещё стакан»
     * и «выполнил норму» — разные события. Полное закрытие счётчика делается
     * из карточки долгим тапом.
     */
    fun onPrimaryAction(progress: HabitWithProgress) {
        val habit = progress.habit
        viewModelScope.launch {
            when {
                habit.type == HabitType.COUNTER -> {
                    val step = counterStep(habit)
                    val next = progress.todayValue + step
                    markHabit(
                        progress = progress,
                        status = statusForValue(progress, next),
                        value = next
                    )
                }

                progress.doneToday -> clearHabitMark(habit.id, clock.today())

                else -> markHabit(progress, EntryStatus.DONE)
            }
        }
    }

    /** «Минимум» — вторая кнопка на карточке (п. 5). */
    fun markMinimum(progress: HabitWithProgress) {
        viewModelScope.launch { markHabit(progress, EntryStatus.MINIMUM) }
    }

    fun markSkipped(progress: HabitWithProgress, reasonId: Long?) {
        viewModelScope.launch {
            habits.mark(
                habitId = progress.habit.id,
                date = clock.today(),
                status = EntryStatus.SKIPPED,
                skipReasonId = reasonId
            )
        }
    }

    fun setCounterValue(progress: HabitWithProgress, value: Float) {
        viewModelScope.launch {
            markHabit(progress, statusForValue(progress, value), value = value)
        }
    }

    /**
     * Статус по набранному значению счётчика: норма закрывает день полностью,
     * минимальная планка — наполовину, остальное считается пропуском.
     */
    private fun statusForValue(progress: HabitWithProgress, value: Float): EntryStatus {
        val habit = progress.habit
        return when {
            value >= habit.targetValue -> EntryStatus.DONE
            habit.hasMinimum && value >= (habit.minimumValue ?: 0f) -> EntryStatus.MINIMUM
            else -> EntryStatus.SKIPPED
        }
    }

    fun setNote(progress: HabitWithProgress, note: String?) {
        viewModelScope.launch {
            val status = progress.todayEntry?.status ?: EntryStatus.DONE
            habits.mark(
                habitId = progress.habit.id,
                date = clock.today(),
                status = status,
                value = progress.todayValue,
                note = note.orEmpty()
            )
        }
    }

    fun clearMark(progress: HabitWithProgress) {
        viewModelScope.launch { clearHabitMark(progress.habit.id, clock.today()) }
    }

    /** @return false, если месячная квота заморозок исчерпана. */
    suspend fun freezeToday(progress: HabitWithProgress): Boolean =
        habits.freeze(progress.habit.id, clock.today())

    // --- пауза и отпуск ----------------------------------------------------

    fun toggleVacation() {
        viewModelScope.launch {
            val open = state.value.pauses.firstOrNull { it.habitId == null && it.endDate == null }
            if (open != null) habits.resumePause(open.id)
            else habits.pause(null, clock.today(), null, "Отпуск")
        }
    }

    fun pauseHabit(progress: HabitWithProgress, until: LocalDate?) {
        viewModelScope.launch {
            habits.pause(progress.habit.id, clock.today(), until)
        }
    }

    fun resumeHabit(progress: HabitWithProgress) {
        viewModelScope.launch {
            state.value.pauses
                .filter { it.habitId == progress.habit.id && it.covers(clock.today()) }
                .forEach { habits.resumePause(it.id) }
        }
    }

    // --- список ------------------------------------------------------------

    fun addFromPreset(preset: Habit) {
        viewModelScope.launch {
            val id = habits.upsertHabit(preset.copy(createdAt = clock.now()))
            habits.getHabit(id)?.let(reminderScheduler::schedule)
        }
    }

    fun archive(progress: HabitWithProgress) {
        viewModelScope.launch {
            reminderScheduler.cancel(progress.habit.id)
            habits.archiveHabit(progress.habit.id)
        }
    }

    fun toggleShowOnlyDue() { showOnlyDue.value = !showOnlyDue.value }

    /** Шаг счётчика: для крупных целей вроде 10 000 шагов единица бессмысленна. */
    private fun counterStep(habit: Habit): Float = when {
        habit.targetValue >= 1000f -> habit.targetValue / 10f
        habit.targetValue >= 100f -> 10f
        else -> 1f
    }
}
