package dev.ashwake.ui.abstinence.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import dev.ashwake.ui.components.AshTextField
import dev.ashwake.ui.components.TextAction
import dev.ashwake.ui.components.ChipButton
import dev.ashwake.ui.theme.AshTheme
import dev.ashwake.ui.components.IconButtonSlot
import dev.ashwake.ui.components.IconPicker
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.ashwake.domain.model.abstinence.AbstinenceMode
import dev.ashwake.domain.model.abstinence.Baseline
import java.time.Duration
import java.time.Instant
import androidx.compose.ui.res.stringResource
import dev.ashwake.R

/**
 * Создание отказа.
 *
 * Baseline необязателен: без него блок «сэкономлено» просто не показывается.
 * Просить эти цифры при создании — самый честный момент, потом их никто не введёт.
 */
@Composable
fun CreateAbstinenceDialog(
    onCreate: (
        name: String,
        icon: String?,
        iconPath: String?,
        mode: AbstinenceMode,
        startedAt: Instant,
        motivation: String?,
        baseline: Baseline?,
        substitutes: List<String>
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf<String?>(null) }
    var iconPath by remember { mutableStateOf<String?>(null) }
    var showIconPicker by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(AbstinenceMode.GENTLE) }
    var daysAgo by remember { mutableIntStateOf(0) }
    var motivation by remember { mutableStateOf("") }
    var baselineEnabled by remember { mutableStateOf(false) }
    var unitName by remember { mutableStateOf("") }
    var unitsPerDay by remember { mutableStateOf("") }
    var costPerUnit by remember { mutableStateOf("") }
    var substitutes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.abstinence_novyy_otkaz)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    IconButtonSlot(
                        emoji = icon,
                        iconPath = iconPath,
                        expanded = showIconPicker,
                        onClick = { showIconPicker = !showIconPicker }
                    )
                    AshTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = stringResource(R.string.editor_nazvanie),
                        placeholder = stringResource(R.string.editor_ne_kuryu),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (showIconPicker) {
                    IconPicker(
                        emoji = icon,
                        iconPath = iconPath,
                        onEmoji = { picked -> icon = if (icon == picked) null else picked },
                        onIcon = { iconPath = it }
                    )
                }

                Text(stringResource(R.string.editor_rezhim), style = AshTheme.type.subhead)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AbstinenceMode.entries.forEach { option ->
                        ChipButton(
                        text = if (option == AbstinenceMode.GENTLE) "мягкий" else "строгий",
                        selected = mode == option,
                        onClick = { mode = option }
                    )
                    }
                }
                Text(
                    if (mode == AbstinenceMode.GENTLE)
                        "Срыв отнимает семь дней, но не обнуляет счётчик"
                    else "Срыв обнуляет счётчик, как в классических трекерах",
                    style = AshTheme.type.footnote,
                    color = AshTheme.colors.text2
                )

                Text(stringResource(R.string.editor_nachalo), style = AshTheme.type.subhead)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 1, 3, 7, 30).forEach { days ->
                        ChipButton(
                        text = if (days == 0) "сейчас" else "$days дн. назад",
                        selected = daysAgo == days,
                        onClick = { daysAgo = days }
                    )
                    }
                }

                AshTextField(
                    value = motivation,
                    onValueChange = { motivation = it },
                    label = stringResource(R.string.editor_zachem_ya_eto_brosil),
                    placeholder = stringResource(R.string.editor_pokazhetsya_kogda_stanet_tyazhelo),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                AshTextField(
                    value = substitutes,
                    onValueChange = { substitutes = it },
                    label = stringResource(R.string.editor_chem_zanyatsya_vmesto_po_stroke),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.editor_schitat_sekonomlennoe), style = AshTheme.type.subhead)
                        Text(
                            "Без этих цифр блок экономии просто не показывается",
                            style = AshTheme.type.footnote,
                            color = AshTheme.colors.text2
                        )
                    }
                    Switch(checked = baselineEnabled, onCheckedChange = { baselineEnabled = it })
                }

                if (baselineEnabled) {
                    AshTextField(
                        value = unitName,
                        onValueChange = { unitName = it },
                        label = stringResource(R.string.editor_chego_imenno),
                        placeholder = stringResource(R.string.editor_sigaret),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AshTextField(
                            value = unitsPerDay,
                            onValueChange = { unitsPerDay = it },
                            label = stringResource(R.string.editor_v_den),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        AshTextField(
                            value = costPerUnit,
                            onValueChange = { costPerUnit = it },
                            label = stringResource(R.string.editor_cena_za_shtuku),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextAction(
                text = stringResource(R.string.editor_sozdat),
                enabled = name.isNotBlank(),
                onClick = {
                    val baseline = if (baselineEnabled) {
                        val units = unitsPerDay.replace(',', '.').toFloatOrNull()
                        val cost = costPerUnit.replace(',', '.').toFloatOrNull()
                        // Половинчатый baseline дал бы неверную экономию — берём только полный
                        if (unitName.isNotBlank() && units != null && cost != null) {
                            Baseline(unitName.trim(), units, cost)
                        } else null
                    } else null
            
                    onCreate(
                        name,
                        icon,
                        iconPath,
                        mode,
                        Instant.now().minus(Duration.ofDays(daysAgo.toLong())),
                        motivation.takeIf { it.isNotBlank() },
                        baseline,
                        substitutes.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
                    )
                }
            )
        },
        dismissButton = { TextAction(text = stringResource(R.string.detail_otmena), onClick = onDismiss) }
    )
}
