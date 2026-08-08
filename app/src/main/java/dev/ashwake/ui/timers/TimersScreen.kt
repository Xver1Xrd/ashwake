package dev.ashwake.ui.timers

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ashwake.ui.focus.FocusScreen
import dev.ashwake.ui.routines.RoutinesScreen

/**
 * Рутины и фокус под одной вкладкой.
 *
 * Обе фичи — таймеры, и разносить их по отдельным пунктам нижней навигации
 * значило бы занять два места из пяти под одно и то же действие «запустить время».
 */
@Composable
fun TimersScreen(onRunRoutine: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            listOf("Рутины", "Фокус").forEachIndexed { index, label ->
                SegmentedButton(
                    selected = tab == index,
                    onClick = { tab = index },
                    shape = SegmentedButtonDefaults.itemShape(index, 2),
                    label = { Text(label) }
                )
            }
        }

        when (tab) {
            0 -> RoutinesScreen(onRun = onRunRoutine)
            else -> FocusScreen()
        }
    }
}
