package dev.ashwake.ui.tasks.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ashwake.core.model.Priority
import dev.ashwake.core.model.WeekdayMask
import dev.ashwake.core.time.AppClock
import dev.ashwake.data.assets.TaskPreset
import dev.ashwake.data.assets.TaskPresetLoader
import dev.ashwake.data.attachments.TaskAttachmentStore
import dev.ashwake.domain.model.tasks.Project
import dev.ashwake.domain.model.tasks.RecurrenceRule
import dev.ashwake.domain.model.tasks.RecurrenceType
import dev.ashwake.domain.model.tasks.AttachmentKind
import dev.ashwake.domain.model.tasks.Tag
import dev.ashwake.domain.model.tasks.Task
import dev.ashwake.domain.model.tasks.TaskAttachment
import dev.ashwake.domain.repository.tasks.ProjectRepository
import dev.ashwake.domain.repository.tasks.TagRepository
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import android.net.Uri
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
    val iconPath: String? = null,
    val note: String = "",
    val projectId: Long? = null,
    val priority: Priority = Priority.P4,
    val difficulty: Int = 1,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    /** Конец интервала хранится через длительность задачи: это переживает синхронизацию. */
    val endTime: LocalTime? = null,
    val estimateMinutes: Int? = null,
    val estimateHours: String = "",
    val estimateMinutesText: String = "",
    val tagsInput: String = "",
    val persistentReminderMinutes: Int? = null,
    val sourceLink: String? = null,
    val attachments: List<TaskAttachment> = emptyList(),
    val subtasks: List<EditableSubtask> = emptyList(),
    // повтор
    val recurrenceEnabled: Boolean = false,
    val isTemplate: Boolean = false,
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
    val saved: Boolean = false,
    val attachmentError: String? = null,
    val today: LocalDate = LocalDate.ofEpochDay(0)
) {
    val canSave: Boolean get() = title.isNotBlank()
}

