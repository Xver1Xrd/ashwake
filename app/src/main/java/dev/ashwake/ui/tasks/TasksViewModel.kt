package dev.ashwake.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ashwake.core.time.AppClock
import dev.ashwake.domain.engine.nlp.ParsedQuickInput
import dev.ashwake.domain.engine.nlp.QuickInputParser
import dev.ashwake.domain.engine.tasks.EisenhowerClassifier
import dev.ashwake.domain.model.tasks.EisenhowerQuadrant
import dev.ashwake.domain.model.tasks.PostponeSource
import dev.ashwake.domain.model.tasks.Project
import dev.ashwake.domain.model.tasks.StaleResolution
import dev.ashwake.domain.model.tasks.Tag
import dev.ashwake.domain.model.tasks.Task
import dev.ashwake.domain.repository.tasks.ProjectRepository
import dev.ashwake.domain.repository.tasks.TagRepository
import dev.ashwake.domain.repository.tasks.TaskFilter
import dev.ashwake.domain.repository.tasks.TaskRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class TasksViewMode { LIST, MATRIX }

data class TasksUiState(
    val tasks: List<Task> = emptyList(),
    val projects: List<Project> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val filter: TaskFilter = TaskFilter(),
    val viewMode: TasksViewMode = TasksViewMode.LIST,
    val quickInput: String = "",
    val parsed: ParsedQuickInput? = null,
    val today: LocalDate = LocalDate.EPOCH,
    /** Задача, по которой открыт диалог «залежалась» (5-й перенос). */
    val staleDialogTask: Task? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TasksViewModel @Inject constructor(
    private val tasks: TaskRepository,
    private val projects: ProjectRepository,
    private val tags: TagRepository,
    private val parser: QuickInputParser,
    private val classifier: EisenhowerClassifier,
    private val clock: AppClock
) : ViewModel() {

    private data class UiBits(
        val filter: TaskFilter,
        val mode: TasksViewMode,
        val input: String,
        val staleTaskId: Long?
    )

    private val filter = MutableStateFlow(TaskFilter())
    private val viewMode = MutableStateFlow(TasksViewMode.LIST)
    private val quickInput = MutableStateFlow("")
    private val staleTaskId = MutableStateFlow<Long?>(null)

    private val uiBits = combine(filter, viewMode, quickInput, staleTaskId, ::UiBits)

    val state: StateFlow<TasksUiState> = combine(
        filter.flatMapLatest { tasks.observeTasks(it) },
        projects.observeProjects(),
        tags.observeTags(),
        uiBits
    ) { taskList, projectList, tagList, bits ->
        val today = clock.today()
        TasksUiState(
            tasks = taskList,
            projects = projectList,
            tags = tagList,
            filter = bits.filter,
            viewMode = bits.mode,
            quickInput = bits.input,
            // Разбор идёт на каждое нажатие клавиши: парсер чистый и дешёвый,
            // зато пользователь сразу видит, что распозналось.
            parsed = bits.input.takeIf { it.isNotBlank() }?.let { parser.parse(it, today) },
            today = today,
            staleDialogTask = taskList.firstOrNull { it.id == bits.staleTaskId }
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        TasksUiState(today = clock.today())
    )

    // --- быстрый ввод ------------------------------------------------------

    fun onQuickInputChange(text: String) { quickInput.value = text }

    fun submitQuickInput() {
        val raw = quickInput.value
        if (raw.isBlank()) return
        val parsed = parser.parse(raw, clock.today())
        if (parsed.title.isBlank()) return

        viewModelScope.launch {
            tasks.upsert(
                Task(
                    title = parsed.title,
                    priority = parsed.priority ?: dev.ashwake.core.model.Priority.P4,
                    dueDate = parsed.date,
                    dueTime = parsed.time,
                    estimateMinutes = parsed.estimateMinutes,
                    projectId = filter.value.projectId,
                    tags = parsed.tagNames.map { Tag(name = it, color = 0) }
                )
            )
            quickInput.value = ""
        }
    }

    /** Создание задачи из «поделиться» (п. 11): ссылка кладётся в отдельное поле. */
    fun createFromShared(text: String, link: String?) {
        viewModelScope.launch {
            val parsed = parser.parse(text, clock.today())
            tasks.upsert(
                Task(
                    title = parsed.title.ifBlank { text.take(120) },
                    dueDate = parsed.date,
                    dueTime = parsed.time,
                    estimateMinutes = parsed.estimateMinutes,
                    sourceLink = link,
                    tags = parsed.tagNames.map { Tag(name = it, color = 0) }
                )
            )
        }
    }

    // --- действия над задачами ---------------------------------------------

    fun complete(task: Task) {
        viewModelScope.launch { tasks.complete(task.id) }
    }

    fun reopen(task: Task) {
        viewModelScope.launch { tasks.reopen(task.id) }
    }

    /** Свайп влево — «на завтра». На 5-м переносе поднимаем диалог разбора завала. */
    fun postponeToTomorrow(task: Task) {
        viewModelScope.launch {
            tasks.postpone(task.id, clock.today().plusDays(1), PostponeSource.SWIPE)
            if (task.postponeCount + 1 >= STALE_DIALOG_THRESHOLD) {
                staleTaskId.value = task.id
            }
        }
    }

    fun delete(task: Task) {
        viewModelScope.launch { tasks.delete(task.id) }
    }

    fun moveToQuadrant(task: Task, quadrant: EisenhowerQuadrant) {
        if (classifier.quadrantOf(task, clock.today()) == quadrant) return
        viewModelScope.launch {
            // Приоритет подтягивается к квадранту: иначе задача останется чужого цвета.
            tasks.setQuadrant(task.id, quadrant, classifier.priorityFor(quadrant))
        }
    }

    // --- диалог залежавшейся задачи ----------------------------------------

    fun dismissStaleDialog() { staleTaskId.value = null }

    fun resolveStale(task: Task, resolution: StaleResolution, payload: String? = null) {
        viewModelScope.launch {
            when (resolution) {
                StaleResolution.DELETE -> tasks.delete(task.id)
                StaleResolution.DELEGATE -> tasks.setDelegate(task.id, payload)
                StaleResolution.SPLIT -> tasks.addSubtasks(
                    task.id,
                    payload.orEmpty().split('\n').map { it.trim() }.filter { it.isNotEmpty() }
                )
                StaleResolution.SCHEDULE_SLOT -> tasks.upsert(
                    task.copy(dueDate = clock.today(), dueTime = task.dueTime)
                )
                StaleResolution.KEEP -> Unit
            }
            staleTaskId.value = null
        }
    }

    // --- фильтры и режим ---------------------------------------------------

    fun setViewMode(mode: TasksViewMode) { viewMode.value = mode }

    fun toggleStaleFilter() {
        filter.update { it.copy(onlyStale = !it.onlyStale) }
    }

    fun setProjectFilter(projectId: Long?) {
        filter.update { it.copy(projectId = projectId) }
    }

    fun setTagFilter(tagId: Long?) {
        filter.update { it.copy(tagId = tagId) }
    }

    fun setShowDone(show: Boolean) {
        filter.update { it.copy(includeDone = show) }
    }

    fun quadrantOf(task: Task, today: LocalDate): EisenhowerQuadrant =
        classifier.quadrantOf(task, today)

    private companion object {
        const val STALE_DIALOG_THRESHOLD = 5
    }
}
