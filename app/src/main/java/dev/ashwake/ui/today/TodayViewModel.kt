package dev.ashwake.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ashwake.core.time.AppClock
import dev.ashwake.domain.model.habits.EntrySource
import dev.ashwake.domain.model.habits.EntryStatus
import dev.ashwake.domain.model.habits.HabitWithProgress
import dev.ashwake.domain.model.tasks.Task
import dev.ashwake.domain.model.tasks.TaskStatus
import dev.ashwake.domain.repository.habits.HabitRepository
import dev.ashwake.domain.repository.tasks.TaskRepository
import dev.ashwake.domain.repository.timebox.TimeboxRepository
import dev.ashwake.domain.usecase.habits.MarkHabitUseCase
import dev.ashwake.domain.usecase.tasks.CompleteTaskUseCase
import dev.ashwake.domain.usecase.tasks.PostponeTaskUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Экран «Сегодня»: план дня, задачи на сегодня и привычки — одним взглядом.
 *
 * Позиционируется как главная отправная точка дня: сюда ведёт нижняя
 * навигация, отсюда же расходятся быстрые действия.
 */
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val tasks: TaskRepository,
    private val habits: HabitRepository,
    private val timebox: TimeboxRepository,
    private val completeTask: CompleteTaskUseCase,
    private val postponeTask: PostponeTaskUseCase,
    private val markHabit: MarkHabitUseCase,
    private val clock: AppClock
) : ViewModel() {

    private val today = clock.today()

    /** Задачи: просроченные и запланированные на сегодня, без выполненных. */
    val tasksToday: StateFlow<List<Task>> = combine(
        tasks.observeTasksInRange(today, today, includeDone = false),
        tasks.observeTasksInRange(
            today.minusYears(5),
            today.minusDays(1),
            includeDone = false
        )
    ) { planned, overdue ->
        (planned + overdue)
            .filter { it.status == TaskStatus.ACTIVE && !it.isTemplate }
            .distinctBy { it.id }
            .sortedBy { it.dueTime?.toSecondOfDay() ?: Int.MAX_VALUE }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Привычки дня с прогрессом и статусом отметки. */
    val habitsToday: StateFlow<List<HabitWithProgress>> =
        habits.observeHabitsWithProgress(today)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** План дня из таймбоксинга. */
    val dayPlan = timebox.observeDay(today)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun complete(task: Task) {
        viewModelScope.launch {
            completeTask(task.id)
            _message.value = "Задача закрыта: ${task.title}"
        }
    }

    fun postpone(task: Task) {
        viewModelScope.launch {
            postponeTask(task.id)
            _message.value = "Перенесено на завтра: ${task.title}"
        }
    }

    fun toggleHabit(progress: HabitWithProgress) {
        viewModelScope.launch {
            val status = if (progress.doneToday) EntryStatus.SKIPPED else EntryStatus.DONE
            markHabit(progress, status, source = EntrySource.MANUAL)
        }
    }

    fun planDay() {
        viewModelScope.launch {
            val result = timebox.planDay(today)
            _message.value = when {
                result.blocks.isEmpty() && result.withoutEstimate.isNotEmpty() ->
                    "Нет задач с оценками времени"
                result.deficitMinutes > 0 ->
                    "Разложено ${result.blocks.size} задач, не влезло ${result.deferred.size}"
                else -> "День разложен: ${result.blocks.size} задач"
            }
        }
    }

    fun consumeMessage() { _message.value = null }
}

