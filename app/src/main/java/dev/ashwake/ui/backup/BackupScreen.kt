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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import dev.ashwake.ui.components.AshTextField
import dev.ashwake.ui.components.PrimaryButton
import dev.ashwake.ui.components.ChipButton
import dev.ashwake.ui.components.TextAction
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
                stringResource(R.string.backup_kopiya_pishetsya_v_vybrannuyu_papku_raz_v_su),
                style = AshTheme.type.footnote,
                color = AshTheme.colors.text2
            )

            Text(
                folder?.let { stringResource(R.string.backup_papka_vybrana) } ?: stringResource(R.string.backup_papka_ne_vybrana),
                style = AshTheme.type.callout,
                color = if (folder != null) Moss else Ember
            )
            ChipButton(
                text = if (folder == null) stringResource(R.string.backup_vybrat_papku) else stringResource(R.string.backup_smenit_papku),
                modifier = Modifier.fillMaxWidth(),
                onClick = { pickFolder.launch(null) }
            )

            HorizontalDivider()
            Text(stringResource(R.string.backup_parol_arhiva), style = AshTheme.type.headline)
            AshTextField(
                value = password,
                onValueChange = { password = it },
                label = stringResource(R.string.backup_parol),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                if (password.isBlank()) {
                    stringResource(R.string.backup_bez_parolya_kopiya_sohranyaetsya_otkrytym_te)
                } else {
                    stringResource(R.string.backup_parol_nigde_ne_hranitsya_zabytyy_parol_oznac)
                },
                style = AshTheme.type.footnote,
                color = if (password.isBlank()) Ember else AshTheme.colors.text2
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(
                    text = stringResource(R.string.backup_sdelat_kopiyu),
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.backupNow(password) }
                )
                ChipButton(
                    text = stringResource(R.string.backup_otkryt_arhiv),
                    modifier = Modifier.weight(1f),
                    onClick = { pickArchive.launch(arrayOf("*/*")) }
                )
            }

            HorizontalDivider()
            Text(stringResource(R.string.backup_import_iz_drugogo_prilozheniya), style = AshTheme.type.headline)
            Text(
                stringResource(R.string.backup_razbor_pokazyvaetsya_do_primeneniya_nichego),
                style = AshTheme.type.footnote,
                color = AshTheme.colors.text2
            )

            ImportSource.entries.forEach { source ->
                ChipButton(
                    text = sourceLabel(source),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        importSource = source
                        pickImport.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values"))
                    }
                )
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
                        stringResource(R.string.backup_importiruetsya_1_s_propuscheno_2_s, report.imported, report.skipped),
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
                PrimaryButton(
                    text = stringResource(R.string.backup_primenit),
                    enabled = !import.busy && report.imported > 0,
                    onClick = viewModel::applyImport
                )
            },
            dismissButton = {
                TextAction(text = stringResource(R.string.detail_otmena), onClick = viewModel::cancelImport)
            }
        )
    }

    restorePreview?.let { contents ->
        AlertDialog(
            onDismissRequest = viewModel::dismissRestorePreview,
            title = { Text(stringResource(R.string.backup_chto_v_arhive)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.backup_zadach_1_s, contents.tasks))
                    Text(stringResource(R.string.backup_privychek_1_s_otmetok_2_s, contents.habits, contents.habitEntries))
                    Text(stringResource(R.string.backup_otkazov_1_s, contents.abstinences))
                    Text(stringResource(R.string.backup_zapisey_rituala_1_s, contents.reviews))
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text(
                        stringResource(R.string.backup_vosstanovlenie_zamenit_tekuschie_dannye_celi),
                        style = AshTheme.type.footnote,
                        color = AshTheme.colors.text2
                    )
                }
            },
            confirmButton = {
                TextAction(
                    text = if (restoring) stringResource(R.string.backup_vosstanovlenie) else stringResource(R.string.backup_zamenit_dannye),
                    color = AshTheme.colors.danger,
                    enabled = !restoring,
                    onClick = viewModel::applyRestore
                )
            },
            dismissButton = {
                TextAction(
                    text = stringResource(R.string.detail_otmena),
                    onClick = viewModel::dismissRestorePreview
                )
            }
        )
    }
}

private fun sourceLabel(source: ImportSource): String = when (source) {
    ImportSource.LOOP_CSV -> "Loop Habit Tracker · Checkmarks.csv"
    ImportSource.TICKTICK_CSV -> "TickTick · CSV"
    ImportSource.TODOIST_CSV -> "Todoist · CSV"
}
