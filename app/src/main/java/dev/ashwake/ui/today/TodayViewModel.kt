package dev.ashwake.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ashwake.core.time.AppClock
import dev.ashwake.domain.model.habits.EntryStatus
import dev.ashwake.domain.model.habits.Habit
import dev.ashwake.domain.model.habits.HabitType
import dev.ashwake.domain.model.habits.HabitWithProgress
import dev.ashwake.domain.model.tasks.Task
import dev.ashwake.domain.repository.abstinence.AbstinenceRepository
import dev.ashwake.domain.repository.abstinence.AbstinenceWithStats
import dev.ashwake.domain.repository.habits.HabitRepository
import dev.ashwake.domain.repository.tasks.TaskRepository
import dev.ashwake.domain.usecase.habits.ClearHabitMarkUseCase
import dev.ashwake.domain.usecase.habits.MarkHabitUseCase
import dev.ashwake.domain.usecase.tasks.CompleteTaskUseCase
import dev.ashwake.domain.usecase.tasks.PostponeTaskUseCase
import dev.ashwake.domain.usecase.tasks.UndoPostponeUseCase
import dev.ashwake.domain.usecase.tasks.ReopenTaskUseCase
import dev.ashwake.ui.components.FlameLevel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Состояние главного экрана.
 *
 * Экран собирает четыре источника — персонажа, задачи на сегодня, привычки
 * и отказы — потому что смысл экрана именно в том, чтобы держать их рядом.
 * Раньше отказы жили только на своём экране, и человек, который бросает
 * курить, видел свой счётчик, только если специально за ним ходил.
 */
