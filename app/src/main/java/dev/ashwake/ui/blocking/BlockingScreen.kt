package dev.ashwake.ui.blocking

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ashwake.domain.model.blocking.UnlockCondition
import dev.ashwake.ui.theme.Ember
import dev.ashwake.ui.theme.Moss
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.ui.res.stringResource
import dev.ashwake.R

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockingScreen(
    onBack: () -> Unit,
    viewModel: BlockingViewModel = hiltViewModel()
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val bypasses by viewModel.bypasses.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }

    // Разрешения выдаются в настройках системы — перепроверяем на возврате
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.blocking_blokirovka_prilozheniy)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.detail_nazad))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.blocking_novoe_pravilo))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PermissionsBlock(permissions, viewModel)

            HorizontalDivider()
            Text(stringResource(R.string.blocking_pravila), style = MaterialTheme.typography.titleSmall)

            if (rules.isEmpty()) {
                Text(
                    "Правил нет. Правило описывает, какие приложения закрыты и что нужно сделать, чтобы они открылись",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            rules.forEach { rule ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(rule.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                conditionLabel(rule.condition, rule.unlockTime),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                rule.packages.joinToString { viewModel.appLabel(it) },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        }
                        Switch(
                            checked = rule.enabled,
                            enabled = permissions.allGranted,
                            onCheckedChange = { viewModel.setEnabled(rule, it) }
                        )
                        IconButton(onClick = { viewModel.delete(rule) }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.blocking_udalit))
                        }
                    }
                }
            }

            if (bypasses.isNotEmpty()) {
                HorizontalDivider()
                Text(stringResource(R.string.blocking_ekstrennye_obhody), style = MaterialTheme.typography.titleSmall)
                Text(
                    "Обход всегда возможен — запирать себя в собственном телефоне нельзя. Но он остаётся в логе, и по нему видно, работает правило или стало формальностью",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                bypasses.take(10).forEach { record ->
                    Text(
                        "${record.at.atZone(ZoneId.systemDefault()).format(TIME_FORMAT)} · " +
                            viewModel.appLabel(record.packageName),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    if (showCreate) {
        CreateRuleDialog(viewModel = viewModel, onDismiss = { showCreate = false })
    }
}

/**
 * Экран-объяснение разрешений (п. 7).
 *
 * Каждое разрешение объясняется до запроса: доступ к статистике и рисование
 * поверх других приложений выглядят пугающе, и молча просить их нельзя.
 */
@Composable
private fun PermissionsBlock(state: PermissionState, viewModel: BlockingViewModel) {
    Text(stringResource(R.string.blocking_razresheniya), style = MaterialTheme.typography.titleSmall)

    PermissionRow(
        granted = state.usageAccess,
        title = stringResource(R.string.blocking_dostup_k_statistike_ispolzovaniya),
        explanation = "Нужен, чтобы понять, какое приложение открыто прямо сейчас. Ashwake читает только имя приложения на переднем плане и никуда его не отправляет",
        onOpen = viewModel::openUsageAccessSettings
    )
    PermissionRow(
        granted = state.overlay,
        title = stringResource(R.string.blocking_poverh_drugih_prilozheniy),
        explanation = "Нужен, чтобы показать экран с напоминанием, что осталось сделать",
        onOpen = viewModel::openOverlaySettings
    )

    if (!state.allGranted) {
        Text(
            "Без обоих разрешений блокировка не включается",
            style = MaterialTheme.typography.labelMedium,
            color = Ember
        )
    }
}

@Composable
private fun PermissionRow(
    granted: Boolean,
    title: String,
    explanation: String,
    onOpen: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (granted) "✓  $title" else "○  $title",
                style = MaterialTheme.typography.bodyMedium,
                color = if (granted) Moss else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (!granted) TextButton(onClick = onOpen) { Text(stringResource(R.string.blocking_vydat)) }
        }
        Text(
            explanation,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateRuleDialog(viewModel: BlockingViewModel, onDismiss: () -> Unit) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val routines by viewModel.routineList.collectAsStateWithLifecycle()

    var name by remember { mutableStateOf("") }
    var condition by remember { mutableStateOf(UnlockCondition.MORNING_HABITS_DONE) }
    var routineId by remember { mutableStateOf<Long?>(null) }
    var hour by remember { mutableStateOf(10) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var query by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.blocking_novoe_pravilo)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.editor_nazvanie)) },
                    placeholder = { Text(stringResource(R.string.blocking_utro_bez_socsetey)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(stringResource(R.string.blocking_otkryt_kogda), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    UnlockCondition.entries.forEach { option ->
                        FilterChip(
                            selected = condition == option,
                            onClick = { condition = option },
                            label = { Text(shortConditionLabel(option)) }
                        )
                    }
                }

                when (condition) {
                    UnlockCondition.ROUTINE_DONE -> FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        routines.forEach { routine ->
                            FilterChip(
                                selected = routineId == routine.id,
                                onClick = { routineId = routine.id },
                                label = { Text(routine.name) }
                            )
                        }
                    }

                    UnlockCondition.TIME_AFTER -> FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(8, 10, 12, 14, 18).forEach { option ->
                            FilterChip(
                                selected = hour == option,
                                onClick = { hour = option },
                                label = { Text("%02d:00".format(option)) }
                            )
                        }
                    }

                    else -> Unit
                }

                HorizontalDivider()
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.blocking_poisk_prilozheniya)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    val visible = apps.filter {
                        query.isBlank() || it.label.contains(query, ignoreCase = true)
                    }
                    items(visible.size) { index ->
                        val app = visible[index]
                        val isSelected = app.packageName in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (isSelected) selected - app.packageName
                                    else selected + app.packageName
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (isSelected) "✓  ${app.label}" else "○  ${app.label}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isSelected) Moss
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && selected.isNotEmpty(),
                onClick = {
                    viewModel.createRule(
                        name = name,
                        condition = condition,
                        routineId = routineId,
                        unlockTime = LocalTime.of(hour, 0)
                            .takeIf { condition == UnlockCondition.TIME_AFTER },
                        packages = selected.toList()
                    )
                    onDismiss()
                }
            ) { Text(stringResource(R.string.editor_sozdat)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.detail_otmena)) } }
    )
}

private fun conditionLabel(condition: UnlockCondition, time: LocalTime?): String =
    when (condition) {
        UnlockCondition.MORNING_HABITS_DONE -> "Пока не отмечены утренние привычки"
        UnlockCondition.ROUTINE_DONE -> "Пока не пройдена рутина"
        UnlockCondition.TIME_AFTER ->
            "Откроется в " + (time?.let { "%02d:%02d".format(it.hour, it.minute) } ?: "—")
    }

private fun shortConditionLabel(condition: UnlockCondition): String = when (condition) {
    UnlockCondition.MORNING_HABITS_DONE -> "привычки"
    UnlockCondition.ROUTINE_DONE -> "рутина"
    UnlockCondition.TIME_AFTER -> "время"
}
