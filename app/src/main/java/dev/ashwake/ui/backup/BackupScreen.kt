package dev.ashwake.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ashwake.data.importer.ImportSource
import dev.ashwake.ui.theme.Ember
import dev.ashwake.ui.theme.Moss

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel()
) {
    val folder by viewModel.backupFolder.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val restorePreview by viewModel.restorePreview.collectAsStateWithLifecycle()
    val import by viewModel.import.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    var password by remember { mutableStateOf("") }
    var importSource by remember { mutableStateOf(ImportSource.TICKTICK_CSV) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let(viewModel::setBackupFolder) }

    val pickArchive = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.readBackup(it, password) } }

    val pickImport = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.parseImport(it, importSource) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Данные") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Резервные копии", style = MaterialTheme.typography.titleSmall)
            Text(
                "Копия пишется в выбранную папку раз в сутки. Положите её туда, " +
                    "где работает ваша синхронизация — приложение само никуда " +
                    "ничего не отправляет",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                folder?.let { "Папка выбрана" } ?: "Папка не выбрана",
                style = MaterialTheme.typography.bodyMedium,
                color = if (folder != null) Moss else Ember
            )
            OutlinedButton(
                onClick = { pickFolder.launch(null) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (folder == null) "Выбрать папку" else "Сменить папку") }

            HorizontalDivider()
            Text("Пароль архива", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Пароль") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                if (password.isBlank()) {
                    "Без пароля копия сохраняется открытым текстом — её сможет " +
                        "прочитать всё, что имеет доступ к папке"
                } else {
                    "Пароль нигде не хранится. Забытый пароль означает потерянный архив"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (password.isBlank()) Ember else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.backupNow(password) },
                    modifier = Modifier.weight(1f)
                ) { Text("Сделать копию") }
                OutlinedButton(
                    onClick = { pickArchive.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f)
                ) { Text("Открыть архив") }
            }

            HorizontalDivider()
            Text("Импорт из другого приложения", style = MaterialTheme.typography.titleSmall)
            Text(
                "Разбор показывается до применения: ничего не меняется, " +
                    "пока вы не нажмёте «Применить»",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ImportSource.entries.forEach { source ->
                OutlinedButton(
                    onClick = {
                        importSource = source
                        pickImport.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(sourceLabel(source)) }
            }
        }
    }

    import.report?.let { report ->
        AlertDialog(
            onDismissRequest = viewModel::cancelImport,
            title = { Text("Что распозналось") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Импортируется ${report.imported}, пропущено ${report.skipped}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (report.previewTitles.isNotEmpty()) {
                        Text("Например:", style = MaterialTheme.typography.labelLarge)
                        report.previewTitles.forEach {
                            Text("· $it", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (report.reasons.isNotEmpty()) {
                        HorizontalDivider()
                        Text("Пропущено:", style = MaterialTheme.typography.labelLarge)
                        report.reasons.forEach { (reason, count) ->
                            Text(
                                "$reason — $count",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = viewModel::applyImport,
                    enabled = !import.busy && report.imported > 0
                ) { Text("Применить") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelImport) { Text("Отмена") }
            }
        )
    }

    restorePreview?.let { contents ->
        AlertDialog(
            onDismissRequest = viewModel::dismissRestorePreview,
            title = { Text("Что в архиве") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Задач: ${contents.tasks}")
                    Text("Привычек: ${contents.habits}, отметок: ${contents.habitEntries}")
                    Text("Отказов: ${contents.abstinences}")
                    Text("Записей ритуала: ${contents.reviews}")
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text(
                        "Архив прочитан. Запись в базу появится вместе с полной " +
                            "заменой данных — она необратима, и делать её вслепую нельзя",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissRestorePreview) { Text("Закрыть") }
            }
        )
    }
}

private fun sourceLabel(source: ImportSource): String = when (source) {
    ImportSource.LOOP_CSV -> "Loop Habit Tracker · Checkmarks.csv"
    ImportSource.TICKTICK_CSV -> "TickTick · CSV"
    ImportSource.TODOIST_CSV -> "Todoist · CSV"
}
