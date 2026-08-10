package dev.ashwake.ui.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ashwake.data.backup.BackupContents
import dev.ashwake.data.backup.BackupManager
import dev.ashwake.data.backup.BackupResult
import dev.ashwake.data.backup.RestoreResult
import dev.ashwake.data.importer.ImportReport
import dev.ashwake.data.importer.ImportSource
import dev.ashwake.data.importer.LoopCsvParser
import dev.ashwake.data.importer.TickTickCsvParser
import dev.ashwake.data.importer.TodoistCsvParser
import dev.ashwake.data.settings.AppSettings
import dev.ashwake.domain.repository.habits.HabitRepository
import dev.ashwake.domain.repository.tasks.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Состояние мастера импорта: разбор → предпросмотр → применение (п. 12). */
data class ImportState(
    val report: ImportReport? = null,
    val applied: Boolean = false,
    val busy: Boolean = false
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backups: BackupManager,
    private val settings: AppSettings,
    private val loopParser: LoopCsvParser,
    private val tickTickParser: TickTickCsvParser,
    private val todoistParser: TodoistCsvParser,
    private val tasks: TaskRepository,
    private val habits: HabitRepository
) : ViewModel() {

    val backupFolder: StateFlow<String?> = settings.backupFolderUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _restorePreview = MutableStateFlow<BackupContents?>(null)
    val restorePreview: StateFlow<BackupContents?> = _restorePreview.asStateFlow()

    private val _restoring = MutableStateFlow(false)
    val restoring: StateFlow<Boolean> = _restoring.asStateFlow()

    /** Архив, который показан в предпросмотре и ждёт подтверждения. */
    private var pendingRestore: Pair<Uri, String?>? = null

    private val _import = MutableStateFlow(ImportState())
    val import: StateFlow<ImportState> = _import.asStateFlow()

    /** Разобранное, но ещё не применённое: применяется только по кнопке. */
    private var pendingTasks: List<dev.ashwake.domain.model.tasks.Task> = emptyList()
    private var pendingHabits: dev.ashwake.data.importer.ImportedHabits? = null

    // --- бэкап -------------------------------------------------------------

    fun setBackupFolder(uri: Uri) {
        viewModelScope.launch {
            // Постоянное разрешение: без него доступ к папке исчезнет
            // после перезапуска, и авто-бэкап тихо перестанет работать
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            settings.setBackupFolder(uri.toString())
            _message.value = "Папка выбрана. Копия делается раз в сутки"
        }
    }

    fun backupNow(password: String?) {
        viewModelScope.launch {
            _message.value = when (
                val result = backups.createBackup(password?.takeIf { it.isNotBlank() }?.toCharArray())
            ) {
                is BackupResult.Success ->
                    "Сохранено: ${result.fileName} · ${result.contents.total} записей"
                BackupResult.NoFolder -> "Сначала выберите папку"
                is BackupResult.Failed -> result.reason
            }
        }
    }

    fun readBackup(uri: Uri, password: String?) {
        viewModelScope.launch {
            pendingRestore = uri to password
            handleRestoreResult(
                backups.readBackup(uri, password?.takeIf { it.isNotBlank() }?.toCharArray())
            )
        }
    }

    /**
     * Применение архива. Отдельным шагом после предпросмотра: замена данных
     * необратима, и подтверждать её человек должен уже увидев, что внутри.
     */
    fun applyRestore() {
        val (uri, password) = pendingRestore ?: return
        viewModelScope.launch {
            _restoring.value = true
            handleRestoreResult(
                backups.restore(uri, password?.takeIf { it.isNotBlank() }?.toCharArray())
            )
            _restoring.value = false
        }
    }

    private fun handleRestoreResult(result: RestoreResult) {
        when (result) {
            is RestoreResult.Preview -> _restorePreview.value = result.contents
            is RestoreResult.Restored -> {
                _restorePreview.value = null
                pendingRestore = null
                _message.value = "Данные восстановлены: ${result.contents.total} записей"
            }
            RestoreResult.NeedsPassword -> _message.value = "Архив зашифрован — нужен пароль"
            RestoreResult.WrongPassword -> _message.value = "Пароль не подошёл"
            is RestoreResult.Failed -> _message.value = result.reason
        }
    }

    fun dismissRestorePreview() {
        _restorePreview.value = null
        pendingRestore = null
    }

    // --- импорт ------------------------------------------------------------

    fun parseImport(uri: Uri, source: ImportSource) {
        viewModelScope.launch {
            _import.value = ImportState(busy = true)

            val content = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?.toString(Charsets.UTF_8)
                }.getOrNull()
            }
            if (content == null) {
                _import.value = ImportState()
                _message.value = "Файл не читается"
                return@launch
            }

            when (source) {
                ImportSource.LOOP_CSV -> {
                    val parsed = loopParser.parse(content)
                    pendingHabits = parsed
                    pendingTasks = emptyList()
                    _import.value = ImportState(
                        report = ImportReport(
                            source = source,
                            imported = parsed.entries.size,
                            skipped = 0,
                            previewTitles = parsed.habits.map { it.name }
                        )
                    )
                }

                ImportSource.TICKTICK_CSV -> {
                    val (parsed, report) = tickTickParser.parse(content)
                    pendingTasks = parsed
                    pendingHabits = null
                    _import.value = ImportState(report = report)
                }

                ImportSource.TODOIST_CSV -> {
                    val (parsed, report) = todoistParser.parse(content)
                    pendingTasks = parsed
                    pendingHabits = null
                    _import.value = ImportState(report = report)
                }
            }
        }
    }

    /**
     * Применение импорта.
     *
     * Score по импортированной истории не переносится, а считается заново
     * нашим движком: чужая формула дала бы числа, не сходящиеся с будущими днями.
     */
    fun applyImport() {
        viewModelScope.launch {
            _import.value = _import.value.copy(busy = true)

            pendingTasks.forEach { tasks.upsert(it.copy(id = 0)) }

            pendingHabits?.let { imported ->
                val idByName = mutableMapOf<String, Long>()
                imported.habits.forEach { habit ->
                    idByName[habit.name] = habits.upsertHabit(habit.copy(id = 0))
                }
                imported.entries.forEach { entry ->
                    val habitId = idByName[entry.habitName] ?: return@forEach
                    habits.mark(
                        habitId = habitId,
                        date = entry.date,
                        status = entry.status,
                        value = entry.value
                    )
                }
            }

            pendingTasks = emptyList()
            pendingHabits = null
            _import.value = ImportState(applied = true)
            _message.value = "Импорт применён"
        }
    }

    fun cancelImport() {
        pendingTasks = emptyList()
        pendingHabits = null
        _import.value = ImportState()
    }

    fun consumeMessage() { _message.value = null }
}
