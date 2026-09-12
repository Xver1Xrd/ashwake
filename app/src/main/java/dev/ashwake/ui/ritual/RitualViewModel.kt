package dev.ashwake.ui.ritual

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ashwake.core.time.AppClock
import dev.ashwake.data.settings.AppSettings
import dev.ashwake.domain.engine.ritual.RitualAccessEvaluator
import dev.ashwake.domain.engine.ritual.RitualAccessStatus
import dev.ashwake.domain.model.habits.EntryStatus
import dev.ashwake.domain.model.habits.HabitWithProgress
import dev.ashwake.domain.model.ritual.DailyReview
import dev.ashwake.domain.model.ritual.ReviewCompletion
import dev.ashwake.domain.model.tasks.PostponeSource
import dev.ashwake.domain.model.tasks.Task
import dev.ashwake.domain.repository.ritual.RitualRepository
import dev.ashwake.domain.repository.ritual.RitualState
import dev.ashwake.domain.repository.tasks.TaskFilter
import dev.ashwake.domain.repository.tasks.TaskRepository
import dev.ashwake.domain.repository.timebox.TimeboxRepository
import dev.ashwake.domain.usecase.habits.MarkHabitUseCase
import dev.ashwake.domain.usecase.ritual.CompleteRitualUseCase
import dev.ashwake.domain.usecase.tasks.CompleteTaskUseCase
import dev.ashwake.domain.usecase.tasks.DeleteTaskUseCase
import dev.ashwake.domain.usecase.tasks.PostponeTaskUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/** Пять шагов ритуала из п. 9. */
enum class RitualStep { SCALES, TASKS, HABITS, TOMORROW, NOTE }

