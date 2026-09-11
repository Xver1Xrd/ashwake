package dev.ashwake.ui.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ashwake.data.db.dao.ritual.RitualDao
import dev.ashwake.data.db.dao.tasks.TaskDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

enum class HistoryFilter(val title: String) {
    ALL("Все"),
    TASKS("Задачи"),
    ARCHIVE("Архив"),
    NOTES("Заметки")
}

enum class HistoryItemType {
    TASK, ARCHIVE, NOTE
}

data class SearchResultItem(
    val id: String,
    val type: HistoryItemType,
    val title: String,
    val subtitle: String? = null,
    val date: String? = null,
    val taskId: Long? = null
)

@HiltViewModel
class MoreViewModel @Inject constructor(
    private val taskDao: TaskDao,
    private val ritualDao: RitualDao
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filter = MutableStateFlow(HistoryFilter.ALL)
    val filter: StateFlow<HistoryFilter> = _filter.asStateFlow()

    val searchResults: StateFlow<List<SearchResultItem>> = combine(
        _query,
        _filter,
        taskDao.observeTasks(1, null, null, null, null, 0, null),
        taskDao.observeTrash(),
        ritualDao.observeReviews(0, 47500)
    ) { q, f, tasks, trash, reviews ->
        val trimmed = q.trim().lowercase()
        if (trimmed.isEmpty()) return@combine emptyList()

        val results = mutableListOf<SearchResultItem>()

        // 1. Задачи
        if (f == HistoryFilter.ALL || f == HistoryFilter.TASKS) {
            tasks.forEach { t ->
                val task = t.task
                val titleMatch = task.title.lowercase().contains(trimmed)
                val noteMatch = task.note?.lowercase()?.contains(trimmed) == true
                if (titleMatch || noteMatch) {
                    val statusText = if (task.status == "DONE") "✓ Выполнена" else "Активная"
                    val dateText = task.dueDate?.let { LocalDate.ofEpochDay(it.toLong()).format(DATE_FORMAT) }
                    results += SearchResultItem(
                        id = "task-${task.id}",
                        type = HistoryItemType.TASK,
                        title = task.title,
                        subtitle = task.note ?: statusText,
                        date = dateText,
                        taskId = task.id
                    )
                }
            }
        }

        // 2. Архив / Корзина
        if (f == HistoryFilter.ALL || f == HistoryFilter.ARCHIVE) {
            trash.forEach { t ->
                val task = t.task
                val titleMatch = task.title.lowercase().contains(trimmed)
                val noteMatch = task.note?.lowercase()?.contains(trimmed) == true
                if (titleMatch || noteMatch) {
                    results += SearchResultItem(
                        id = "trash-${task.id}",
                        type = HistoryItemType.ARCHIVE,
                        title = task.title,
                        subtitle = "В корзине" + (task.note?.let { " · $it" } ?: ""),
                        date = task.dueDate?.let { LocalDate.ofEpochDay(it.toLong()).format(DATE_FORMAT) },
                        taskId = task.id
                    )
                }
            }
        }

        // 3. Заметки ритуалов
        if (f == HistoryFilter.ALL || f == HistoryFilter.NOTES) {
            reviews.filter { !it.note.isNullOrBlank() }.forEach { rev ->
                val note = rev.note.orEmpty()
                if (note.lowercase().contains(trimmed)) {
                    val dateText = LocalDate.ofEpochDay(rev.date.toLong()).format(DATE_FORMAT)
                    results += SearchResultItem(
                        id = "review-${rev.date}",
                        type = HistoryItemType.NOTE,
                        title = "Заметка дня: $dateText",
                        subtitle = note,
                        date = dateText,
                        taskId = null
                    )
                }
            }
        }

        results
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setQuery(value: String) { _query.value = value }
    fun setFilter(value: HistoryFilter) { _filter.value = value }

    private companion object {
        val DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale("ru"))
    }
}
