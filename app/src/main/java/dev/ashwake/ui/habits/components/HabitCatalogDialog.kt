package dev.ashwake.ui.habits.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ashwake.data.assets.HabitPresetCategory
import dev.ashwake.domain.model.habits.Habit
import dev.ashwake.domain.model.habits.HabitScheduleType
import dev.ashwake.domain.model.habits.HabitType
import androidx.compose.ui.res.stringResource
import dev.ashwake.R

/** Каталог готовых привычек по категориям (п. 3). */
@Composable
fun HabitCatalogDialog(
    categories: List<HabitPresetCategory>,
    onPick: (Habit) -> Unit,
    onCreateOwn: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.habits_dobavit_privychku)) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onCreateOwn)
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text(stringResource(R.string.components_svoya_privychka), style = MaterialTheme.typography.bodyLarge)
                    }
                }

                categories.forEach { category ->
                    item(key = "cat-${category.sphere}") {
                        Text(
                            category.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                    }
                    items(category.habits.size) { index ->
                        val habit = category.habits[index]
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(habit) }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(habit.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                describe(habit),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.components_zakryt)) } }
    )
}

private fun describe(habit: Habit): String {
    val schedule = when (habit.schedule.type) {
        HabitScheduleType.DAILY -> "каждый день"
        HabitScheduleType.TIMES_PER_WEEK -> "${habit.schedule.timesPerWeek} раза в неделю"
        HabitScheduleType.EVERY_OTHER_DAY -> "через день"
        HabitScheduleType.WEEKDAYS -> "по дням недели"
        HabitScheduleType.BIWEEKLY -> "раз в две недели"
    }
    val target = when (habit.type) {
        HabitType.COUNTER ->
            " · ${formatValue(habit.targetValue)}${habit.unitName?.let { " $it" } ?: ""}"
        HabitType.NEGATIVE -> " · отказ"
        HabitType.CHECK -> ""
    }
    val minimum = habit.minimumValue?.let { " · минимум ${formatValue(it)}" }.orEmpty()
    return schedule + target + minimum
}