data class RitualForm(
    val dayRating: Int? = null,
    val mood: Int? = null,
    val energy: Int? = null,
    val note: String = "",
    val topTaskIds: List<Long> = emptyList(),
    val planTomorrow: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RitualViewModel @Inject constructor(
    private val ritual: RitualRepository,
    private val markHabit: MarkHabitUseCase,
    private val postponeTask: PostponeTaskUseCase,
    private val deleteTask: DeleteTaskUseCase,
    private val completeTask: CompleteTaskUseCase,
    private val tasks: TaskRepository,
    private val timebox: TimeboxRepository,
    private val completeRitual: CompleteRitualUseCase,
    private val evaluator: RitualAccessEvaluator,
    private val settings: AppSettings,
    private val clock: AppClock
) : ViewModel() {

    private val ticker = MutableStateFlow(clock.now())

    val allReviews: StateFlow<List<DailyReview>> = ritual.observeAllReviews()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val taskTitles: StateFlow<Map<Long, String>> = tasks.observeTasks(TaskFilter(includeDone = true))
        .map { list -> list.associate { it.id to it.title } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val accessStatus: StateFlow<RitualAccessStatus> = combine(
        ticker,
        settings.dayStartHour,
        allReviews
    ) { nowInstant, dayStart, reviews ->
        val zone = clock.zone()
        val currentDateTime = LocalDateTime.ofInstant(nowInstant, zone)
        evaluator.evaluate(currentDateTime, dayStart) { targetDate ->
            reviews.any { it.date == targetDate }
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        evaluator.evaluate(
            LocalDateTime.ofInstant(clock.now(), clock.zone()),
            4
        ) { false }
    )

    private val _viewingHistory = MutableStateFlow(false)
    val viewingHistory: StateFlow<Boolean> = _viewingHistory.asStateFlow()

    private val _date = MutableStateFlow(clock.today())
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    val state: StateFlow<RitualState> = _date
        .flatMapLatest { ritual.observeRitual(it) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            RitualState(clock.today(), null)
        )

    private val _step = MutableStateFlow(RitualStep.SCALES)
    val step: StateFlow<RitualStep> = _step.asStateFlow()

    private val _form = MutableStateFlow(RitualForm())
    val form: StateFlow<RitualForm> = _form.asStateFlow()

    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    init {
        // Синхронизируем дату с вычисленным статусом окна
        viewModelScope.launch {
            accessStatus.collect { status ->
                val target = when (status) {
                    is RitualAccessStatus.Available -> status.targetDate
                    is RitualAccessStatus.AlreadyCompleted -> status.targetDate
                    is RitualAccessStatus.TooEarly -> status.targetDate
                }
                _date.value = target
            }
        }

        // Тикер для пересчёта доступности окна
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                ticker.value = clock.now()
            }
        }
    }

    fun openHistory() { _viewingHistory.value = true }
    fun closeHistory() { _viewingHistory.value = false }
    fun toggleHistory() { _viewingHistory.value = !_viewingHistory.value }

    // --- шаги ---------------------------------------------------------------

    fun next() {
        val order = RitualStep.entries
        val index = order.indexOf(_step.value)
        if (index < order.lastIndex) _step.value = order[index + 1] else finish()
    }

    fun back() {
        val order = RitualStep.entries
        val index = order.indexOf(_step.value)
        if (index > 0) _step.value = order[index - 1]
    }

    fun goTo(step: RitualStep) { _step.value = step }

    // --- шаг 1: шкалы -------------------------------------------------------

    fun setDayRating(value: Int) = _form.update { it.copy(dayRating = value) }
    fun setMood(value: Int) = _form.update { it.copy(mood = value) }
    fun setEnergy(value: Int) = _form.update { it.copy(energy = value) }

    // --- шаг 2: незакрытые задачи -------------------------------------------

    fun postponeToTomorrow(task: Task) {
        viewModelScope.launch {
            postponeTask(task.id, _date.value.plusDays(1), PostponeSource.RITUAL)
        }
    }

    fun complete(task: Task) {
        viewModelScope.launch {
            completeTask(task.id)
        }
    }

    /** Разбор пачкой: одна кнопка вместо десяти свайпов. */
    fun postponeAll(tasks: List<Task>) {
        viewModelScope.launch {
            tasks.forEach { postponeTask(it.id, _date.value.plusDays(1), PostponeSource.RITUAL) }
        }
    }

    fun drop(task: Task) {
        viewModelScope.launch { deleteTask(task.id) }
    }

    // --- шаг 3: непроставленные привычки ------------------------------------

    fun markDone(progress: HabitWithProgress) {
        viewModelScope.launch {
            markHabit(
                progress = progress,
                status = EntryStatus.DONE,
                date = _date.value,
                source = dev.ashwake.domain.model.habits.EntrySource.RITUAL
            )
        }
    }

    fun markSkipped(progress: HabitWithProgress) {
        viewModelScope.launch {
            markHabit(
                progress = progress,
                status = EntryStatus.SKIPPED,
                date = _date.value,
                source = dev.ashwake.domain.model.habits.EntrySource.RITUAL
            )
        }
    }

    // --- шаг 4: три главные задачи ------------------------------------------

    fun toggleTopTask(task: Task) {
        _form.update { current ->
            val ids = current.topTaskIds
            when {
                task.id in ids -> current.copy(topTaskIds = ids - task.id)
                ids.size >= TOP_LIMIT -> current
                else -> current.copy(topTaskIds = ids + task.id)
            }
        }
    }

    fun setPlanTomorrow(value: Boolean) = _form.update { it.copy(planTomorrow = value) }

    // --- шаг 5: заметка и сохранение ----------------------------------------

    fun setNote(value: String) = _form.update { it.copy(note = value) }

    fun finish() {
        val currentStatus = accessStatus.value
        if (currentStatus !is RitualAccessStatus.Available) return

        val form = _form.value
        val date = currentStatus.targetDate
        val alreadyReviewed = state.value.review != null

        viewModelScope.launch {
            completeRitual(
                date = date,
                dayRating = form.dayRating,
                mood = form.mood,
                energy = form.energy,
                note = form.note,
                topTaskIds = form.topTaskIds,
                alreadyReviewed = alreadyReviewed,
                planTomorrow = form.planTomorrow
            )
            _finished.value = true
        }
    }

    fun reset() {
        _finished.value = false
        _step.value = RitualStep.SCALES
        _form.value = RitualForm()
    }

    private companion object {
        const val TOP_LIMIT = 3
    }
}
