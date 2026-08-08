package dev.ashwake.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ashwake.core.model.Priority
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
import dev.ashwake.domain.usecase.tasks.CompleteTaskUseCase
import dev.ashwake.domain.usecase.tasks.DeleteTaskUseCase
import dev.ashwake.domain.usecase.tasks.PostponeTaskUseCase
import dev.ashwake.domain.usecase.tasks.ReopenTaskUseCase
import dev.ashwake.domain.usecase.tasks.SaveTaskUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

enum class TasksViewMode { LIST, MATRIX, CALENDAR, TIMEBOX }

/** Масштаб календаря: месяц / неделя / день (п. 17, экран «Задачи»). */
enum class CalendarScale { MONTH, WEEK, DAY }

data class TasksUiState(
    val tasks: List<Task> = emptyList(),
    val projects: List<Project> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val filter: TaskFilter = TaskFilter(),
    val viewMode: TasksViewMode = TasksViewMode.LIST,
    val quickInput: String = "",
    val parsed: ParsedQuickInput? = null,
    val today: LocalDate = LocalDate.EPOCH,
    val staleDialogTask: Task? = null,
    val expandedTaskIds: Set<Long> = emptySet()
)

data class CalendarUiState(
    val scale: CalendarScale = CalendarScale.MONTH,
    val anchor: LocalDate = LocalDate.EPOCH,
    val selected: LocalDate = LocalDate.EPOCH,
    val tasksByDate: Map<LocalDate, List<Task>> = emptyMap()
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TasksViewModel @Inject constructor(
    private val tasks: TaskRepository,
    private val projects: ProjectRepository,
    private val tags: TagRepository,
    private val parser: QuickInputParser,
    private val classifier: EisenhowerClassifier,
    private val clock: AppClock,
    private val saveTask: SaveTaskUseCase,
    private val completeTask: CompleteTaskUseCase,
    private val reopenTask: ReopenTaskUseCase,
    private val postponeTask: PostponeTaskUseCase,
    private val deleteTask: DeleteTaskUseCase
) : ViewModel() {

    private data class UiBits(
        val filter: TaskFilter,
        val mode: TasksViewMode,
        val input: String,
        val staleTaskId: Long?,
        val expanded: Set<Long>
    )

    private val filter = MutableStateFlow(TaskFilter())
    private val viewMode = MutableStateFlow(TasksViewMode.LIST)
    private val quickInput = MutableStateFlow("")
    private val staleTaskId = MutableStateFlow<Long?>(null)
    private val expandedTaskIds = MutableStateFlow<Set<Long>>(emptySet())

    private val uiBits = combine(
        filter, viewMode, quickInput, staleTaskId, expandedTaskIds, ::UiBits
    )

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
            staleDialogTask = taskList.firstOrNull { it.id == bits.staleTaskId },
            expandedTaskIds = bits.expanded
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        TasksUiState(today = clock.today())
    )

    // --- календарь ---------------------------------------------------------

    private val calendarScale = MutableStateFlow(CalendarScale.MONTH)
    private val calendarAnchor = MutableStateFlow(clock.today())
    private val selectedDate = MutableStateFlow(clock.today())

    val calendarState: StateFlow<CalendarUiState> =
        combine(calendarScale, calendarAnchor, selectedDate) { scale, anchor, selected ->
            Triple(scale, anchor, selected)
        }.flatMapLatest { (scale, anchor, selected) ->
            val (from, to) = visibleRange(anchor, scale)
            tasks.observeTasksInRange(from, to).map { list ->
                CalendarUiState(
                    scale = scale,
                    anchor = anchor,
                    selected = selected,
                    // dueDate здесь не может быть null: запрос его и фильтрует
                    tasksByDate = list.groupBy { it.dueDate!! }
                )
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            CalendarUiState(anchor = clock.today(), selected = clock.today())
        )

    /** Видимый интервал с запасом: месяц отрисовывается сеткой с хвостами соседних месяцев. */
    private fun visibleRange(anchor: LocalDate, scale: CalendarScale): Pair<LocalDate, LocalDate> =
        when (scale) {
            CalendarScale.MONTH -> {
                val first = anchor.withDayOfMonth(1)
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                first to first.plusDays(41)
            }
            CalendarScale.WEEK -> {
                val first = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                first to first.plusDays(6)
            }
            CalendarScale.DAY -> anchor to anchor
        }

    fun setCalendarScale(scale: CalendarScale) { calendarScale.value = scale }

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
        calendarAnchor.value = date
    }

    fun shiftCalendar(forward: Boolean) {
        val step = if (forward) 1L else -1L
        calendarAnchor.update { anchor ->
            when (calendarScale.value) {
                CalendarScale.MONTH -> anchor.plusMonths(step)
                CalendarScale.WEEK -> anchor.plusWeeks(step)
                CalendarScale.DAY -> anchor.plusDays(step)
            }
        }
        if (calendarScale.value == CalendarScale.DAY) selectedDate.value = calendarAnchor.value
    }

    fun goToToday() {
        calendarAnchor.value = clock.today()
        selectedDate.value = clock.today()
    }

    // --- быстрый ввод ------------------------------------------------------

    fun onQuickInputChange(text: String) { quickInput.value = text }

    fun submitQuickInput() {
        val raw = quickInput.value
        if (raw.isBlank()) return
        val parsed = parser.parse(raw, clock.today())
        if (parsed.title.isBlank()) return

        viewModelScope.launch {
            saveTask(
                Task(
                    title = parsed.title,
                    priority = parsed.priority ?: Priority.P4,
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
            saveTask(
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
        viewModelScope.launch {
            if (task.isDone) reopenTask(task.id) else completeTask(task.id)
        }
    }

    /** Свайп влево — «на завтра». На 5-м переносе поднимаем диалог разбора завала. */
    fun postponeToTomorrow(task: Task) {
        viewModelScope.launch {
            val count = postponeTask(task.id, source = PostponeSource.SWIPE)
            if (count >= STALE_DIALOG_THRESHOLD) staleTaskId.value = task.id
        }
    }

    fun delete(task: Task) {
        viewModelScope.launch { deleteTask(task.id) }
    }

    fun toggleExpanded(taskId: Long) {
        expandedTaskIds.update { current ->
            if (taskId in current) current - taskId else current + taskId
        }
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
                StaleResolution.DELETE -> deleteTask(task.id)
                StaleResolution.DELEGATE -> tasks.setDelegate(task.id, payload)
                StaleResolution.SPLIT -> tasks.addSubtasks(
                    task.id,
                    payload.orEmpty().split('\n').map { it.trim() }.filter { it.isNotEmpty() }
                )
                StaleResolution.SCHEDULE_SLOT -> saveTask(task.copy(dueDate = clock.today()))
                StaleResolution.KEEP -> Unit
            }
            staleTaskId.value = null
        }
    }

    // --- проекты -----------------------------------------------------------

    fun createProject(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val palette = PROJECT_PALETTE
            val color = palette[(name.hashCode().let { if (it < 0) -it else it }) % palette.size]
            projects.upsert(Project(name = name.trim(), color = color))
        }
    }

    fun archiveProject(project: Project) {
        viewModelScope.launch {
            projects.archive(project.id)
            if (filter.value.projectId == project.id) setProjectFilter(null)
        }
    }

    // --- фильтры и режим ---------------------------------------------------

    fun setViewMode(mode: TasksViewMode) { viewMode.value = mode }

    fun toggleStaleFilter() = filter.update { it.copy(onlyStale = !it.onlyStale) }

    fun setProjectFilter(projectId: Long?) = filter.update { it.copy(projectId = projectId) }

    fun setTagFilter(tagId: Long?) = filter.update { it.copy(tagId = tagId) }

    fun toggleShowDone() = filter.update { it.copy(includeDone = !it.includeDone) }

    fun quadrantOf(task: Task, today: LocalDate): EisenhowerQuadrant =
        classifier.quadrantOf(task, today)

    private companion object {
        const val STALE_DIALOG_THRESHOLD = 5

        /** Приглушённые цвета проекта — из общей палитры, без чистых тонов. */
        val PROJECT_PALETTE = intArrayOf(
            0xFF6E7BA6.toInt(), 0xFF7F6A9E.toInt(), 0xFF5E8C7A.toInt(),
            0xFFA8845C.toInt(), 0xFF9E6A6A.toInt(), 0xFF6A8C9E.toInt()
        )
    }
}
