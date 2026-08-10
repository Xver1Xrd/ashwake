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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import dev.ashwake.ui.components.AshNavBar
import dev.ashwake.ui.theme.AshTheme
import dev.ashwake.data.importer.ImportSource
import dev.ashwake.ui.theme.Ember
import dev.ashwake.ui.theme.Moss
import androidx.compose.ui.res.stringResource
import dev.ashwake.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel()
) {
    val folder by viewModel.backupFolder.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val restorePreview by viewModel.restorePreview.collectAsStateWithLifecycle()
    val restoring by viewModel.restoring.collectAsStateWithLifecycle()
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
        containerColor = AshTheme.colors.background,
        topBar = {
            AshNavBar(
                title = stringResource(R.string.backup_dannye),
                onBack = onBack
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
            Text(stringResource(R.string.backup_rezervnye_kopii), style = AshTheme.type.headline)
            Text(
                "Копия пишется в выбранную папку раз в сутки. Положите её туда, где работает ваша синхронизация — приложение само никуда ничего не отправляет",
                style = AshTheme.type.footnote,
                color = AshTheme.colors.text2
            )

            Text(
                folder?.let { "Папка выбрана" } ?: "Папка не выбрана",
                style = AshTheme.type.callout,
                color = if (folder != null) Moss else Ember
            )
            OutlinedButton(
                onClick = { pickFolder.launch(null) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (folder == null) "Выбрать папку" else "Сменить папку") }

            HorizontalDivider()
            Text(stringResource(R.string.backup_parol_arhiva), style = AshTheme.type.headline)
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.backup_parol)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                if (password.isBlank()) {
                    "Без пароля копия сохраняется открытым текстом — её сможет прочитать всё, что имеет доступ к папке"
                } else {
                    "Пароль нигде не хранится. Забытый пароль означает потерянный архив"
                },
                style = AshTheme.type.footnote,
                color = if (password.isBlank()) Ember else AshTheme.colors.text2
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.backupNow(password) },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.backup_sdelat_kopiyu)) }
                OutlinedButton(
                    onClick = { pickArchive.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.backup_otkryt_arhiv)) }
            }

            HorizontalDivider()
            Text(stringResource(R.string.backup_import_iz_drugogo_prilozheniya), style = AshTheme.type.headline)
            Text(
                "Разбор показывается до применения: ничего не меняется, пока вы не нажмёте «Применить»",
                style = AshTheme.type.footnote,
                color = AshTheme.colors.text2
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
            title = { Text(stringResource(R.string.backup_chto_raspoznalos)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Импортируется ${report.imported}, пропущено ${report.skipped}",
                        style = AshTheme.type.callout
                    )
                    if (report.previewTitles.isNotEmpty()) {
                        Text(stringResource(R.string.backup_naprimer), style = AshTheme.type.subhead)
                        report.previewTitles.forEach {
                            Text("· $it", style = AshTheme.type.subhead)
                        }
                    }
                    if (report.reasons.isNotEmpty()) {
                        HorizontalDivider()
                        Text(stringResource(R.string.backup_propuscheno), style = AshTheme.type.subhead)
                        report.reasons.forEach { (reason, count) ->
                            Text(
                                "$reason — $count",
                                style = AshTheme.type.subhead,
                                color = AshTheme.colors.text2
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = viewModel::applyImport,
                    enabled = !import.busy && report.imported > 0
                ) { Text(stringResource(R.string.backup_primenit)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelImport) { Text(stringResource(R.string.detail_otmena)) }
            }
        )
    }

    restorePreview?.let { contents ->
        AlertDialog(
            onDismissRequest = viewModel::dismissRestorePreview,
            title = { Text(stringResource(R.string.backup_chto_v_arhive)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Задач: ${contents.tasks}")
                    Text("Привычек: ${contents.habits}, отметок: ${contents.habitEntries}")
                    Text("Отказов: ${contents.abstinences}")
                    Text("Записей ритуала: ${contents.reviews}")
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text(
                        "Восстановление заменит текущие данные целиком: задачи, привычки с историей отметок, отказы, персонажа и монеты. Отменить это нельзя",
                        style = AshTheme.type.footnote,
                        color = AshTheme.colors.text2
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::applyRestore,
                    enabled = !restoring
                ) {
                    Text(
                        text = if (restoring) "Восстановление…" else "Заменить данные",
                        color = AshTheme.colors.danger
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissRestorePreview) { Text(stringResource(R.string.detail_otmena)) }
            }
        )
    }
}

private fun sourceLabel(source: ImportSource): String = when (source) {
    ImportSource.LOOP_CSV -> "Loop Habit Tracker · Checkmarks.csv"
    ImportSource.TICKTICK_CSV -> "TickTick · CSV"
    ImportSource.TODOIST_CSV -> "Todoist · CSV"
}
