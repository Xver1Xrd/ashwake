package dev.ashwake.ui.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ashwake.data.db.dao.ritual.RitualDao
import dev.ashwake.data.db.dao.ritual.SearchReviewRow
import dev.ashwake.data.db.dao.tasks.TaskDao
import dev.ashwake.data.db.dao.tasks.SearchTaskRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
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
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class MoreViewModel @Inject constructor(
    private val taskDao: TaskDao,
    private val ritualDao: RitualDao
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filter = MutableStateFlow(HistoryFilter.ALL)
    val filter: StateFlow<HistoryFilter> = _filter.asStateFlow()

    // Keep only the light projections warm between keystrokes. The database
    // subscriptions still stop shortly after search is closed.
    private val taskRows: StateFlow<List<SearchTaskRow>> = taskDao.observeSearchTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val trashRows: StateFlow<List<SearchTaskRow>> = taskDao.observeSearchTrash()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val noteRows: StateFlow<List<SearchReviewRow>> = ritualDao.observeSearchNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Лёгкие проекции: поиск сохраняет Unicode-регистр и не грузит связи задач. */
    val searchResults: StateFlow<List<SearchResultItem>> = combine(_query, _filter) { q, f ->
        q.trim().lowercase() to f
    }.debounce(220).flatMapLatest { (trimmed, f) ->
        if (trimmed.isEmpty()) return@flatMapLatest flowOf(emptyList())
        when (f) {
            HistoryFilter.TASKS -> taskRows.map { rows ->
                rows.toSearchResults(trimmed, archived = false)
            }
            HistoryFilter.ARCHIVE -> trashRows.map { rows ->
                rows.toSearchResults(trimmed, archived = true)
            }
            HistoryFilter.NOTES -> noteRows.map { rows ->
                rows.filter { it.note.lowercase().contains(trimmed) }.map { review ->
                    val date = LocalDate.ofEpochDay(review.date.toLong()).format(DATE_FORMAT)
                    SearchResultItem("review-${review.date}", HistoryItemType.NOTE, "Заметка дня: $date", review.note, date)
                }
            }
            HistoryFilter.ALL -> combine(
                taskRows,
                trashRows,
                noteRows
            ) { tasks, trash, reviews ->
                tasks.toSearchResults(trimmed, archived = false) +
                    trash.toSearchResults(trimmed, archived = true) +
                    reviews.filter { it.note.lowercase().contains(trimmed) }.map { review ->
                        val date = LocalDate.ofEpochDay(review.date.toLong()).format(DATE_FORMAT)
                        SearchResultItem("review-${review.date}", HistoryItemType.NOTE, "Заметка дня: $date", review.note, date)
                    }
            }
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun List<SearchTaskRow>.toSearchResults(query: String, archived: Boolean): List<SearchResultItem> =
        filter { it.title.lowercase().contains(query) || it.note?.lowercase()?.contains(query) == true }
            .map { task ->
                val date = task.dueDate?.let { LocalDate.ofEpochDay(it.toLong()).format(DATE_FORMAT) }
                SearchResultItem(
                    id = "${if (archived) "trash" else "task"}-${task.id}",
                    type = if (archived) HistoryItemType.ARCHIVE else HistoryItemType.TASK,
                    title = task.title,
                    subtitle = if (archived) "В корзине" + (task.note?.let { " · $it" } ?: "")
                        else task.note ?: if (task.status == "DONE") "✓ Выполнена" else "Активная",
                    date = date,
                    taskId = task.id
                )
            }

    fun setQuery(value: String) { _query.value = value }
    fun setFilter(value: HistoryFilter) { _filter.value = value }

    private companion object {
        val DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale("ru"))
    }
}
