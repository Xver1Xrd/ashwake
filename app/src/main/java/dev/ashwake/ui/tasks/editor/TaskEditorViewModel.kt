package dev.ashwake.ui.tasks.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ashwake.core.model.Priority
import dev.ashwake.core.model.WeekdayMask
import dev.ashwake.core.time.AppClock
import dev.ashwake.domain.model.tasks.Project
import dev.ashwake.domain.model.tasks.RecurrenceRule
import dev.ashwake.domain.model.tasks.RecurrenceType
import dev.ashwake.domain.model.tasks.Tag
import dev.ashwake.domain.model.tasks.Task
import dev.ashwake.domain.repository.tasks.ProjectRepository
import dev.ashwake.domain.repository.tasks.TaskRepository
import dev.ashwake.domain.usecase.tasks.CompleteTaskUseCase
import dev.ashwake.domain.usecase.tasks.DeleteTaskUseCase
import dev.ashwake.domain.usecase.tasks.ReopenTaskUseCase
import dev.ashwake.domain.usecase.tasks.SaveTaskUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/** Подзадача в редакторе. `id == 0` — ещё не сохранённая. */
data class EditableSubtask(
    val id: Long = 0,
    val title: String,
    val done: Boolean = false
)

data class TaskEditorState(
    val taskId: Long = 0,
    val title: String = "",
    val emoji: String? = null,
    val note: String = "",
    val projectId: Long? = null,
    val priority: Priority = Priority.P4,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val estimateMinutes: Int? = null,
    val tagsInput: String = "",
    val persistentReminderMinutes: Int? = null,
    val sourceLink: String? = null,
    val subtasks: List<EditableSubtask> = emptyList(),
    // повтор
    val recurrenceEnabled: Boolean = false,
    val recurrenceType: RecurrenceType = RecurrenceType.DAILY,
    val recurrenceInterval: Int = 2,
    val recurrenceWeekdays: WeekdayMask = WeekdayMask.NONE,
    val recurrenceDayOfMonth: Int = 1,
    val recurrenceFromCompletion: Boolean = false,
    val existingRecurrenceId: Long = 0,
    // служебное
    val isNew: Boolean = true,
    val isDone: Boolean = false,
    val loading: Boolean = true,
    val saved: Boolean = false
) {
    val canSave: Boolean get() = title.isNotBlank()
}

