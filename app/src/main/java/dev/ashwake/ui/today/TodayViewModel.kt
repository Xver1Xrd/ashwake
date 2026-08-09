package dev.ashwake.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ashwake.core.time.AppClock
import dev.ashwake.data.assets.Catalog
import dev.ashwake.data.assets.CatalogLoader
import dev.ashwake.domain.model.habits.EntryStatus
import dev.ashwake.domain.model.habits.Habit
import dev.ashwake.domain.model.habits.HabitType
import dev.ashwake.domain.model.habits.HabitWithProgress
import dev.ashwake.domain.model.tasks.Task
import dev.ashwake.domain.repository.character.CharacterRepository
import dev.ashwake.domain.repository.character.CharacterState
import dev.ashwake.domain.repository.habits.HabitRepository
import dev.ashwake.domain.repository.tasks.TaskRepository
import dev.ashwake.domain.usecase.habits.MarkHabitUseCase
import dev.ashwake.domain.usecase.tasks.CompleteTaskUseCase
import dev.ashwake.domain.usecase.tasks.ReopenTaskUseCase
import dev.ashwake.ui.character.render.CharacterLayer
import dev.ashwake.ui.character.render.buildCharacterLayers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Состояние главного экрана.
 *
 * Экран собирает три источника — привычки на сегодня, задачи на сегодня
 * и персонажа — потому что смысл экрана именно в том, чтобы держать их
 * рядом: диорама сверху и список дел под ней это один непрерывный жест,
 * а не три раздела.
 */
data class TodayUiState(
    val today: LocalDate = LocalDate.EPOCH,
    val habits: List<HabitWithProgress> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val character: CharacterState = CharacterState(),
    val layers: List<CharacterLayer> = emptyList()
) {
    /** Сделано дел за день: привычки плюс задачи, одним числом на обложке. */
    val doneCount: Int
        get() = habits.count { it.doneToday } + tasks.count { it.isDone }

    val totalCount: Int
        get() = habits.size + tasks.size

    val progress: Float
        get() = if (totalCount == 0) 0f else doneCount.toFloat() / totalCount

    val pendingHabits: List<HabitWithProgress> get() = habits.filterNot { it.doneToday }
    val pendingTasks: List<Task> get() = tasks.filterNot { it.isDone }
}

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val habits: HabitRepository,
    private val tasks: TaskRepository,
    private val character: CharacterRepository,
    private val catalogLoader: CatalogLoader,
    private val markHabit: MarkHabitUseCase,
    private val completeTask: CompleteTaskUseCase,
    private val reopenTask: ReopenTaskUseCase,
    private val clock: AppClock
) : ViewModel() {

    private val catalog = MutableStateFlow(Catalog.EMPTY)

    val state: StateFlow<TodayUiState> = combine(
        habits.observeHabitsWithProgress(clock.today()),
        tasks.observeTasksInRange(clock.today(), clock.today(), includeDone = true),
        character.observeState(),
        catalog
    ) { habitList, taskList, characterState, loadedCatalog ->
        TodayUiState(
            today = clock.today(),
            // На главном экране только то, что сегодня действительно требуется:
            // приостановленные привычки не должны занимать место и портить счёт
            habits = habitList.filter { it.dueToday && !it.paused },
            tasks = taskList.filterNot { it.isTemplate },
            character = characterState,
            layers = buildCharacterLayers(
                characterState.equipped.values,
                loadedCatalog.paletteTints
            )
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        TodayUiState(today = clock.today())
    )

    init {
        viewModelScope.launch { catalog.value = catalogLoader.load() }
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
        viewModelScope.launch {
            when {
                habit.type == HabitType.COUNTER -> {
                    val next = progress.todayValue + counterStep(habit)
                    markHabit(progress, statusForValue(progress, next), value = next)
                }

                progress.doneToday -> habits.clearMark(habit.id, clock.today())

                else -> markHabit(progress, EntryStatus.DONE)
            }
        }
    }

    fun toggleTask(task: Task) {
        viewModelScope.launch {
            if (task.isDone) reopenTask(task.id) else completeTask(task.id)
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
}
