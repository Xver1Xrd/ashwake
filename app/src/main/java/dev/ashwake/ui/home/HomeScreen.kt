package dev.ashwake.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ashwake.ui.character.render.CharacterLayer
import dev.ashwake.ui.character.render.PixelCharacter
import dev.ashwake.ui.theme.Gold

/**
 * Главный экран: персонаж на диораме и прогресс дня (п. 15.9).
 *
 * Держит самую важную петлю: сюда смотришь утром и видишь, что день уже
 * начался, — уровень, монеты и закрытые задачи.
 */
@Composable
fun HomeScreen(
    onOpenToday: () -> Unit = {},
    onOpenCharacter: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()

    val layers = remember(state.equipped, catalog) {
        val hidden = state.equipped.values.flatMap { it.hides }.toSet()
        state.equipped.values
            .filterNot { it.slot in hidden }
            .sortedBy { it.layer }
            .map { item ->
                CharacterLayer(
                    slot = item.slot,
                    color = Color(catalog.paletteTints[item.paletteId] ?: DEFAULT_TINT),
                    label = item.slot.title,
                    frames = item.frames
                )
            }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(state.profile.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Уровень ${state.level} · ${state.wallet.xp} XP",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "${state.wallet.coins} ◈",
                    style = MaterialTheme.typography.titleMedium,
                    color = Gold
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                PixelCharacter(
                    layers = layers,
                    modifier = Modifier.fillMaxSize(),
                    reduceMotion = state.profile.reduceMotion
                )
                if (layers.isEmpty()) {
                    Text(
                        "Персонаж ещё одет в стартовый набор",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                    )
                }
            }

            ProgressCard(
                title = "Задачи",
                subtitle = "${summary.tasksDone} из ${summary.tasksPlanned}",
                progress = summary.tasksProgress,
                onClick = onOpenToday
            )

            ProgressCard(
                title = "Привычки",
                subtitle = "${summary.habitsDone} из ${summary.habitsPlanned}",
                progress = summary.habitsProgress,
                onClick = onOpenCharacter
            )
        }
    }
}

@Composable
private fun ProgressCard(
    title: String,
    subtitle: String,
    progress: Float,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
    }
}

private const val DEFAULT_TINT = 0xFF6E7BA6.toInt()
