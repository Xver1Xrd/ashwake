package dev.ashwake.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ashwake.core.time.AppClock
import dev.ashwake.data.assets.HabitPresetCategory
import dev.ashwake.data.assets.HabitPresetLoader
import dev.ashwake.data.settings.AppSettings
import dev.ashwake.domain.model.habits.Habit
import dev.ashwake.domain.repository.character.CharacterRepository
import dev.ashwake.domain.repository.habits.HabitRepository
import dev.ashwake.domain.scheduler.HabitReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingState(
    val categories: List<HabitPresetCategory> = emptyList(),
    /** Выбранные привычки: по имени, потому что у пресета ещё нет id. */
    val picked: Set<String> = emptySet(),
    val finished: Boolean = false
)

/**
 * Знакомство при первом запуске.
 *
 * Единственное, что оно делает с данными, — заводит выбранные привычки.
 * Ничего не навязывает: пропустить можно на любом шаге, и приложение
 * останется ровно в том же состоянии, что и без знакомства.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val habits: HabitRepository,
    private val character: CharacterRepository,
    private val presetLoader: HabitPresetLoader,
    private val reminderScheduler: HabitReminderScheduler,
    private val settings: AppSettings,
    private val clock: AppClock
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Персонаж собирается уже здесь: на шаге про него он должен быть
            // одет, иначе знакомство показывает пустую фигуру
            character.ensureBuiltinData()
            _state.update { it.copy(categories = presetLoader.load()) }
        }
    }

    fun toggle(habit: Habit) {
        _state.update { current ->
            val picked = if (habit.name in current.picked) {
                current.picked - habit.name
            } else {
                current.picked + habit.name
            }
            current.copy(picked = picked)
        }
    }

    /** Заводит выбранное и закрывает знакомство. Пустой выбор — тоже выбор. */
    fun finish() {
        viewModelScope.launch {
            val chosen = _state.value.let { current ->
                current.categories.flatMap { it.habits }.filter { it.name in current.picked }
            }
            chosen.forEach { preset ->
                val id = habits.upsertHabit(preset.copy(createdAt = clock.now()))
                habits.getHabit(id)?.let(reminderScheduler::schedule)
            }
            settings.setOnboardingDone()
            _state.update { it.copy(finished = true) }
        }
    }

    fun skip() {
        viewModelScope.launch {
            settings.setOnboardingDone()
            _state.update { it.copy(finished = true) }
        }
    }
}
