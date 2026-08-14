package dev.ashwake.ui.tasks.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ashwake.ui.components.AshTextField
import dev.ashwake.ui.components.ChipButton
import dev.ashwake.ui.components.TextAction
import dev.ashwake.ui.theme.AshTheme
import dev.ashwake.domain.model.tasks.StaleResolution
import dev.ashwake.domain.model.tasks.Task
import androidx.compose.ui.res.stringResource
import dev.ashwake.R

/**
 * Диалог залежавшейся задачи: открывается на 5-м переносе (п. 1).
 *
 * Формулировки нейтральные — это разбор завала, а не выговор. Вариант «оставить»
 * есть всегда: если человек его выбирает третий раз подряд, это данные для аналитики,
 * а не повод не дать закрыть окно.
 */
@Composable
fun StaleTaskDialog(
    task: Task,
    onResolve: (StaleResolution, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var mode by remember { mutableStateOf<StaleResolution?>(null) }
    var payload by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.components_zadacha_perenositsya_1_s_y_raz, task.postponeCount)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(task.title, style = AshTheme.type.body)
                Text(
                    stringResource(R.string.components_pohozhe_v_tekuschem_vide_ona_ne_dvigaetsya_c),
                    style = AshTheme.type.callout,
                    color = AshTheme.colors.text2
                )

                when (mode) {
                    StaleResolution.DELEGATE -> AshTextField(
                        value = payload,
                        onValueChange = { payload = it },
                        label = stringResource(R.string.components_komu_delegirovat),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                        )

                    StaleResolution.SPLIT -> AshTextField(
                        value = payload,
                        onValueChange = { payload = it },
                        label = stringResource(R.string.components_podzadachi_po_odnoy_v_stroke),
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                        )

                    else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ChipButton(
                            text = stringResource(R.string.blocking_udalit),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onResolve(StaleResolution.DELETE, null) }
                        )

                        ChipButton(
                            text = stringResource(R.string.components_delegirovat),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { mode = StaleResolution.DELEGATE }
                        )

                        ChipButton(
                            text = stringResource(R.string.components_razbit_na_podzadachi),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { mode = StaleResolution.SPLIT }
                        )

                        ChipButton(
                            text = stringResource(R.string.components_postavit_na_segodnya),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onResolve(StaleResolution.SCHEDULE_SLOT, null) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (mode != null) {
                TextAction(
                    text = stringResource(R.string.editor_gotovo),
                    enabled = payload.isNotBlank(),
                    onClick = { onResolve(mode!!, payload.takeIf { it.isNotBlank() }) }
                )
            }
        },
        dismissButton = {
            TextAction(
                text = if (mode != null) stringResource(R.string.detail_nazad) else stringResource(R.string.components_ostavit_kak_est),
                onClick = {
                    if (mode != null) mode = null else onResolve(StaleResolution.KEEP, null)
                }
            )
        }
    )
}
