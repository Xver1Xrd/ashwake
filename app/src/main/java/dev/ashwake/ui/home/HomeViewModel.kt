package dev.ashwake.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ashwake.core.time.AppClock
import dev.ashwake.data.assets.Catalog
import dev.ashwake.data.assets.CatalogLoader
import dev.ashwake.domain.model.tasks.TaskStatus
import dev.ashwake.domain.repository.character.CharacterRepository
import dev.ashwake.domain.repository.character.CharacterState
import dev.ashwake.domain.repository.habits.HabitRepository
import dev.ashwake.domain.repository.tasks.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Сводка дня для главного экрана. */
data class HomeSummary(
    val tasksPlanned: Int = 0,
    val tasksDone: Int = 0,
    val habitsPlanned: Int = 0,
    val habitsDone: Int = 0
) {
    val tasksProgress: Float
        get() = if (tasksPlanned == 0) 1f else tasksDone.toFloat() / tasksPlanned

    val habitsProgress: Float
        get() = if (habitsPlanned == 0) 1f else habitsDone.toFloat() / habitsPlanned
}

/**
 * Главный экран: персонаж и день одним взглядом (п. 15.9).
 *
 * Показывает рендер персонажа с надетым снаряжением, уровень и кошелёк,
 * а под ними — прогресс дня: сколько задач и привычек закрыто.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val character: CharacterRepository,
    private val tasks: TaskRepository,
    private val habits: HabitRepository,
    private val catalogLoader: CatalogLoader,
    private val clock: AppClock
) : ViewModel() {

    val state: StateFlow<CharacterState> = character.observeState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CharacterState())

    private val today = clock.today()

    val summary: StateFlow<HomeSummary> = combine(
        tasks.observeTasksInRange(today, today, includeDone = true),
        habits.observeHabitsWithProgress(today)
    ) { tasksToday, habitsToday ->
        HomeSummary(
            tasksPlanned = tasksToday.size,
            tasksDone = tasksToday.count { it.status == TaskStatus.DONE },
            habitsPlanned = habitsToday.count { it.dueToday },
            habitsDone = habitsToday.count { it.doneToday }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeSummary())

    private val _catalog = MutableStateFlow(Catalog.EMPTY)
    val catalog: StateFlow<Catalog> = _catalog.asStateFlow()

    init {
        viewModelScope.launch { _catalog.value = catalogLoader.load() }
    }
}
