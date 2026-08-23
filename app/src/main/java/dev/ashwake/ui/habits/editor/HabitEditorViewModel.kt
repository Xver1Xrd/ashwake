package dev.ashwake.ui.habits.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ashwake.core.model.Sphere
import dev.ashwake.core.model.WeekdayMask
import dev.ashwake.core.time.AppClock
import dev.ashwake.domain.model.habits.AnchorType
import dev.ashwake.domain.model.habits.Habit
import dev.ashwake.domain.model.habits.HabitAnchor
import dev.ashwake.domain.model.habits.HabitSchedule
import dev.ashwake.domain.model.habits.HabitScheduleType
import dev.ashwake.domain.model.habits.HabitType
import dev.ashwake.domain.repository.habits.HabitRepository
import dev.ashwake.domain.scheduler.HabitReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject

data class HabitEditorState(
    val habitId: Long = 0,
    val name: String = "",
    val icon: String? = null,
    val iconPath: String? = null,
    val type: HabitType = HabitType.CHECK,
    val sphere: Sphere = Sphere.HEALTH,
    val targetValue: Float = 1f,
    val unitName: String = "",
    val minimumEnabled: Boolean = false,
    val minimumValue: Float = 1f,
    val scheduleType: HabitScheduleType = HabitScheduleType.DAILY,
    val timesPerWeek: Int = 3,
    val weekdays: WeekdayMask = WeekdayMask.EVERY_DAY,
    val reminderTime: LocalTime? = null,
    val freezeQuota: Int = 3,
    val anchorType: AnchorType? = null,
    val anchorHabitId: Long? = null,
    val isNew: Boolean = true,
    val loading: Boolean = true,
    val saved: Boolean = false
) {
    val canSave: Boolean get() = name.isNotBlank()
}

