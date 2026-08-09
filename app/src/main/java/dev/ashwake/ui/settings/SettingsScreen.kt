package dev.ashwake.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import dev.ashwake.R
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenBlocking: () -> Unit,
    onOpenBackup: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val timebox by viewModel.timebox.collectAsStateWithLifecycle()
    val dayStart by viewModel.dayStartHour.collectAsStateWithLifecycle()
    val useCalendar by viewModel.useCalendar.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.character_nastroyki)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.detail_nazad))
                    }
                }
            )
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
            Text(stringResource(R.string.settings_nachalo_sutok), style = MaterialTheme.typography.titleSmall)
            Text(
                "Отметка в час ночи попадёт в предыдущий день. Влияет на стрики, счётчики отказов и статистику",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0, 2, 4, 6).forEach { hour ->
                    FilterChip(
                        selected = dayStart == hour,
                        onClick = { viewModel.setDayStartHour(hour) },
                        label = { Text("%02d:00".format(hour)) }
                    )
                }
            }

            HorizontalDivider()
            Text(stringResource(R.string.settings_rabochie_chasy), style = MaterialTheme.typography.titleSmall)
            Text(
                "В этом окне раскладывается день",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(6, 7, 8, 9, 10).forEach { hour ->
                    FilterChip(
                        selected = timebox.workStartMinute == hour * 60,
                        onClick = { viewModel.setWorkStart(hour) },
                        label = { Text("с %02d".format(hour)) }
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(17, 18, 19, 20, 22).forEach { hour ->
                    FilterChip(
                        selected = timebox.workEndMinute == hour * 60,
                        onClick = { viewModel.setWorkEnd(hour) },
                        label = { Text("до %02d".format(hour)) }
                    )
                }
            }

            Text(stringResource(R.string.settings_bufer_mezhdu_blokami), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0, 5, 10, 15).forEach { minutes ->
                    FilterChip(
                        selected = timebox.bufferMinutes == minutes,
                        onClick = { viewModel.setBuffer(minutes) },
                        label = { Text("$minutes мин") }
                    )
                }
            }

            Text(stringResource(R.string.settings_obed), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = timebox.lunchStartMinute == null,
                    onClick = { viewModel.setLunch(false) },
                    label = { Text(stringResource(R.string.settings_bez_obeda)) }
                )
                listOf(12, 13, 14).forEach { hour ->
                    FilterChip(
                        selected = timebox.lunchStartMinute == hour * 60,
                        onClick = { viewModel.setLunch(true, hour) },
                        label = { Text("%02d:00".format(hour)) }
                    )
                }
            }

            HorizontalDivider()
            SettingsRow(
                title = stringResource(R.string.settings_sistemnyy_kalendar),
                subtitle = if (useCalendar) "События учитываются при раскладке дня"
                else "События календаря не учитываются",
                onClick = { viewModel.setUseCalendar(!useCalendar) }
            )
            SettingsRow(
                title = stringResource(R.string.settings_dannye_i_bekapy),
                subtitle = stringResource(R.string.settings_rezervnye_kopii_shifrovanie_import_iz_drugih),
                onClick = onOpenBackup
            )
            SettingsRow(
                title = stringResource(R.string.blocking_blokirovka_prilozheniy),
                subtitle = stringResource(R.string.settings_vyklyuchena_po_umolchaniyu_trebuet_dvuh_razr),
                onClick = onOpenBlocking
            )

            HorizontalDivider()
            BatteryOptimizationSection()

            HorizontalDivider()
            Text(
                "Приложение работает офлайн: ни одного сетевого вызова, ни аналитики, ни сторонних SDK",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

/**
 * Оптимизация батареи (п. 16 приёмки).
 *
 * Если система усыпляет приложение, будильники привычек, рутин и настойчивые
 * напоминания приходят с опозданием или не приходят вовсе — а это ровно то,
 * ради чего приложение и заводят. Поэтому здесь не молчаливый запрос
 * разрешения, а объяснение и ссылка в системные настройки: решение остаётся
 * за человеком, но он знает, чем платит.
 *
 * Открывается общий список, а не прямой запрос исключения: прямой запрос
 * требует разрешения, за которое Play снимает приложения с публикации.
 */
@Composable
private fun BatteryOptimizationSection() {
    val context = LocalContext.current
    val ignoring = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.getSystemService(PowerManager::class.java)
                ?.isIgnoringBatteryOptimizations(context.packageName) == true
        } else true
    }

    Text(
        stringResource(R.string.settings_battery_title),
        style = MaterialTheme.typography.titleSmall
    )
    Text(
        if (ignoring) stringResource(R.string.settings_battery_ok)
        else stringResource(R.string.settings_battery_warning),
        style = MaterialTheme.typography.labelSmall,
        color = if (ignoring) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.error
    )
    if (!ignoring) {
        SettingsRow(
            title = stringResource(R.string.settings_battery_action),
            subtitle = stringResource(R.string.settings_battery_action_hint),
            onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        )
    }
}
