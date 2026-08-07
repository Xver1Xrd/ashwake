package dev.ashwake.ui.habits.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ashwake.core.time.AppClock
import dev.ashwake.domain.model.habits.EntryStatus
import dev.ashwake.domain.repository.habits.HabitDetail
import dev.ashwake.domain.repository.habits.HabitRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class HabitDetailViewModel @Inject constructor(
    private val habits: HabitRepository,
    private val clock: AppClock,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val habitId: Long = savedStateHandle.get<String>(ARG_HABIT_ID)?.toLongOrNull() ?: 0L

    val today: LocalDate = clock.today()

    val detail: StateFlow<HabitDetail?> =
        habits.observeDetail(habitId, today)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Правка истории прямо из heatmap: тап по дню переключает
     * «выполнено → пропущено → без отметки». Будущие дни не трогаем.
     */
    fun cycleDay(date: LocalDate) {
        if (date > today) return
        viewModelScope.launch {
            val current = detail.value?.entries?.get(date)?.status
            when (current) {
                null -> habits.mark(habitId, date, EntryStatus.DONE)
                EntryStatus.DONE, EntryStatus.MINIMUM ->
                    habits.mark(habitId, date, EntryStatus.SKIPPED)
                else -> habits.clearMark(habitId, date)
            }
        }
    }

    companion object {
        const val ARG_HABIT_ID = "habitId"
    }
}
