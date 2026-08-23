package dev.ashwake.ui.settings

import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ashwake.R
import dev.ashwake.ui.components.AshIcons
import dev.ashwake.ui.components.AshNavBar
import dev.ashwake.ui.components.ChipButton
import dev.ashwake.ui.components.ListGroup
import dev.ashwake.ui.components.ListGroupFooter
import dev.ashwake.ui.components.ListDivider
import dev.ashwake.ui.components.ListRow
import dev.ashwake.ui.components.SegmentedControl
import dev.ashwake.ui.components.tappable
import dev.ashwake.ui.theme.AccentColor
import dev.ashwake.ui.theme.AshTheme
import dev.ashwake.ui.theme.ThemeMode
import dev.ashwake.ui.theme.ThemeSettings

/**
 * Настройки.
 *
 * Раскладка группами, а не сплошной лентой с разделителями: настройка
 * находится по группе, в которой лежит, и её не приходится вычитывать из
 * общего потока. Каждой группе положено пояснение снизу — настройка,
 * смысл которой надо угадывать, не настраивается, а перещёлкивается
 * наугад.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenBlocking: () -> Unit,
    onOpenBackup: () -> Unit = {},
    onOpenThemeEditor: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val timebox by viewModel.timebox.collectAsStateWithLifecycle()
    val dayStart by viewModel.dayStartHour.collectAsStateWithLifecycle()
    val useCalendar by viewModel.useCalendar.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val colors = AshTheme.colors

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        AshNavBar(title = stringResource(R.string.character_nastroyki), onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(GroupGap)
        ) {
            AppearanceSection(
                theme = theme,
                onThemeMode = viewModel::setThemeMode,
                onAccent = viewModel::setAccent,
                onOpenEditor = onOpenThemeEditor
            )

            ChipGroup(
                header = stringResource(R.string.settings_nachalo_sutok),
                footer = stringResource(R.string.settings_otmetka_v_chas_nochi_popadet_v_predyduschiy),
                options = listOf(0, 2, 4, 6),
                label = { "%02d:00".format(it) },
                selected = { it == dayStart },
                onSelect = viewModel::setDayStartHour
            )

            ListGroup(
                header = stringResource(R.string.settings_rabochie_chasy),
                footer = stringResource(R.string.settings_v_etom_okne_raskladyvaetsya_den)
            ) {
                ChipRow(
                    options = listOf(6, 7, 8, 9, 10),
                    label = { "с %02d".format(it) },
                    selected = { timebox.workStartMinute == it * 60 },
                    onSelect = viewModel::setWorkStart
                )
                ListDivider(inset = 0.dp)
                ChipRow(
                    options = listOf(17, 18, 19, 20, 22),
                    label = { "до %02d".format(it) },
                    selected = { timebox.workEndMinute == it * 60 },
                    onSelect = viewModel::setWorkEnd
                )
            }

            ChipGroup(
                header = stringResource(R.string.settings_bufer_mezhdu_blokami),
                footer = stringResource(R.string.settings_zazor_mezhdu_sosednimi_blokami_bez_nego_den),
                options = listOf(0, 5, 10, 15),
                label = { "$it мин" },
                selected = { timebox.bufferMinutes == it },
                onSelect = viewModel::setBuffer
            )

            ListGroup(header = stringResource(R.string.settings_obed)) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(ChipRowPadding),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChipButton(
                        text = stringResource(R.string.settings_bez_obeda),
                        selected = timebox.lunchStartMinute == null,
                        onClick = { viewModel.setLunch(false) }
                    )
                    listOf(12, 13, 14).forEach { hour ->
                        ChipButton(
                            text = "%02d:00".format(hour),
                            selected = timebox.lunchStartMinute == hour * 60,
                            onClick = { viewModel.setLunch(true, hour) }
                        )
                    }
                }
            }

            ListGroup(header = stringResource(R.string.settings_dannye_i_sistema)) {
                ListRow(
                    title = stringResource(R.string.settings_sistemnyy_kalendar),
                    subtitle = if (useCalendar) stringResource(R.string.settings_sobytiya_uchityvayutsya_pri_raskladke_dnya)
                    else stringResource(R.string.settings_sobytiya_kalendarya_ne_uchityvayutsya),
                    trailing = {
                        Switch(
                            checked = useCalendar,
                            onCheckedChange = { viewModel.setUseCalendar(it) }
                        )
                    },
                    onClick = { viewModel.setUseCalendar(!useCalendar) }
                )
                ListDivider()
                ListRow(
                    title = stringResource(R.string.settings_dannye_i_bekapy),
                    subtitle = stringResource(
                        R.string.settings_rezervnye_kopii_shifrovanie_import_iz_drugih
                    ),
                    showChevron = true,
                    onClick = onOpenBackup
                )
                ListDivider()
                ListRow(
                    title = stringResource(R.string.blocking_blokirovka_prilozheniy),
                    subtitle = stringResource(
                        R.string.settings_vyklyuchena_po_umolchaniyu_trebuet_dvuh_razr
                    ),
                    showChevron = true,
                    onClick = onOpenBlocking
                )
            }

            BatteryOptimizationSection()

            ListGroupFooter(
                stringResource(R.string.settings_prilozhenie_rabotaet_oflayn_ni_odnogo_setevo)
            )
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

/**
 * Оформление: быстрый выбор темы и акцента прямо в настройках, всё
 * остальное — в редакторе. Два самых частых переключения не должны
 * требовать захода на отдельный экран, а двадцать редких не должны
 * растягивать настройки на три экрана прокрутки.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceSection(
    theme: ThemeSettings,
    onThemeMode: (ThemeMode) -> Unit,
    onAccent: (AccentColor) -> Unit,
    onOpenEditor: () -> Unit
) {
    val colors = AshTheme.colors
    val modes = ThemeMode.entries

    ListGroup(header = stringResource(R.string.settings_oformlenie)) {
        Box(Modifier.padding(ChipRowPadding)) {
            SegmentedControl(
                options = modes.map { stringResource(it.titleRes) },
                selectedIndex = modes.indexOf(theme.mode),
                onSelect = { onThemeMode(modes[it]) }
            )
        }

        ListDivider(inset = 0.dp)

        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(ChipRowPadding),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AccentColor.entries.forEach { accent ->
                // Свой цвет из редактора отменяет пресет: иначе выбранными
                // выглядели бы сразу два, и непонятно, что применено
                val selected = theme.customAccent == null && theme.accent == accent
                Box(
                    Modifier
                        .size(40.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    accent.resolve(colors.isDark),
                                    accent.resolveAlt(colors.isDark)
                                )
                            ),
                            CircleShape
                        )
                        .border(
                            width = if (selected) 2.dp else 0.dp,
                            color = if (selected) colors.text else Color.Transparent,
                            shape = CircleShape
                        )
                        .tappable(onClick = { onAccent(accent) }),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Icon(
                            AshIcons.Check,
                            contentDescription = stringResource(accent.titleRes),
                            tint = if (colors.isDark) Color.Black else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        ListDivider()

        ListRow(
            title = stringResource(R.string.settings_redaktor_temy),
            subtitle = stringResource(R.string.settings_svoy_cvet_forma_uglov_skruglenie_plotnost_ef),
            showChevron = true,
            onClick = onOpenEditor
        )
    }
}

/** Группа из одного ряда таблеток: половина настроек здесь именно такая. */
@Composable
private fun <T> ChipGroup(
    header: String,
    options: List<T>,
    label: (T) -> String,
    selected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    footer: String? = null
) {
    ListGroup(header = header, footer = footer) {
        ChipRow(options, label, selected, onSelect)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(
    options: List<T>,
    label: (T) -> String,
    selected: (T) -> Boolean,
    onSelect: (T) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(ChipRowPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            ChipButton(
                text = label(option),
                selected = selected(option),
                onClick = { onSelect(option) }
            )
        }
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
    val colors = AshTheme.colors
    val ignoring = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.getSystemService(PowerManager::class.java)
                ?.isIgnoringBatteryOptimizations(context.packageName) == true
        } else true
    }

    ListGroup(header = stringResource(R.string.settings_battery_title)) {
        ListRow(
            title = if (ignoring) stringResource(R.string.settings_battery_ok)
            else stringResource(R.string.settings_battery_warning),
            titleColor = if (ignoring) colors.text else colors.danger,
            leading = {
                Icon(
                    imageVector = if (ignoring) AshIcons.CheckCircle else AshIcons.Warning,
                    contentDescription = null,
                    tint = if (ignoring) colors.success else colors.danger,
                    modifier = Modifier.size(20.dp)
                )
            }
        )
        if (!ignoring) {
            ListDivider()
            ListRow(
                title = stringResource(R.string.settings_battery_action),
                subtitle = stringResource(R.string.settings_battery_action_hint),
                showChevron = true,
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
}

/** Просвет между группами: меньше — и группы сливаются в одну ленту. */
private val GroupGap = 18.dp

/** Внутренние поля ряда таблеток внутри группы. */
private val ChipRowPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