@HiltViewModel
class TaskEditorViewModel @Inject constructor(
    private val tasks: TaskRepository,
    private val projects: ProjectRepository,
    private val tags: TagRepository,
    private val saveTask: SaveTaskUseCase,
    private val completeTask: CompleteTaskUseCase,
    private val reopenTask: ReopenTaskUseCase,
    private val deleteTask: DeleteTaskUseCase,
    private val presetLoader: TaskPresetLoader,
    private val attachmentStore: TaskAttachmentStore,
    private val clock: AppClock,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val taskId: Long = savedStateHandle.get<String>(ARG_TASK_ID)?.toLongOrNull() ?: 0L

    private val _state = MutableStateFlow(TaskEditorState(taskId = taskId, isNew = taskId == 0L))
    val state: StateFlow<TaskEditorState> = _state.asStateFlow()

    private val _presets = MutableStateFlow<List<TaskPreset>>(emptyList())
    val presets: StateFlow<List<TaskPreset>> = _presets.asStateFlow()

    val userTemplates: StateFlow<List<Task>> = tasks.observeTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val projectList: StateFlow<List<Project>> = projects.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val availableTags: StateFlow<List<Tag>> = tags.observeTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Подзадачи, удалённые в редакторе: применяются одной пачкой при сохранении. */
    private val removedSubtaskIds = mutableSetOf<Long>()
    private val saveMutex = Mutex()

    init {
        val today = clock.today()
        viewModelScope.launch { _presets.value = presetLoader.load() }
        if (taskId == 0L) {
            // Новая задача заводится на сегодня. Задача без даты не попадает
            // ни в один список дня, и человек, создавший её с главного экрана,
            // просто не находит её там, куда смотрел.
            _state.update { it.copy(loading = false, dueDate = today, today = today) }
        } else {
            _state.update { it.copy(today = today) }
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
        val today = clock.today()
        val est = task.estimateMinutes
        val hours = if (est != null && est >= 60) (est / 60).toString() else ""
        val mins = if (est != null && est % 60 > 0) (est % 60).toString() else if (est != null && est < 60) est.toString() else ""
        _state.value = TaskEditorState(
            taskId = task.id,
            title = task.title,
            emoji = task.emoji,
            iconPath = task.iconPath,
            note = task.note.orEmpty(),
            projectId = task.projectId,
            priority = task.priority,
            difficulty = task.difficulty,
            dueDate = task.dueDate,
            dueTime = task.dueTime,
            endTime = task.dueTime?.plusMinutes(task.estimateMinutes?.toLong() ?: 0L),
            estimateMinutes = task.estimateMinutes,
            estimateHours = hours,
            estimateMinutesText = mins,
            tagsInput = task.tags.joinToString(" ") { it.name },
            persistentReminderMinutes = task.persistentReminderMinutes,
            sourceLink = task.sourceLink,
            attachments = task.attachments,
            subtasks = task.subtasks.map { EditableSubtask(it.id, it.title, it.isDone) },
            recurrenceEnabled = rule != null,
            isTemplate = task.isTemplate,
            recurrenceType = rule?.type ?: RecurrenceType.DAILY,
            recurrenceInterval = rule?.intervalDays ?: 2,
            recurrenceWeekdays = rule?.weekdays ?: WeekdayMask.NONE,
            recurrenceDayOfMonth = rule?.dayOfMonth ?: 1,
            recurrenceFromCompletion = rule?.fromCompletion ?: false,
            existingRecurrenceId = rule?.id ?: 0,
            isNew = false,
            isDone = task.isDone,
            loading = false,
            today = today
        )
    }

    // --- правки полей ------------------------------------------------------

    fun setTitle(value: String) = _state.update { it.copy(title = value) }

    fun applyPreset(preset: TaskPreset) {
        _state.update { current ->
            val est = preset.estimateMinutes
            val hours = if (est != null && est >= 60) (est / 60).toString() else ""
            val mins = if (est != null && est % 60 > 0) (est % 60).toString() else if (est != null && est < 60) est.toString() else ""
            current.copy(
                title = preset.title,
                priority = preset.priority,
                difficulty = 1,
                estimateMinutes = preset.estimateMinutes,
                estimateHours = hours,
                estimateMinutesText = mins,
                subtasks = preset.subtasks.map { EditableSubtask(title = it) }
            )
        }
    }

    fun applyUserTemplate(template: Task) = _state.update { current ->
        current.copy(
            title = template.title,
            emoji = template.emoji,
            iconPath = template.iconPath,
            note = template.note.orEmpty(),
            projectId = template.projectId,
            priority = template.priority,
            difficulty = template.difficulty,
            dueTime = template.dueTime,
            endTime = template.dueTime?.plusMinutes(template.estimateMinutes?.toLong() ?: 0L),
            estimateMinutes = template.estimateMinutes,
            tagsInput = template.tags.joinToString(" ") { "#${it.name}" },
            recurrenceEnabled = template.recurrence != null,
            recurrenceType = template.recurrence?.type ?: current.recurrenceType,
            recurrenceInterval = template.recurrence?.intervalDays ?: current.recurrenceInterval,
            recurrenceWeekdays = template.recurrence?.weekdays ?: current.recurrenceWeekdays,
            recurrenceDayOfMonth = template.recurrence?.dayOfMonth ?: current.recurrenceDayOfMonth,
            isTemplate = false
        )
    }

    /** Повторный выбор той же эмодзи снимает её: отдельная кнопка «убрать» не нужна. */
    fun setEmoji(value: String?) = _state.update {
        it.copy(emoji = if (it.emoji == value) null else value)
    }

    fun setIconPath(value: String?) = _state.update { it.copy(iconPath = value) }

    fun addAttachment(uri: Uri) {
        val taskId = _state.value.taskId
        if (taskId == 0L) return
        viewModelScope.launch {
            val saved = attachmentStore.import(uri) ?: return@launch
            val attachment = TaskAttachment(
                taskId = taskId, fileName = saved.fileName, displayName = saved.displayName,
                mimeType = saved.mimeType, sizeBytes = saved.sizeBytes, createdAt = clock.now()
            )
            val id = tasks.addAttachment(attachment)
            _state.update { it.copy(attachments = it.attachments + attachment.copy(id = id)) }
        }
    }

    fun removeAttachment(attachment: TaskAttachment) = viewModelScope.launch {
        tasks.removeAttachment(attachment.id)
        attachmentStore.delete(attachment.fileName)
        _state.update { it.copy(attachments = it.attachments.filterNot { item -> item.id == attachment.id }) }
    }

    fun setNote(value: String) = _state.update { it.copy(note = value) }
    fun setProject(id: Long?) = _state.update { it.copy(projectId = id) }
    fun setPriority(value: Priority) = _state.update { it.copy(priority = value) }
    fun setDifficulty(value: Int) = _state.update { it.copy(difficulty = value.coerceIn(1, 5)) }
    fun setDate(value: LocalDate?) = _state.update { it.copy(dueDate = value) }
    fun setTagsInput(value: String) = _state.update { it.copy(tagsInput = value) }
    fun setSourceLink(value: String?) = _state.update { it.copy(sourceLink = value) }

    fun appendTag(name: String) {
        val clean = name.trim().removePrefix("#")
        if (clean.isBlank()) return
        _state.update { current ->
            val existing = current.tagsInput.split(Regex("[\\s,]+"))
                .map { it.trim().removePrefix("#") }
                .filter { it.isNotBlank() }
            if (existing.any { it.equals(clean, ignoreCase = true) }) current
            else {
                val newTags = if (current.tagsInput.isBlank()) "#$clean" else "${current.tagsInput.trim()} #$clean"
                current.copy(tagsInput = newTags)
            }
        }
    }

    /** Время без даты бессмысленно: молча подставляем сегодня. */
    fun setTime(value: LocalTime?) = _state.update {
        it.copy(dueTime = value, dueDate = it.dueDate ?: value?.let { _ -> clock.today() })
    }

    fun setEndTime(value: LocalTime?) = _state.update { current ->
        val start = current.dueTime
        val duration = if (start != null && value != null) {
            java.time.Duration.between(start, value).toMinutes().let { if (it <= 0) it + 24 * 60 else it }.toInt()
        } else current.estimateMinutes
        current.copy(endTime = value, estimateMinutes = duration).let { updated ->
            val hours = duration?.div(60)?.takeIf { it > 0 }?.toString().orEmpty()
            val minutes = duration?.rem(60)?.takeIf { it > 0 }?.toString().orEmpty()
            updated.copy(estimateHours = hours, estimateMinutesText = minutes)
        }
    }

    fun setIsTemplate(value: Boolean) = _state.update { it.copy(isTemplate = value) }

    fun setEstimate(minutes: Int?) = _state.update {
        val h = if (minutes != null && minutes >= 60) (minutes / 60).toString() else ""
        val m = if (minutes != null && minutes % 60 > 0) (minutes % 60).toString() else if (minutes != null && minutes < 60) minutes.toString() else ""
        it.copy(estimateMinutes = minutes, estimateHours = h, estimateMinutesText = m)
    }

    fun setEstimateHours(hoursText: String) {
        val filtered = hoursText.filter { it.isDigit() }.take(2)
        _state.update { current ->
            val h = filtered.toIntOrNull() ?: 0
            val m = current.estimateMinutesText.toIntOrNull() ?: 0
            val total = if (h == 0 && m == 0 && filtered.isEmpty() && current.estimateMinutesText.isEmpty()) null else h * 60 + m
            current.copy(
                estimateHours = filtered,
                estimateMinutes = total
            )
        }
    }

    fun setEstimateMinutesText(minutesText: String) {
        val filtered = minutesText.filter { it.isDigit() }.take(2)
        _state.update { current ->
            val h = current.estimateHours.toIntOrNull() ?: 0
            val m = filtered.toIntOrNull() ?: 0
            val total = if (h == 0 && m == 0 && current.estimateHours.isEmpty() && filtered.isEmpty()) null else h * 60 + m
            current.copy(
                estimateMinutesText = filtered,
                estimateMinutes = total
            )
        }
    }

    fun clearEstimate() {
        _state.update { it.copy(estimateHours = "", estimateMinutesText = "", estimateMinutes = null) }
    }

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

    fun editSubtask(index: Int, newTitle: String) {
        if (newTitle.isBlank()) return
        _state.update { current ->
            val updated = current.subtasks.mapIndexed { i, subtask ->
                if (i == index) subtask.copy(title = newTitle.trim()) else subtask
            }
            current.copy(subtasks = updated)
        }
    }

    fun duplicateSubtask(index: Int) {
        _state.update { current ->
            val subtask = current.subtasks.getOrNull(index) ?: return@update current
            val copy = EditableSubtask(id = 0, title = subtask.title, done = false)
            val list = current.subtasks.toMutableList()
            list.add(index + 1, copy)
            current.copy(subtasks = list)
        }
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
            saveMutex.withLock {
                val savedId = saveMainTask(current)
                _state.update { it.copy(saved = true, taskId = savedId, isNew = false) }
            }
        }
    }

    /**
     * Редактор не должен терять уже введённую задачу при «назад». Если есть
     * название, сохраняем всё текущее состояние перед уходом с экрана;
     * пустую форму намеренно не создаём в списке задач.
     */
    fun saveBeforeExit(onFinished: () -> Unit) {
        val current = _state.value
        if (current.title.isBlank()) {
            onFinished()
            return
        }
        viewModelScope.launch {
            runCatching {
                saveMutex.withLock { saveMainTask(_state.value) }
            }
            onFinished()
        }
    }

    private suspend fun saveMainTask(current: TaskEditorState): Long {
        val existing = if (current.taskId != 0L) tasks.getTask(current.taskId) else null
        val task = (existing ?: Task(title = "")).copy(
                id = current.taskId,
                title = current.title.trim(),
                emoji = current.emoji,
                iconPath = current.iconPath,
                note = current.note.takeIf { it.isNotBlank() },
                projectId = current.projectId,
                priority = current.priority,
                difficulty = current.difficulty,
                dueDate = current.dueDate,
                dueTime = current.dueTime,
                estimateMinutes = current.estimateMinutes,
                persistentReminderMinutes = current.persistentReminderMinutes,
                sourceLink = current.sourceLink,
                tags = parseTags(current.tagsInput),
                recurrence = buildRecurrence(current),
                isTemplate = current.isTemplate
        )
        val savedId = saveTask(task)
        applySubtasks(savedId, current.subtasks)
        _state.update { it.copy(taskId = savedId, isNew = false, attachmentError = null) }
        return savedId
    }

    fun addAttachments(sourceUris: List<String>, kind: AttachmentKind) {
        if (sourceUris.isEmpty()) return
        viewModelScope.launch {
            val error = runCatching {
                saveMutex.withLock {
                    val current = _state.value
                    require(current.canSave) { "Сначала укажите название задачи" }
                    val id = saveMainTask(current)
                    sourceUris.forEach { source ->
                        val saved = attachmentStore.import(Uri.parse(source)) ?: return@forEach
                        val attachment = TaskAttachment(
                            taskId = id,
                            fileName = saved.fileName,
                            displayName = saved.displayName,
                            mimeType = saved.mimeType,
                            sizeBytes = saved.sizeBytes,
                            createdAt = clock.now()
                        )
                        val attachmentId = tasks.addAttachment(attachment)
                        _state.update { state ->
                            state.copy(attachments = state.attachments + attachment.copy(id = attachmentId))
                        }
                    }
                }
            }.exceptionOrNull()
            error?.let { _state.update { state ->
                state.copy(attachmentError = it.message ?: "Не удалось прикрепить файл")
            } }
        }
    }

    fun reportAttachmentError(message: String) {
        _state.update { it.copy(attachmentError = message) }
    }

    private suspend fun applySubtasks(parentId: Long, subtasks: List<EditableSubtask>) {
        removedSubtaskIds.forEach { deleteTask(it) }
        removedSubtaskIds.clear()

        val newTitles = subtasks.filter { it.id == 0L }.map { it.title }
        if (newTitles.isNotEmpty()) tasks.addSubtasks(parentId, newTitles)

        // Существующие подзадачи: применяем изменившееся название и статус
        val stored = tasks.getTask(parentId)?.subtasks.orEmpty().associateBy { it.id }
        subtasks.filter { it.id != 0L }.forEach { edited ->
            val before = stored[edited.id] ?: return@forEach
            if (before.title != edited.title) {
                tasks.upsert(before.copy(title = edited.title))
            }
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
