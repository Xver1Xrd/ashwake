package dev.ashwake.ui.ritual

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ashwake.core.time.AppClock
import dev.ashwake.domain.engine.character.StatSource
import dev.ashwake.domain.engine.reward.RewardContext
import dev.ashwake.domain.engine.reward.RewardSource
import dev.ashwake.domain.model.habits.EntryStatus
import dev.ashwake.domain.model.habits.HabitWithProgress
import dev.ashwake.domain.model.ritual.ReviewCompletion
import dev.ashwake.domain.model.tasks.PostponeSource
import dev.ashwake.domain.model.tasks.Task
import dev.ashwake.domain.repository.character.CharacterRepository
import dev.ashwake.domain.repository.ritual.RitualRepository
import dev.ashwake.domain.repository.ritual.RitualState
import dev.ashwake.domain.repository.timebox.TimeboxRepository
import dev.ashwake.domain.usecase.habits.MarkHabitUseCase
import dev.ashwake.domain.usecase.tasks.DeleteTaskUseCase
import dev.ashwake.domain.usecase.tasks.PostponeTaskUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
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
    private val timebox: TimeboxRepository,
    private val character: CharacterRepository,
    private val clock: AppClock
) : ViewModel() {

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
        // Утром ритуал может относиться ко вчера: пропущенный день не теряется
        viewModelScope.launch { _date.value = ritual.currentRitualDate() }
    }

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
        val form = _form.value
        val date = _date.value

        viewModelScope.launch {
            ritual.saveReview(
                date = date,
                dayRating = form.dayRating,
                mood = form.mood,
                energy = form.energy,
                note = form.note,
                topTaskIds = form.topTaskIds,
                completedAs = if (date < clock.today()) ReviewCompletion.NEXT_MORNING
                else ReviewCompletion.EVENING
            )

            // Награда только за первое прохождение дня: переоткрыть и «пройти»
            // ритуал второй раз не должно приносить монет
            if (state.value.review == null) {
                character.grantReward(
                    RewardContext(
                        source = RewardSource.RITUAL_DONE,
                        time = clock.now().atZone(clock.zone()).toLocalTime()
                    ),
                    refId = date.toString()
                )
                character.grantStatPoints(StatSource.RITUAL_DONE, refId = date.toString())
            }

            if (form.planTomorrow) timebox.planDay(date.plusDays(1))
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