@HiltViewModel
class TaskEditorViewModel @Inject constructor(
    private val tasks: TaskRepository,
    private val projects: ProjectRepository,
    private val saveTask: SaveTaskUseCase,
    private val completeTask: CompleteTaskUseCase,
    private val reopenTask: ReopenTaskUseCase,
    private val deleteTask: DeleteTaskUseCase,
    private val clock: AppClock,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val taskId: Long = savedStateHandle.get<String>(ARG_TASK_ID)?.toLongOrNull() ?: 0L

    private val _state = MutableStateFlow(TaskEditorState(taskId = taskId, isNew = taskId == 0L))
    val state: StateFlow<TaskEditorState> = _state.asStateFlow()

    val projectList: StateFlow<List<Project>> = projects.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Подзадачи, удалённые в редакторе: применяются одной пачкой при сохранении. */
    private val removedSubtaskIds = mutableSetOf<Long>()

    init {
        if (taskId == 0L) {
            // Новая задача заводится на сегодня. Задача без даты не попадает
            // ни в один список дня, и человек, создавший её с главного экрана,
            // просто не находит её там, куда смотрел.
            _state.update { it.copy(loading = false, dueDate = clock.today()) }
        } else {
            viewModelScope.launch { load(taskId) }
        }
    }

    private suspend fun load(id: Long) {
        val task = tasks.getTask(id)
        if (task == null) {
            _state.update { it.copy(loading = false) }
            return
        }
        val rule = task.recurrence
        _state.value = TaskEditorState(
            taskId = task.id,
            title = task.title,
            emoji = task.emoji,
            note = task.note.orEmpty(),
            projectId = task.projectId,
            priority = task.priority,
            dueDate = task.dueDate,
            dueTime = task.dueTime,
            estimateMinutes = task.estimateMinutes,
            tagsInput = task.tags.joinToString(" ") { "#${it.name}" },
            persistentReminderMinutes = task.persistentReminderMinutes,
            sourceLink = task.sourceLink,
            subtasks = task.subtasks.map { EditableSubtask(it.id, it.title, it.isDone) },
            recurrenceEnabled = rule != null,
            recurrenceType = rule?.type ?: RecurrenceType.DAILY,
            recurrenceInterval = rule?.intervalDays ?: 2,
            recurrenceWeekdays = rule?.weekdays ?: WeekdayMask.NONE,
            recurrenceDayOfMonth = rule?.dayOfMonth ?: 1,
            recurrenceFromCompletion = rule?.fromCompletion ?: false,
            existingRecurrenceId = rule?.id ?: 0,
            isNew = false,
            isDone = task.isDone,
            loading = false
        )
    }

    // --- правки полей ------------------------------------------------------

    fun setTitle(value: String) = _state.update { it.copy(title = value) }

    /** Повторный выбор той же эмодзи снимает её: отдельная кнопка «убрать» не нужна. */
    fun setEmoji(value: String?) = _state.update {
        it.copy(emoji = if (it.emoji == value) null else value)
    }

    fun setNote(value: String) = _state.update { it.copy(note = value) }
    fun setProject(id: Long?) = _state.update { it.copy(projectId = id) }
    fun setPriority(value: Priority) = _state.update { it.copy(priority = value) }
    fun setDate(value: LocalDate?) = _state.update { it.copy(dueDate = value) }
    fun setTagsInput(value: String) = _state.update { it.copy(tagsInput = value) }
    fun setSourceLink(value: String?) = _state.update { it.copy(sourceLink = value) }

    /** Время без даты бессмысленно: молча подставляем сегодня. */
    fun setTime(value: LocalTime?) = _state.update {
        it.copy(dueTime = value, dueDate = it.dueDate ?: value?.let { _ -> clock.today() })
    }

    fun setEstimate(minutes: Int?) = _state.update { it.copy(estimateMinutes = minutes) }

    fun setPersistentReminder(minutes: Int?) =
        _state.update { it.copy(persistentReminderMinutes = minutes) }

    // --- повтор ------------------------------------------------------------

    fun setRecurrenceEnabled(enabled: Boolean) =
        _state.update { it.copy(recurrenceEnabled = enabled) }

    fun setRecurrenceType(type: RecurrenceType) =
        _state.update { it.copy(recurrenceType = type) }

    fun setRecurrenceInterval(days: Int) =
        _state.update { it.copy(recurrenceInterval = days.coerceIn(1, 365)) }

    fun toggleRecurrenceWeekday(day: DayOfWeek) =
        _state.update { it.copy(recurrenceWeekdays = it.recurrenceWeekdays.toggle(day)) }

    fun setRecurrenceDayOfMonth(day: Int) =
        _state.update { it.copy(recurrenceDayOfMonth = day.coerceIn(1, 31)) }

    fun setRecurrenceFromCompletion(value: Boolean) =
        _state.update { it.copy(recurrenceFromCompletion = value) }

    // --- подзадачи ---------------------------------------------------------

    fun addSubtask(title: String) {
        if (title.isBlank()) return
        _state.update { it.copy(subtasks = it.subtasks + EditableSubtask(title = title.trim())) }
    }

    fun removeSubtask(index: Int) {
        _state.update { current ->
            val target = current.subtasks.getOrNull(index) ?: return@update current
            if (target.id != 0L) removedSubtaskIds += target.id
            current.copy(subtasks = current.subtasks.filterIndexed { i, _ -> i != index })
        }
    }

    fun toggleSubtask(index: Int) {
        _state.update { current ->
            val updated = current.subtasks.mapIndexed { i, subtask ->
                if (i == index) subtask.copy(done = !subtask.done) else subtask
            }
            current.copy(subtasks = updated)
        }
    }

    // --- сохранение --------------------------------------------------------

    fun save() {
        val current = _state.value
        if (!current.canSave) return

        viewModelScope.launch {
            val existing = if (current.taskId != 0L) tasks.getTask(current.taskId) else null

            val task = (existing ?: Task(title = "")).copy(
                id = current.taskId,
                title = current.title.trim(),
                emoji = current.emoji,
                note = current.note.takeIf { it.isNotBlank() },
                projectId = current.projectId,
                priority = current.priority,
                dueDate = current.dueDate,
                dueTime = current.dueTime,
                estimateMinutes = current.estimateMinutes,
                persistentReminderMinutes = current.persistentReminderMinutes,
                sourceLink = current.sourceLink,
                tags = parseTags(current.tagsInput),
                recurrence = buildRecurrence(current)
            )

            val savedId = saveTask(task)
            applySubtasks(savedId, current.subtasks)
            _state.update { it.copy(saved = true, taskId = savedId, isNew = false) }
        }
    }

    private suspend fun applySubtasks(parentId: Long, subtasks: List<EditableSubtask>) {
        removedSubtaskIds.forEach { deleteTask(it) }
        removedSubtaskIds.clear()

        val newTitles = subtasks.filter { it.id == 0L }.map { it.title }
        if (newTitles.isNotEmpty()) tasks.addSubtasks(parentId, newTitles)

        // Существующие подзадачи: применяем только изменившийся статус
        val stored = tasks.getTask(parentId)?.subtasks.orEmpty().associateBy { it.id }
        subtasks.filter { it.id != 0L }.forEach { edited ->
            val before = stored[edited.id] ?: return@forEach
            if (before.isDone != edited.done) {
                if (edited.done) completeTask(edited.id) else reopenTask(edited.id)
            }
        }
    }

    private fun parseTags(input: String): List<Tag> =
        input.split(Regex("[\\s,]+"))
            .map { it.trim().removePrefix("#") }
            .filter { it.isNotEmpty() }
            .distinct()
            .map { Tag(name = it, color = 0) }

    private fun buildRecurrence(state: TaskEditorState): RecurrenceRule? {
        if (!state.recurrenceEnabled) return null
        return RecurrenceRule(
            id = state.existingRecurrenceId,
            type = state.recurrenceType,
            intervalDays = state.recurrenceInterval,
            weekdays = state.recurrenceWeekdays,
            dayOfMonth = state.recurrenceDayOfMonth,
            startDate = state.dueDate ?: clock.today(),
            fromCompletion = state.recurrenceFromCompletion
        )
    }

    fun toggleDone() {
        val current = _state.value
        if (current.taskId == 0L) return
        viewModelScope.launch {
            if (current.isDone) reopenTask(current.taskId) else completeTask(current.taskId)
            _state.update { it.copy(saved = true) }
        }
    }

    fun delete() {
        val id = _state.value.taskId
        if (id == 0L) return
        viewModelScope.launch {
            deleteTask(id)
            _state.update { it.copy(saved = true) }
        }
    }

    companion object {
        const val ARG_TASK_ID = "taskId"
    }
}