@HiltViewModel
class HabitEditorViewModel @Inject constructor(
    private val habits: HabitRepository,
    private val scheduler: HabitReminderScheduler,
    private val clock: AppClock,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val habitId: Long = savedStateHandle.get<String>(ARG_HABIT_ID)?.toLongOrNull() ?: 0L

    private val _state = MutableStateFlow(HabitEditorState(habitId = habitId, isNew = habitId == 0L))
    val state: StateFlow<HabitEditorState> = _state.asStateFlow()

    /** Список для выбора якоря «после привычки X» — себя в нём быть не должно. */
    val anchorCandidates: StateFlow<List<Habit>> = habits.observeHabits()
        .map { list -> list.filter { it.id != habitId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (habitId == 0L) {
            _state.update { it.copy(loading = false) }
        } else {
            viewModelScope.launch { load() }
        }
    }

    private suspend fun load() {
        val habit = habits.getHabit(habitId) ?: run {
            _state.update { it.copy(loading = false) }
            return
        }
        val anchor = habit.anchors.firstOrNull()
        _state.value = HabitEditorState(
            habitId = habit.id,
            name = habit.name,
            icon = habit.icon,
            iconPath = habit.iconPath,
            type = habit.type,
            sphere = habit.sphere,
            targetValue = habit.targetValue,
            unitName = habit.unitName.orEmpty(),
            minimumEnabled = habit.hasMinimum,
            minimumValue = habit.minimumValue ?: 1f,
            scheduleType = habit.schedule.type,
            timesPerWeek = habit.schedule.timesPerWeek,
            weekdays = habit.schedule.weekdays,
            reminderTime = habit.reminderTime,
            freezeQuota = habit.freezeQuotaPerMonth,
            anchorType = anchor?.type,
            anchorHabitId = anchor?.refHabitId,
            isNew = false,
            loading = false
        )
    }

    fun setName(value: String) = _state.update { it.copy(name = value) }

    /** Повторный выбор той же эмодзи снимает её. */
    fun setIcon(value: String?) = _state.update {
        it.copy(icon = if (it.icon == value) null else value)
    }

    fun setIconPath(value: String?) = _state.update { it.copy(iconPath = value) }
    fun setType(value: HabitType) = _state.update { it.copy(type = value) }
    fun setSphere(value: Sphere) = _state.update { it.copy(sphere = value) }
    fun setTarget(value: Float) = _state.update { it.copy(targetValue = value.coerceAtLeast(1f)) }
    fun setUnit(value: String) = _state.update { it.copy(unitName = value) }
    fun setMinimumEnabled(value: Boolean) = _state.update { it.copy(minimumEnabled = value) }
    fun setMinimum(value: Float) = _state.update { it.copy(minimumValue = value.coerceAtLeast(0f)) }
    fun setScheduleType(value: HabitScheduleType) = _state.update { it.copy(scheduleType = value) }
    fun setTimesPerWeek(value: Int) = _state.update { it.copy(timesPerWeek = value.coerceIn(1, 7)) }
    fun toggleWeekday(day: DayOfWeek) = _state.update { it.copy(weekdays = it.weekdays.toggle(day)) }
    fun setReminder(value: LocalTime?) = _state.update { it.copy(reminderTime = value) }
    fun setFreezeQuota(value: Int) = _state.update { it.copy(freezeQuota = value.coerceIn(0, 15)) }
    fun setAnchorType(value: AnchorType?) = _state.update { it.copy(anchorType = value) }
    fun setAnchorHabit(id: Long?) = _state.update { it.copy(anchorHabitId = id) }

    fun save() {
        val current = _state.value
        if (!current.canSave) return

        viewModelScope.launch {
            val existing = if (current.habitId != 0L) habits.getHabit(current.habitId) else null
            val today = clock.today()

            val habit = (existing ?: Habit(name = "")).copy(
                id = current.habitId,
                name = current.name.trim(),
                icon = current.icon,
                iconPath = current.iconPath,
                type = current.type,
                sphere = current.sphere,
                targetValue = if (current.type == HabitType.COUNTER) current.targetValue else 1f,
                unitName = current.unitName.takeIf {
                    it.isNotBlank() && current.type == HabitType.COUNTER
                },
                minimumValue = current.minimumValue.takeIf { current.minimumEnabled },
                schedule = HabitSchedule(
                    type = current.scheduleType,
                    timesPerWeek = current.timesPerWeek,
                    weekdays = current.weekdays,
                    // Якорь ставим явно: без него «через день» считался бы от даты
                    // создания, и правка привычки сдвигала бы всю сетку
                    intervalAnchor = existing?.schedule?.intervalAnchor ?: today,
                    biweeklyAnchor = existing?.schedule?.biweeklyAnchor ?: today
                ),
                reminderTime = current.reminderTime,
                freezeQuotaPerMonth = current.freezeQuota,
                anchors = buildAnchors(current)
            )

            val id = habits.upsertHabit(habit)
            habits.getHabit(id)?.let(scheduler::schedule)
            _state.update { it.copy(saved = true, habitId = id, isNew = false) }
        }
    }

    private fun buildAnchors(state: HabitEditorState): List<HabitAnchor> {
        val type = state.anchorType ?: return emptyList()
        if (type == AnchorType.HABIT_DONE && state.anchorHabitId == null) return emptyList()
        return listOf(
            HabitAnchor(
                habitId = state.habitId,
                type = type,
                refHabitId = state.anchorHabitId
            )
        )
    }

    fun archive() {
        val id = _state.value.habitId
        if (id == 0L) return
        viewModelScope.launch {
            scheduler.cancel(id)
            habits.archiveHabit(id)
            _state.update { it.copy(saved = true) }
        }
    }

    fun delete() {
        val id = _state.value.habitId
        if (id == 0L) return
        viewModelScope.launch {
            scheduler.cancel(id)
            habits.deleteHabit(id)
            _state.update { it.copy(saved = true) }
        }
    }

    companion object {
        const val ARG_HABIT_ID = "habitId"
    }
}
