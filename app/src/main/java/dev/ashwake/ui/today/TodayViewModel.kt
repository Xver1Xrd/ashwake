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
import dev.ashwake.domain.repository.abstinence.AbstinenceRepository
import dev.ashwake.domain.repository.abstinence.AbstinenceWithStats
import dev.ashwake.domain.repository.character.CharacterRepository
import dev.ashwake.domain.repository.character.CharacterState
import dev.ashwake.domain.repository.habits.HabitRepository
import dev.ashwake.domain.repository.tasks.TaskRepository
import dev.ashwake.domain.usecase.habits.ClearHabitMarkUseCase
import dev.ashwake.domain.usecase.habits.MarkHabitUseCase
import dev.ashwake.domain.usecase.tasks.CompleteTaskUseCase
import dev.ashwake.domain.usecase.tasks.PostponeTaskUseCase
import dev.ashwake.domain.usecase.tasks.ReopenTaskUseCase
import dev.ashwake.ui.character.render.CharacterLayer
import dev.ashwake.ui.character.render.buildCharacterLayers
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
    val today: LocalDate = LocalDate.EPOCH,
    val tasks: List<Task> = emptyList(),
    val habits: List<HabitWithProgress> = emptyList(),
    val abstinences: List<AbstinenceWithStats> = emptyList(),
    val character: CharacterState = CharacterState(),
    val layers: List<CharacterLayer> = emptyList(),
    /** Первый запрос к базе ещё не вернулся: показываем заготовку, а не пустоту. */
    val loading: Boolean = true
) {
    /** Задачи, у которых срок раньше сегодняшнего. Показываются отдельной группой. */
    val overdueTasks: List<Task> get() = tasks.filter { it.isOverdue(today) }

    /** Всё, что относится к сегодняшнему дню, без просрочки. */
    val todayTasks: List<Task> get() = tasks.filterNot { it.isOverdue(today) }

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
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val habits: HabitRepository,
    private val tasks: TaskRepository,
    private val abstinences: AbstinenceRepository,
    private val character: CharacterRepository,
    private val catalogLoader: CatalogLoader,
    private val markHabit: MarkHabitUseCase,
    private val clearHabitMark: ClearHabitMarkUseCase,
    private val completeTask: CompleteTaskUseCase,
    private val postpone: PostponeTaskUseCase,
    private val reopenTask: ReopenTaskUseCase,
    private val clock: AppClock
) : ViewModel() {

    private val catalog = MutableStateFlow(Catalog.EMPTY)

    /**
     * «Сейчас» для счётчиков отказов. Тикает раз в минуту, а не раз в секунду:
     * на главном экране показаны дни, и обновлять их чаще — значит пересобирать
     * весь список ради цифры, которая не меняется.
     */
    private val minuteTicker = MutableStateFlow(clock.now())

    val state: StateFlow<TodayUiState> = combine(
        habits.observeHabitsWithProgress(clock.today()),
        tasks.observeTasksForDay(clock.today()),
        minuteTicker.flatMapLatest { now -> abstinences.observeAll(now) },
        character.observeState(),
        catalog
    ) { habitList, taskList, abstinenceList, characterState, loadedCatalog ->
        TodayUiState(
            loading = false,
            today = clock.today(),
            tasks = taskList,
            // На главном экране только то, что сегодня действительно требуется:
            // приостановленные привычки не должны занимать место и портить счёт
            habits = habitList.filter { it.dueToday && !it.paused },
            abstinences = abstinenceList.filterNot { it.abstinence.archived },
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
        viewModelScope.launch {
            // Персонаж должен быть одет и до первого захода в магазин, иначе
            // главный экран встречает пустой фигурой
            character.ensureBuiltinData()
            abstinences.ensureBuiltinData()
            catalog.value = catalogLoader.load()
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
        viewModelScope.launch {
            when {
                habit.type == HabitType.COUNTER -> {
                    val next = progress.todayValue + counterStep(habit)
                    markHabit(progress, statusForValue(progress, next), value = next)
                }

                progress.doneToday -> clearHabitMark(habit.id, clock.today())

                else -> markHabit(progress, EntryStatus.DONE)
            }
        }
    }

    /** Свайп влево: перенос на завтра. То же действие, что на экране задач. */
    fun postponeTask(task: Task) {
        viewModelScope.launch { postpone(task.id, clock.today().plusDays(1)) }
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

    private companion object {
        const val TICK_MILLIS = 60_000L
    }
}