data class TodayUiState(
    val today: LocalDate = EPOCH_DAY,
    val selectedDate: LocalDate = EPOCH_DAY,
    val tasks: List<Task> = emptyList(),
    val habits: List<HabitWithProgress> = emptyList(),
    val abstinences: List<AbstinenceWithStats> = emptyList(),
    /** Первый запрос к базе ещё не вернулся: показываем заготовку, а не пустоту. */
    val loading: Boolean = true
) {
    val isSelectedToday: Boolean get() = selectedDate == today

    /** Задачи, у которых срок раньше выбранной даты. */
    val overdueTasks: List<Task> get() = if (isSelectedToday) tasks.filter { it.isOverdue(today) } else emptyList()

    /** Всё, что относится к выбранному дню. */
    val todayTasks: List<Task> get() = if (isSelectedToday) tasks.filterNot { it.isOverdue(today) } else tasks

    /** Сделано дел за день: привычки плюс задачи, одним числом на обложке. */
    val doneCount: Int
        get() = habits.count { it.doneToday } + tasks.count { it.isDone }

    val totalCount: Int
        get() = habits.size + tasks.size

    val progress: Float
        get() = if (totalCount == 0) 0f else doneCount.toFloat() / totalCount

    /** Пусто ли на экране совсем: тогда вместо списков показывается объяснение. */
    val isEmpty: Boolean
        get() = tasks.isEmpty() && habits.isEmpty() && abstinences.isEmpty()

    val pendingHabits: List<HabitWithProgress> get() = habits.filterNot { it.doneToday }
    val pendingTasks: List<Task> get() = tasks.filterNot { it.isDone }

    /**
     * Уровень огонька активности:
     * Высокая активность -> BLAZING.
     * Умеренная -> BURNING.
     * Затухает -> EMBER / DIM.
     * Заброшено -> ASH (пепел).
     * При возвращении огонёк возрождается в обратном порядке.
     */
    val flameLevel: FlameLevel
        get() {
            val maxStreak = habits.maxOfOrNull { it.currentStreak } ?: 0
            return when {
                doneCount >= 3 || (doneCount >= 1 && maxStreak >= 3) -> FlameLevel.BLAZING
                doneCount in 1..2 || maxStreak in 1..2 -> FlameLevel.BURNING
                maxStreak > 0 || tasks.any { it.isDone } -> FlameLevel.EMBER
                habits.any { it.score > 0.2f } -> FlameLevel.DIM
                else -> FlameLevel.ASH
            }
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val habits: HabitRepository,
    private val tasks: TaskRepository,
    private val abstinences: AbstinenceRepository,
    private val markHabit: MarkHabitUseCase,
    private val clearHabitMark: ClearHabitMarkUseCase,
    private val completeTask: CompleteTaskUseCase,
    private val postpone: PostponeTaskUseCase,
    private val undoPostpone: UndoPostponeUseCase,
    private val reopenTask: ReopenTaskUseCase,
    private val clock: AppClock
) : ViewModel() {

    /**
     * «Сейчас» для счётчиков отказов. Тикает раз в минуту, а не раз в секунду:
     * на главном экране показаны дни, и обновлять их чаще — значит пересобирать
     * весь список ради цифры, которая не меняется.
     */
    private val minuteTicker = MutableStateFlow(clock.now())
    private val _selectedDate = MutableStateFlow(clock.today())

    val state: StateFlow<TodayUiState> = _selectedDate.flatMapLatest { selDate ->
        combine(
            habits.observeHabitsWithProgress(selDate),
            tasks.observeTasksForDay(selDate),
            minuteTicker.flatMapLatest { now -> abstinences.observeAll(now) }
        ) { habitList, taskList, abstinenceList ->
            TodayUiState(
                loading = false,
                today = clock.today(),
                selectedDate = selDate,
                tasks = taskList,
                habits = habitList.filter { it.dueToday && !it.paused },
                abstinences = abstinenceList.filterNot { it.abstinence.archived }
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        TodayUiState(today = clock.today(), selectedDate = clock.today())
    )

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun goToToday() {
        _selectedDate.value = clock.today()
    }

    init {
        viewModelScope.launch {
            abstinences.ensureBuiltinData()
        }
        viewModelScope.launch {
            while (true) {
                delay(TICK_MILLIS)
                minuteTicker.value = clock.now()
            }
        }
    }

    /**
     * Отметка привычки прямо из главного экрана: тап по кругу в строке.
     *
     * Счётчик прибавляет шаг, а не закрывает норму целиком — правило то же,
     * что и на экране привычек: «выпил ещё стакан» и «выполнил норму»
     * это разные события.
     */
    fun toggleHabit(progress: HabitWithProgress) {
        val habit = progress.habit
        val date = _selectedDate.value
        viewModelScope.launch {
            when {
                habit.type == HabitType.COUNTER -> {
                    val next = progress.todayValue + counterStep(habit)
                    markHabit(progress, statusForValue(progress, next), date = date, value = next)
                }

                progress.doneToday -> clearHabitMark(habit.id, date)

                else -> markHabit(progress, EntryStatus.DONE, date = date)
            }
        }
    }

    /** Установка точного прогресса счётчика (из диалога быстрого ввода). */
    fun setHabitProgress(progress: HabitWithProgress, value: Float) {
        val habit = progress.habit
        val date = _selectedDate.value
        viewModelScope.launch {
            if (value <= 0f) {
                clearHabitMark(habit.id, date)
            } else {
                markHabit(progress, statusForValue(progress, value), date = date, value = value)
            }
        }
    }

    /** Свайп влево: перенос на следующий день от выбранного. */
    fun postponeTask(task: Task) {
        viewModelScope.launch { postpone(task.id, _selectedDate.value.plusDays(1)) }
    }

    /** Отмена переноса по кнопке на плашке. */
    fun undoPostpone(taskId: Long) {
        viewModelScope.launch { undoPostpone.invoke(taskId) }
    }

    fun toggleTask(task: Task) {
        viewModelScope.launch {
            if (task.isDone) reopenTask(task.id) else completeTask(task.id)
        }
    }

    fun reorderHabits(fromIndex: Int, toIndex: Int) {
        val current = state.value.habits
        if (fromIndex !in current.indices || toIndex !in current.indices || fromIndex == toIndex) return
        val list = current.toMutableList()
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        viewModelScope.launch {
            habits.reorderHabits(list.map { it.habit.id })
        }
    }

    /** Шаг счётчика — тот же, что на экране привычек. */
    private fun counterStep(habit: Habit): Float = when {
        habit.targetValue >= 1000f -> habit.targetValue / 10f
        habit.targetValue >= 100f -> 10f
        else -> 1f
    }

    private fun statusForValue(progress: HabitWithProgress, value: Float): EntryStatus {
        val habit = progress.habit
        return when {
            value >= habit.targetValue -> EntryStatus.DONE
            habit.hasMinimum && value >= (habit.minimumValue ?: 0f) -> EntryStatus.MINIMUM
            else -> EntryStatus.SKIPPED
        }
    }

    private companion object {
        const val TICK_MILLIS = 60_000L
    }
}

/**
 * Заглушка «даты ещё нет» до первого значения из репозитория.
 *
 * Не `LocalDate.EPOCH`: это поле появилось только в API 34, а приложение
 * живёт с 26. Компилятор такое пропускает молча, падает оно на устройстве.
 */
private val EPOCH_DAY: LocalDate = LocalDate.ofEpochDay(0)
